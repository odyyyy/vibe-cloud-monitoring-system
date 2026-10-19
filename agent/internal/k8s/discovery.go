// Package k8s discovers pod log files through the Kubernetes API and registers
// them with the log pipeline. It only ever tails files under /var/log/containers
// and requires nothing more than get/list/watch access to pods.
package k8s

import (
	"context"
	"fmt"
	"log"
	"os"
	"strings"
	"sync"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/fields"
	"k8s.io/client-go/informers"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/cache"
	"k8s.io/client-go/tools/clientcmd"

	"github.com/vibemonitoring/agent/internal/config"
)

// SourceSink receives container log sources discovered from pod events. The log
// pipeline implements it; updates are applied asynchronously.
type SourceSink interface {
	AddSource(config.LogSource)
	RemoveSource(path string)
}

// Discoverer watches pods on the local node (or the configured scope) and keeps
// the pipeline's set of tailed container log files in sync.
type Discoverer struct {
	cfg      config.KubernetesConfig
	client   kubernetes.Interface
	sink     SourceSink
	logger   *log.Logger
	nodeName string

	mu sync.Mutex
	// tracked maps "namespace/name" to the log paths currently registered for
	// that pod, so a deleted pod or a restarted container removes exactly the
	// files it owned.
	tracked map[string]map[string]struct{}
}

// New builds a discoverer using in-cluster credentials, or the local kubeconfig
// outside a cluster.
func New(cfg config.KubernetesConfig, sink SourceSink, logger *log.Logger) (*Discoverer, error) {
	restCfg, err := restConfig()
	if err != nil {
		return nil, err
	}
	client, err := kubernetes.NewForConfig(restCfg)
	if err != nil {
		return nil, fmt.Errorf("kubernetes: build client: %w", err)
	}
	// DaemonSet deployments set NODE_NAME from spec.nodeName. Restricting the
	// watch to the local node avoids registering files that do not exist here.
	return newWithClient(cfg, client, sink, logger, strings.TrimSpace(os.Getenv("NODE_NAME"))), nil
}

func newWithClient(cfg config.KubernetesConfig, client kubernetes.Interface, sink SourceSink, logger *log.Logger, nodeName string) *Discoverer {
	return &Discoverer{
		cfg:      cfg,
		client:   client,
		sink:     sink,
		logger:   logger,
		nodeName: nodeName,
		tracked:  map[string]map[string]struct{}{},
	}
}

func restConfig() (*rest.Config, error) {
	if cfg, err := rest.InClusterConfig(); err == nil {
		return cfg, nil
	}

	loadingRules := clientcmd.NewDefaultClientConfigLoadingRules()
	if kubeconfig := strings.TrimSpace(os.Getenv("KUBECONFIG")); kubeconfig != "" {
		loadingRules.ExplicitPath = kubeconfig
	}
	cfg, err := clientcmd.NewNonInteractiveDeferredLoadingClientConfig(
		loadingRules, &clientcmd.ConfigOverrides{},
	).ClientConfig()
	if err != nil {
		return nil, fmt.Errorf("kubernetes: load in-cluster or kubeconfig credentials: %w", err)
	}
	return cfg, nil
}

// Run starts the pod informer and blocks until ctx is cancelled. A cancelled
// context is a normal shutdown and returns nil.
func (d *Discoverer) Run(ctx context.Context) error {
	factory := informers.NewSharedInformerFactoryWithOptions(d.client, 0, d.informerOptions()...)
	informer := factory.Core().V1().Pods().Informer()

	if _, err := informer.AddEventHandler(cache.ResourceEventHandlerFuncs{
		AddFunc: func(obj interface{}) {
			d.onUpsert(obj)
		},
		UpdateFunc: func(_, newObj interface{}) {
			d.onUpsert(newObj)
		},
		DeleteFunc: func(obj interface{}) {
			d.onDelete(obj)
		},
	}); err != nil {
		return fmt.Errorf("kubernetes: register pod handler: %w", err)
	}

	namespace := d.cfg.Namespace
	if namespace == "" {
		namespace = "all"
	}
	node := d.nodeName
	if node == "" {
		node = "all"
	}
	d.logger.Printf("kubernetes discovery enabled: namespace=%s node=%s label_selector=%q log_dir=%s",
		namespace, node, d.cfg.LabelSelector, containerLogDir)

	factory.Start(ctx.Done())
	defer factory.Shutdown()

	if !cache.WaitForCacheSync(ctx.Done(), informer.HasSynced) {
		if ctx.Err() != nil {
			return nil
		}
		return fmt.Errorf("kubernetes: pod informer cache did not sync")
	}

	<-ctx.Done()
	return nil
}

