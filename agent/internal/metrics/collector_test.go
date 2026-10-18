package metrics

import "testing"

func TestTagsCarryHostAndVersion(t *testing.T) {
	c := &Collector{hostname: "server-1"}

	tags := c.tags()

	if tags["host"] != "server-1" {
		t.Errorf("host tag = %q, want %q", tags["host"], "server-1")
	}
	if tags["version"] != Version {
		t.Errorf("version tag = %q, want %q", tags["version"], Version)
	}
}
