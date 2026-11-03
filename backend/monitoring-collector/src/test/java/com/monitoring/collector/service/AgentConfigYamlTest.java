package com.monitoring.collector.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConfigYamlTest {

    @Test
    void deepMergesMappingsAndReplacesLists() {
        AgentConfigYaml.Resolved resolved = AgentConfigYaml.resolve(
                """
                interval_seconds: 15
                logs:
                  - path: /var/log/app.log
                    service_name: app
                metrics:
                  network:
                    interfaces: [eth0]
                """,
                """
                interval_seconds: 30
                metrics:
                  network:
                    interfaces: [eth1, eth2]
                """);

        assertTrue(resolved.configYaml().contains("interval_seconds: 30"));
        assertTrue(resolved.configYaml().contains("path: /var/log/app.log"));
        assertTrue(resolved.configYaml().contains("eth1"));
        assertFalse(resolved.configYaml().contains("eth0"));
        assertEquals(64, resolved.configVersion().length());
    }

    @Test
    void producesStableVersionRegardlessOfMappingOrder() {
        AgentConfigYaml.Resolved first = AgentConfigYaml.resolve(
                "interval_seconds: 15\nlog_batch_size: 500\n", "{}");
        AgentConfigYaml.Resolved second = AgentConfigYaml.resolve(
                "log_batch_size: 500\ninterval_seconds: 15\n", "{}");

        assertEquals(first.configVersion(), second.configVersion());
        assertEquals(first.configYaml(), second.configYaml());
    }

    @Test
    void servesInvalidYamlSoTheAgentCanReportTheError() {
        AgentConfigYaml.Resolved resolved = AgentConfigYaml.resolve(
                "interval_seconds: 15\n",
                "logs: [\n  broken");

        assertTrue(resolved.configYaml().contains("broken"));
        assertNotEquals(
                AgentConfigYaml.resolve("interval_seconds: 15\n", "{}").configVersion(),
                resolved.configVersion());
    }

    @Test
    void emptyTemplatesProduceAnEmptyMapping() {
        AgentConfigYaml.Resolved resolved = AgentConfigYaml.resolve(null, null);

        assertEquals("{}\n", resolved.configYaml());
        assertEquals(64, resolved.configVersion().length());
    }
}