func (d *Discoverer) informerOptions() []informers.SharedInformerOption {
	var opts []informers.SharedInformerOption
	if ns := strings.TrimSpace(d.cfg.Namespace); ns != "" {
		opts = append(opts, informers.WithNamespace(ns))
	}
	labelSelector := strings.TrimSpace(d.cfg.LabelSelector)
	fieldSelector := ""
	if d.nodeName != "" {
		fieldSelector = fields.OneTermEqualSelector("spec.nodeName", d.nodeName).String()
	}
	if labelSelector != "" || fieldSelector != "" {
		opts = append(opts, informers.WithTweakListOptions(func(o *metav1.ListOptions) {
			if labelSelector != "" {
				o.LabelSelector = labelSelector
			}
			if fieldSelector != "" {
				o.FieldSelector = fieldSelector
			}
		}))
	}
	return opts
}

func (d *Discoverer) onUpsert(obj interface{}) {
	pod, ok := obj.(*corev1.Pod)
	if !ok {
		return
	}
	// The field selector normally handles this; keep the check so a client that
	// ignores selectors cannot make the agent tail other nodes' files.
	if d.nodeName != "" && pod.Spec.NodeName != d.nodeName {
		return
	}
	d.syncPod(pod, false)
}

func (d *Discoverer) onDelete(obj interface{}) {
	if tombstone, ok := obj.(cache.DeletedFinalStateUnknown); ok {
		obj = tombstone.Obj
	}
	pod, ok := obj.(*corev1.Pod)
	if !ok {
		return
	}
	d.syncPod(pod, true)
}

// syncPod reconciles the registered log sources for one pod with its current
// container statuses. On delete, every tracked file is removed.
func (d *Discoverer) syncPod(pod *corev1.Pod, deleting bool) {
	key := pod.Namespace + "/" + pod.Name
	desired := map[string]containerLog{}
	if !deleting {
		for _, cl := range podContainerLogs(pod) {
			desired[cl.path] = cl
		}
	}

	d.mu.Lock()
	previous := d.tracked[key]
	if deleting || len(desired) == 0 {
		delete(d.tracked, key)
	} else {
		next := make(map[string]struct{}, len(desired))
		for path := range desired {
			next[path] = struct{}{}
		}
		d.tracked[key] = next
	}
	d.mu.Unlock()

	// Remove first so a restarted container never tails two files at once.
	for path := range previous {
		if _, keep := desired[path]; !keep {
			d.sink.RemoveSource(path)
		}
	}
	for path, cl := range desired {
		if _, known := previous[path]; !known {
			d.sink.AddSource(d.sourceFor(pod, cl))
		}
	}
}

type containerLog struct {
	path      string
	container string
}

// podContainerLogs returns one log path per container that already has a
// container ID. A pod event can arrive before the runtime has assigned IDs; the
// next update event registers those containers.
func podContainerLogs(pod *corev1.Pod) []containerLog {
	statuses := make([]corev1.ContainerStatus, 0,
		len(pod.Status.InitContainerStatuses)+len(pod.Status.ContainerStatuses)+len(pod.Status.EphemeralContainerStatuses))
	statuses = append(statuses, pod.Status.InitContainerStatuses...)
	statuses = append(statuses, pod.Status.ContainerStatuses...)
	statuses = append(statuses, pod.Status.EphemeralContainerStatuses...)

	logs := make([]containerLog, 0, len(statuses))
	for _, status := range statuses {
		path, err := containerLogPath(pod.Namespace, pod.Name, status.Name, status.ContainerID)
		if err != nil {
			continue
		}
		logs = append(logs, containerLog{path: path, container: status.Name})
	}
	return logs
}

func (d *Discoverer) sourceFor(pod *corev1.Pod, cl containerLog) config.LogSource {
	return config.LogSource{
		Path:          cl.path,
		ServiceName:   cl.container,
		FromBeginning: true,
		Kubernetes: &config.KubernetesMetadata{
			Pod:       pod.Name,
			Namespace: pod.Namespace,
			Container: cl.container,
		},
	}
}
