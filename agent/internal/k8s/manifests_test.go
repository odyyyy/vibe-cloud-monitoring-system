package k8s

import (
	"errors"
	"io"
	"os"
	"path/filepath"
	"testing"

	"gopkg.in/yaml.v3"
)

func loadManifests(t *testing.T, name string) []map[string]interface{} {
	t.Helper()
	f, err := os.Open(filepath.Join("..", "..", "k8s", name))
	if err != nil {
		t.Fatalf("open %s: %v", name, err)
	}
	defer f.Close()

	decoder := yaml.NewDecoder(f)
	var docs []map[string]interface{}
	for {
		var doc map[string]interface{}
		err := decoder.Decode(&doc)
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			t.Fatalf("decode %s: %v", name, err)
		}
		if len(doc) > 0 {
			docs = append(docs, doc)
		}
	}
	if len(docs) == 0 {
		t.Fatalf("%s contains no YAML documents", name)
	}
	return docs
}

func findDoc(docs []map[string]interface{}, kind string) map[string]interface{} {
	for _, doc := range docs {
		if doc["kind"] == kind {
			return doc
		}
	}
	return nil
}

func stringList(t *testing.T, value interface{}) []string {
	t.Helper()
	raw, ok := value.([]interface{})
	if !ok {
		t.Fatalf("expected a list, got %T", value)
	}
	out := make([]string, 0, len(raw))
	for _, item := range raw {
		s, ok := item.(string)
		if !ok {
			t.Fatalf("expected a string, got %T", item)
		}
		out = append(out, s)
	}
	return out
}

func contains(list []string, want string) bool {
	for _, item := range list {
		if item == want {
			return true
		}
	}
	return false
}

func TestRBACManifestAllowsOnlyPodReads(t *testing.T) {
	docs := loadManifests(t, "rbac.yaml")
	for _, kind := range []string{"ServiceAccount", "ClusterRole", "ClusterRoleBinding"} {
		if findDoc(docs, kind) == nil {
			t.Errorf("rbac.yaml is missing a %s", kind)
		}
	}

	role := findDoc(docs, "ClusterRole")
	rules, ok := role["rules"].([]interface{})
	if !ok || len(rules) != 1 {
		t.Fatalf("ClusterRole must have exactly one rule, got %v", role["rules"])
	}
	rule, ok := rules[0].(map[string]interface{})
	if !ok {
		t.Fatalf("unexpected rule type %T", rules[0])
	}
	resources := stringList(t, rule["resources"])
	verbs := stringList(t, rule["verbs"])
	if len(resources) != 1 || resources[0] != "pods" {
		t.Errorf("ClusterRole resources = %v, want only pods", resources)
	}
	for _, verb := range []string{"get", "list", "watch"} {
		if !contains(verbs, verb) {
			t.Errorf("ClusterRole is missing verb %q", verb)
		}
	}
	if contains(verbs, "*") || contains(verbs, "create") || contains(verbs, "delete") {
		t.Errorf("ClusterRole grants more than read access: %v", verbs)
	}
}

func TestDaemonSetManifestUsesSecretForAPIKey(t *testing.T) {
	docs := loadManifests(t, "daemonset.yaml")
	daemonSet := findDoc(docs, "DaemonSet")
	if daemonSet == nil {
		t.Fatal("daemonset.yaml is missing a DaemonSet")
	}

	spec := daemonSet["spec"].(map[string]interface{})
	template := spec["template"].(map[string]interface{})
	podSpec := template["spec"].(map[string]interface{})
	if podSpec["serviceAccountName"] != "vibemonitoring-agent" {
		t.Errorf("serviceAccountName = %v, want vibemonitoring-agent", podSpec["serviceAccountName"])
	}

	containers := podSpec["containers"].([]interface{})
	container := containers[0].(map[string]interface{})

	env := container["env"].([]interface{})
	foundAPIKey := false
	for _, item := range env {
		entry := item.(map[string]interface{})
		if entry["name"] != "AGENT_API_KEY" {
			continue
		}
		foundAPIKey = true
		if _, inline := entry["value"]; inline {
			t.Error("AGENT_API_KEY must not be set inline")
		}
		valueFrom, ok := entry["valueFrom"].(map[string]interface{})
		if !ok {
			t.Fatal("AGENT_API_KEY must use valueFrom")
		}
		secretRef, ok := valueFrom["secretKeyRef"].(map[string]interface{})
		if !ok || secretRef["name"] == "" || secretRef["key"] == "" {
			t.Error("AGENT_API_KEY must reference a Secret name and key")
		}
	}
	if !foundAPIKey {
		t.Error("DaemonSet does not set AGENT_API_KEY")
	}

	mounts := container["volumeMounts"].([]interface{})
	mounted := map[string]bool{}
	for _, item := range mounts {
		mount := item.(map[string]interface{})
		if path, ok := mount["mountPath"].(string); ok {
			readOnly, _ := mount["readOnly"].(bool)
			mounted[path] = readOnly
		}
	}
	for _, path := range []string{containerLogDir, "/var/log/pods"} {
		if readOnly, ok := mounted[path]; !ok || !readOnly {
			t.Errorf("%s must be mounted read-only", path)
		}
	}
}
