package k8s

import (
	"path"
	"strings"
	"testing"
)

func TestContainerLogPath(t *testing.T) {
	logPath, err := containerLogPath("payments", "api-7d9f", "api", "containerd://abc123")
	if err != nil {
		t.Fatalf("containerLogPath: %v", err)
	}
	want := path.Join(containerLogDir, "api-7d9f_payments_api-abc123.log")
	if logPath != want {
		t.Fatalf("path = %q, want %q", logPath, want)
	}
	if !strings.HasPrefix(logPath, containerLogDir+"/") {
		t.Fatalf("path %q escapes %s", logPath, containerLogDir)
	}
}

func TestContainerLogPathSupportsDockerScheme(t *testing.T) {
	logPath, err := containerLogPath("default", "web", "nginx", "docker://deadbeef")
	if err != nil {
		t.Fatalf("containerLogPath: %v", err)
	}
	if !strings.HasSuffix(logPath, "web_default_nginx-deadbeef.log") {
		t.Fatalf("unexpected path: %q", logPath)
	}
}

func TestContainerLogPathRejectsUnsafeComponents(t *testing.T) {
	cases := []struct {
		name      string
		namespace string
		pod       string
		container string
	}{
		{"namespace traversal", "..", "pod", "app"},
		{"pod separator", "ns", "po/d", "app"},
		{"pod dotdot", "ns", "..", "app"},
		{"container backslash", "ns", "pod", `app\..\..\etc`},
		{"empty container", "ns", "pod", ""},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := containerLogPath(tc.namespace, tc.pod, tc.container, "abc"); err == nil {
				t.Fatal("expected unsafe name to be rejected")
			}
		})
	}
}

func TestContainerLogPathRejectsUnsafeID(t *testing.T) {
	for _, id := range []string{"", "containerd://", "containerd://../../etc/passwd", "docker://a/b"} {
		t.Run(id, func(t *testing.T) {
			if _, err := containerLogPath("ns", "pod", "app", id); err == nil {
				t.Fatal("expected unsafe container id to be rejected")
			}
		})
	}
}

func TestNormalizeContainerID(t *testing.T) {
	cases := map[string]string{
		"containerd://abc123": "abc123",
		"docker://deadbeef":   "deadbeef",
		"cri-o://cafe":        "cafe",
		"plain":               "plain",
		"  spaced  ":          "spaced",
	}
	for in, want := range cases {
		if got := normalizeContainerID(in); got != want {
			t.Errorf("normalizeContainerID(%q) = %q, want %q", in, got, want)
		}
	}
}
