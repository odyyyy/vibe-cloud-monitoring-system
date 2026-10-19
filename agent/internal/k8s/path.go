package k8s

import (
	"fmt"
	"path"
	"strings"
)

// containerLogDir is the only host directory pod log discovery is allowed to
// read. The kubelet writes a symlink per container into this directory.
const containerLogDir = "/var/log/containers"

// containerLogPath builds the kubelet's standard log path for a container:
//
//	/var/log/containers/<pod>_<namespace>_<container>-<containerID>.log
//
// Every component is validated so a hostile pod name or container ID cannot
// escape containerLogDir.
func containerLogPath(namespace, pod, container, containerID string) (string, error) {
	for _, part := range []string{namespace, pod, container} {
		if !safePathComponent(part) {
			return "", fmt.Errorf("unsafe Kubernetes name %q", part)
		}
	}
	id := normalizeContainerID(containerID)
	if id == "" {
		return "", fmt.Errorf("container %s/%s/%s has no usable container id", namespace, pod, container)
	}

	name := fmt.Sprintf("%s_%s_%s-%s.log", pod, namespace, container, id)
	logPath := path.Join(containerLogDir, name)
	if !isUnderContainerLogDir(logPath) {
		return "", fmt.Errorf("refusing log path outside %s: %s", containerLogDir, logPath)
	}
	return logPath, nil
}

// normalizeContainerID turns a CRI container ID such as
// "containerd://abc123" into the plain ID used in log file names.
func normalizeContainerID(id string) string {
	id = strings.TrimSpace(id)
	if idx := strings.Index(id, "://"); idx >= 0 {
		id = id[idx+3:]
	}
	if !safePathComponent(id) {
		return ""
	}
	return id
}

func safePathComponent(s string) bool {
	return s != "" && s != "." && s != ".." && !strings.ContainsAny(s, "/\\\x00")
}

func isUnderContainerLogDir(logPath string) bool {
	clean := path.Clean(logPath)
	return strings.HasPrefix(clean, containerLogDir+"/")
}
