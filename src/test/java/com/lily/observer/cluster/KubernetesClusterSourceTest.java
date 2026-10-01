package com.lily.observer.cluster;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetricsBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesClusterSourceTest {

    private static final String IMAGE = "703592323320.dkr.ecr.ap-northeast-2.amazonaws.com/lily-test:20261001-053104";

    private static Pod pod(boolean ready, int restarts, String waiting, String lastTerminated) {
        PodBuilder builder = new PodBuilder()
                .withNewMetadata().withName("lily-test-green-1").withLabels(Map.of("app", "lily-test", "color", "green"))
                .endMetadata()
                .withNewSpec().withNodeName("ip-172-31-15-165")
                .addNewContainer().withName("lily-test").withImage(IMAGE).endContainer()
                .endSpec();
        var status = builder.withNewStatus().withPhase("Running").withStartTime("2026-10-01T05:34:00Z")
                .addNewContainerStatus().withName("lily-test").withReady(ready).withRestartCount(restarts);
        if (waiting != null) {
            status = status.withNewState().withNewWaiting().withReason(waiting).endWaiting().endState();
        }
        if (lastTerminated != null) {
            status = status.withNewLastState().withNewTerminated().withReason(lastTerminated).endTerminated()
                    .endLastState();
        }
        return status.endContainerStatus().endStatus().build();
    }

    @Test
    void podStatusReadsReadinessRestartsSlotImageAndUsage() {
        PodMetrics usage = new PodMetricsBuilder().withNewMetadata().withName("lily-test-green-1").endMetadata()
                .addNewContainer().withName("lily-test")
                .withUsage(Map.of("cpu", new Quantity("12m"), "memory", new Quantity("240Mi"))).endContainer()
                .build();

        PodStatus status = KubernetesClusterSource.status(pod(true, 0, null, null), usage);

        assertThat(status.ready()).isTrue();
        assertThat(status.slot()).isEqualTo("green");
        assertThat(status.image()).isEqualTo("lily-test:20261001-053104");
        assertThat(status.node()).isEqualTo("ip-172-31-15-165");
        assertThat(status.startedAt()).isEqualTo(Instant.parse("2026-10-01T05:34:00Z"));
        assertThat(status.cpuMillicores()).isEqualTo(12.0);
        assertThat(status.memoryMiB()).isEqualTo(240.0);
        assertThat(status.problem()).isNull();
    }

    @Test
    void crashLoopAndOomKilledAreReported() {
        PodStatus status = KubernetesClusterSource.status(pod(false, 4, "CrashLoopBackOff", "OOMKilled"), null);

        assertThat(status.ready()).isFalse();
        assertThat(status.restarts()).isEqualTo(4);
        assertThat(status.problem()).isEqualTo("CrashLoopBackOff");
        assertThat(status.lastRestartReason()).isEqualTo("OOMKilled");
        assertThat(status.cpuMillicores()).isNull();
    }

    @Test
    void containerCreatingIsNotAProblem() {
        assertThat(KubernetesClusterSource.status(pod(false, 0, "ContainerCreating", null), null).problem()).isNull();
    }

    @Test
    void nodeStatusComputesUsagePercent() {
        Node node = new NodeBuilder()
                .withNewMetadata().withName("ip-172-31-10-248")
                .withLabels(Map.of("node-role.kubernetes.io/control-plane", "true",
                        "node.kubernetes.io/instance-type", "t3.medium")).endMetadata()
                .withNewStatus()
                .addNewCondition().withType("Ready").withStatus("True").endCondition()
                .withAllocatable(Map.of("cpu", new Quantity("2"), "memory", new Quantity("4000Mi")))
                .endStatus().build();
        NodeMetrics usage = new NodeMetricsBuilder().withNewMetadata().withName("ip-172-31-10-248").endMetadata()
                .withUsage(Map.of("cpu", new Quantity("500m"), "memory", new Quantity("1000Mi"))).build();

        NodeStatus status = KubernetesClusterSource.status(node, usage, 14);

        assertThat(status.role()).isEqualTo("server");
        assertThat(status.instanceType()).isEqualTo("t3.medium");
        assertThat(status.ready()).isTrue();
        assertThat(status.cpuCores()).isEqualTo(2.0);
        assertThat(status.cpuUsedCores()).isEqualTo(0.5);
        assertThat(status.cpuPercent()).isEqualTo(25.0);
        assertThat(status.memoryPercent()).isEqualTo(25.0);
        assertThat(status.pods()).isEqualTo(14);
    }

    private static Deployment deployment(String name, String slotLabel, String slot, int replicas, int ready) {
        return new DeploymentBuilder()
                .withNewMetadata().withName(name).withLabels(Map.of("app", "lily-test")).endMetadata()
                .withNewSpec().withReplicas(replicas)
                .withNewTemplate().withNewMetadata().withLabels(Map.of("app", "lily-test", slotLabel, slot)).endMetadata()
                .withNewSpec().addNewContainer().withName("lily-test").withImage(IMAGE.replace("053104", slot))
                .endContainer().endSpec().endTemplate().endSpec()
                .withNewStatus().withReadyReplicas(ready == 0 ? null : ready).endStatus()
                .build();
    }

    @Test
    void blueGreenActiveSlotComesFromTheServiceSelector() {
        Service service = new ServiceBuilder().withNewSpec()
                .withSelector(Map.of("app", "lily-test", "color", "green")).endSpec().build();

        AppSummary summary = KubernetesClusterSource.summary("lily-test", "default",
                List.of(deployment("lily-test-green", "color", "green", 1, 1),
                        deployment("lily-test-blue", "color", "blue", 0, 0)),
                service, "lily-test.apps.lilycloud.kr");

        assertThat(summary.strategy()).isEqualTo("blue-green");
        assertThat(summary.activeSlot()).isEqualTo("green");
        assertThat(summary.image()).isEqualTo("lily-test:20261001-green");
        assertThat(summary.url()).isEqualTo("https://lily-test.apps.lilycloud.kr");
        assertThat(summary.readyReplicas()).isEqualTo(1);
        assertThat(summary.deployments()).extracting(AppSummary.SlotDeployment::slot).containsExactly("blue", "green");
    }

    @Test
    void canaryTrafficGoesToStable() {
        AppSummary summary = KubernetesClusterSource.summary("lily-test", "default",
                List.of(deployment("lily-test-stable", "track", "stable", 4, 4),
                        deployment("lily-test-canary", "track", "canary", 1, 1)),
                null, null);

        assertThat(summary.strategy()).isEqualTo("canary");
        assertThat(summary.activeSlot()).isEqualTo("stable");
        assertThat(summary.replicas()).isEqualTo(5);
        assertThat(summary.url()).isNull();
    }
}
