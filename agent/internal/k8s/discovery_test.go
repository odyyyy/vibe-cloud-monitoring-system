package k8s

import (
	"context"
	"io"
	"log"
	"path"
	"sort"
	"sync"
	"testing"
	"time"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes/fake"

	"github.com/vibemonitoring/agent/internal/config"
)

func testLogger() *log.Logger {
	return log.New(io.Discard, "", 0)
}

type fakeSink struct {
	mu      sync.Mutex
	adds    []config.LogSource
	removes []string
	addCh   chan config.LogSource
}

func newFakeSink() *fakeSink {
	return &fakeSink{addCh: make(chan config.LogSource, 32)}
}

func (s *fakeSink) AddSource(src config.LogSource) {
	s.mu.Lock()
	s.adds = append(s.adds, src)
	s.mu.Unlock()
	select {
	case s.addCh <- src:
	default:
	}
}

func (s *fakeSink) RemoveSource(path string) {
	s.mu.Lock()
	s.removes = append(s.removes, path)
	s.mu.Unlock()
}

func (s *fakeSink) added() []config.LogSource {
	s.mu.Lock()
	defer s.mu.Unlock()
	return append([]config.LogSource(nil), s.adds...)
}

func (s *fakeSink) removed() []string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return append([]string(nil), s.removes...)
}

func podWithStatuses(namespace, name, node string, statuses ...corev1.ContainerStatus) *corev1.Pod {
	return &corev1.Pod{
		ObjectMeta: metav1.ObjectMeta{Name: name, Namespace: namespace},
		Spec:       corev1.PodSpec{NodeName: node},
		Status:     corev1.PodStatus{ContainerStatuses: statuses},
	}
}

func status(name, containerID string) corev1.ContainerStatus {
	return corev1.ContainerStatus{Name: name, ContainerID: containerID}
}

func TestSyncPodRegistersContainersWithMetadata(t *testing.T) {
	sink := newFakeSink()
	d := newWithClient(config.KubernetesConfig{}, fake.NewClientset(), sink, testLogger(), "")
	pod := podWithStatuses("payments", "api-1", "node-a",
		status("api", "containerd://aaa"),
		status("sidecar", "docker://bbb"),
	)

	d.syncPod(pod, false)

	adds := sink.added()
	if len(adds) != 2 {
		t.Fatalf("expected 2 sources, got %d", len(adds))
	}
	sort.Slice(adds, func(i, j int) bool { return adds[i].Path < adds[j].Path })

	wantPaths := []string{
		path.Join(containerLogDir, "api-1_payments_api-aaa.log"),
		path.Join(containerLogDir, "api-1_payments_sidecar-bbb.log"),
	}
	for i, src := range adds {
		if src.Path != wantPaths[i] {
			t.Errorf("source %d path = %q, want %q", i, src.Path, wantPaths[i])
		}
		if src.Kubernetes == nil {
			t.Fatalf("source %d has no Kubernetes metadata", i)
		}
		if src.Kubernetes.Pod != "api-1" || src.Kubernetes.Namespace != "payments" {
			t.Errorf("source %d metadata = %+v", i, src.Kubernetes)
		}
	}
}

func TestSyncPodRemovesDeletedPod(t *testing.T) {
	sink := newFakeSink()
	d := newWithClient(config.KubernetesConfig{}, fake.NewClientset(), sink, testLogger(), "")
	pod := podWithStatuses("payments", "api-1", "node-a", status("api", "containerd://aaa"))

	d.syncPod(pod, false)
	d.syncPod(pod, true)

	removes := sink.removed()
	if len(removes) != 1 {
		t.Fatalf("expected 1 removed source, got %d", len(removes))
	}
	if removes[0] != path.Join(containerLogDir, "api-1_payments_api-aaa.log") {
		t.Fatalf("unexpected removed path: %q", removes[0])
	}
	if len(d.tracked) != 0 {
		t.Fatalf("tracked pods should be empty, got %v", d.tracked)
	}
}

func TestSyncPodReplacesRestartedContainer(t *testing.T) {
	sink := newFakeSink()
	d := newWithClient(config.KubernetesConfig{}, fake.NewClientset(), sink, testLogger(), "")
	oldPod := podWithStatuses("payments", "api-1", "node-a", status("api", "containerd://old"))
	newPod := podWithStatuses("payments", "api-1", "node-a", status("api", "containerd://new"))

	d.syncPod(oldPod, false)
	d.syncPod(newPod, false)

	if adds := sink.added(); len(adds) != 2 {
		t.Fatalf("expected an add for each container instance, got %d", len(adds))
	}
	removes := sink.removed()
	if len(removes) != 1 {
		t.Fatalf("expected the old container to be removed, got %v", removes)
	}
	if removes[0] != path.Join(containerLogDir, "api-1_payments_api-old.log") {
		t.Fatalf("unexpected removed path: %q", removes[0])
	}
}

func TestSyncPodIgnoresContainersWithoutID(t *testing.T) {
	sink := newFakeSink()
	d := newWithClient(config.KubernetesConfig{}, fake.NewClientset(), sink, testLogger(), "")
	pod := podWithStatuses("payments", "api-1", "node-a",
		status("pending", ""),
		status("ready", "containerd://ready"),
	)

	d.syncPod(pod, false)

	if adds := sink.added(); len(adds) != 1 {
		t.Fatalf("expected 1 source, got %d", len(adds))
	}
}

func TestRunDiscoversExistingPod(t *testing.T) {
	pod := podWithStatuses("payments", "api-1", "node-a", status("api", "containerd://aaa"))
	client := fake.NewClientset(pod)
	sink := newFakeSink()
	d := newWithClient(config.KubernetesConfig{}, client, sink, testLogger(), "")

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	done := make(chan error, 1)
	go func() { done <- d.Run(ctx) }()

	select {
	case src := <-sink.addCh:
		if src.Kubernetes == nil || src.Kubernetes.Pod != "api-1" {
			t.Fatalf("unexpected source: %+v", src)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("timed out waiting for pod discovery")
	}

	cancel()
	select {
	case err := <-done:
		if err != nil {
			t.Fatalf("Run: %v", err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("Run did not stop on context cancellation")
	}
}

func TestRunFiltersPodsByNode(t *testing.T) {
	matching := podWithStatuses("payments", "api-1", "node-a", status("api", "containerd://aaa"))
	other := podWithStatuses("payments", "api-2", "node-b", status("api", "containerd://bbb"))
	client := fake.NewClientset(matching, other)
	sink := newFakeSink()
	d := newWithClient(config.KubernetesConfig{}, client, sink, testLogger(), "node-a")

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	done := make(chan error, 1)
	go func() { done <- d.Run(ctx) }()

	deadline := time.After(5 * time.Second)
	for len(sink.added()) < 1 {
		select {
		case <-sink.addCh:
		case <-deadline:
			t.Fatal("timed out waiting for pod discovery")
		case <-time.After(20 * time.Millisecond):
		}
	}
	// Give the informer a moment to deliver a potential event for node-b too.
	time.Sleep(200 * time.Millisecond)
	cancel()

	adds := sink.added()
	if len(adds) != 1 {
		t.Fatalf("expected 1 source for node-a, got %d: %+v", len(adds), adds)
	}
	if adds[0].Kubernetes == nil || adds[0].Kubernetes.Pod != "api-1" {
		t.Fatalf("unexpected source: %+v", adds[0])
	}
	select {
	case err := <-done:
		if err != nil {
			t.Fatalf("Run: %v", err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("Run did not stop on context cancellation")
	}
}
