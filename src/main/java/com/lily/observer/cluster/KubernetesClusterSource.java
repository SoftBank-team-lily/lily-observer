package com.lily.observer.cluster;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 쿠버네티스 API 와 metrics-server(k3s 기본 설치)로 앱 · 파드 · 서버 상태를 만든다.
 *
 * <p>lily-cicd 규칙: 파드 라벨 {@code app} + 슬롯 라벨({@code track}: stable/canary, {@code color}: blue/green),
 * Service {@code {app}-svc}, Ingress {@code {app}-ingress}.
 * 권한은 deploy/k3s/lily-observer.yaml 의 ClusterRole (조회 전용).
 */
@Component
public class KubernetesClusterSource implements ClusterSource {

    private static final Logger log = LoggerFactory.getLogger(KubernetesClusterSource.class);
    private static final String CONTROL_PLANE = "node-role.kubernetes.io/control-plane";

    private final KubernetesClient k8s;

    public KubernetesClusterSource(KubernetesClient k8s) {
        this.k8s = k8s;
    }

    @Override
    public List<AppSummary> apps(String namespace) {
        List<Deployment> deployments = call(() -> k8s.apps().deployments().inNamespace(namespace)
                .withLabel("app").list().getItems());
        Map<String, List<String>> hosts = new HashMap<>();
        for (Ingress ingress : call(() -> k8s.network().v1().ingresses().inNamespace(namespace).list().getItems())) {
            if (ingress.getSpec() != null && ingress.getSpec().getRules() != null) {
                hosts.put(ingress.getMetadata().getName(), ingress.getSpec().getRules().stream()
                        .map(rule -> rule.getHost()).filter(Objects::nonNull).toList());
            }
        }

        Map<String, List<Deployment>> byApp = new LinkedHashMap<>();
        for (Deployment deployment : deployments) {
            Map<String, String> labels = templateLabels(deployment);
            if (slot(labels) != null) {      // 슬롯 라벨이 있어야 lily-cicd 가 배포한 앱
                byApp.computeIfAbsent(labels.get("app"), key -> new ArrayList<>()).add(deployment);
            }
        }

        List<AppSummary> apps = new ArrayList<>();
        byApp.forEach((app, slots) -> {
            Service service = call(() -> k8s.services().inNamespace(namespace).withName(app + "-svc").get());
            apps.add(summary(app, namespace, slots, service, url(hosts.get(app + "-ingress"))));
        });
        apps.sort(Comparator.comparing(AppSummary::app));
        return apps;
    }

    /**
     * 예전 앱은 nip.io(http)와 apps.lilycloud.kr(https) 두 주소를 가진다. TLS 가 있는 도메인 주소를 먼저 쓴다.
     */
    static String url(List<String> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            return null;
        }
        return hosts.stream().filter(host -> !host.endsWith(".nip.io")).findFirst()
                .map(host -> "https://" + host)
                .orElse("http://" + hosts.get(0));
    }

    static AppSummary summary(String app, String namespace, List<Deployment> slots, Service service, String url) {
        boolean canary = slots.stream().anyMatch(d -> templateLabels(d).containsKey("track"));
        String active;
        if (canary) {
            active = "stable";
        } else {
            Map<String, String> selector = service == null || service.getSpec() == null
                    ? null : service.getSpec().getSelector();
            active = selector == null ? null : selector.get("color");
        }

        List<AppSummary.SlotDeployment> rows = new ArrayList<>();
        int ready = 0;
        int desired = 0;
        String activeImage = null;
        for (Deployment deployment : slots) {
            String slot = slot(templateLabels(deployment));
            int readyReplicas = deployment.getStatus() == null || deployment.getStatus().getReadyReplicas() == null
                    ? 0 : deployment.getStatus().getReadyReplicas();
            int replicas = deployment.getSpec().getReplicas() == null ? 0 : deployment.getSpec().getReplicas();
            String image = shortImage(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
            rows.add(new AppSummary.SlotDeployment(deployment.getMetadata().getName(), slot, readyReplicas, replicas,
                    image));
            ready += readyReplicas;
            desired += replicas;
            if (Objects.equals(slot, active)) {
                activeImage = image;
            }
        }
        rows.sort(Comparator.comparing(AppSummary.SlotDeployment::name));
        return new AppSummary(app, namespace, url,
                canary ? "canary" : "blue-green", active, ready, desired, activeImage, rows);
    }

    @Override
    public List<PodStatus> pods(String namespace, String app) {
        List<Pod> pods = call(() -> k8s.pods().inNamespace(namespace).withLabel("app", app).list().getItems());
        Map<String, PodMetrics> usage = new HashMap<>();
        for (PodMetrics metrics : optionalMetrics(() -> k8s.top().pods().metrics(namespace).getItems())) {
            usage.put(metrics.getMetadata().getName(), metrics);
        }
        List<PodStatus> statuses = new ArrayList<>();
        for (Pod pod : pods) {
            statuses.add(status(pod, usage.get(pod.getMetadata().getName())));
        }
        statuses.sort(Comparator.comparing(PodStatus::name));
        return statuses;
    }

    static PodStatus status(Pod pod, PodMetrics metrics) {
        List<ContainerStatus> containers = pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null
                ? List.of() : pod.getStatus().getContainerStatuses();
        boolean ready = !containers.isEmpty() && containers.stream().allMatch(c -> Boolean.TRUE.equals(c.getReady()));
        int restarts = containers.stream().mapToInt(c -> c.getRestartCount() == null ? 0 : c.getRestartCount()).sum();
        String problem = null;
        String lastRestart = null;
        for (ContainerStatus container : containers) {
            if (problem == null && container.getState() != null && container.getState().getWaiting() != null) {
                problem = container.getState().getWaiting().getReason();
            }
            if (lastRestart == null && container.getLastState() != null
                    && container.getLastState().getTerminated() != null) {
                lastRestart = container.getLastState().getTerminated().getReason();
            }
        }
        if ("ContainerCreating".equals(problem) || "PodInitializing".equals(problem)) {
            problem = null;      // 정상적인 시작 과정
        }

        Double cpu = null;
        Double memory = null;
        if (metrics != null && metrics.getContainers() != null) {
            double cores = 0;
            double bytes = 0;
            for (ContainerMetrics container : metrics.getContainers()) {
                cores += amount(container.getUsage().get("cpu"));
                bytes += amount(container.getUsage().get("memory"));
            }
            cpu = round(cores * 1000);
            memory = round(bytes / 1024 / 1024);
        }

        String startedAt = pod.getStatus() == null ? null : pod.getStatus().getStartTime();
        return new PodStatus(pod.getMetadata().getName(),
                pod.getStatus() == null ? null : pod.getStatus().getPhase(),
                ready, restarts, slot(pod.getMetadata().getLabels()),
                pod.getSpec().getContainers().isEmpty() ? null : shortImage(pod.getSpec().getContainers().get(0).getImage()),
                pod.getSpec().getNodeName(),
                startedAt == null ? null : Instant.parse(startedAt),
                problem, lastRestart, cpu, memory);
    }

    @Override
    public List<NodeStatus> nodes() {
        List<Node> nodes = call(() -> k8s.nodes().list().getItems());
        Map<String, NodeMetrics> usage = new HashMap<>();
        for (NodeMetrics metrics : optionalMetrics(() -> k8s.top().nodes().metrics().getItems())) {
            usage.put(metrics.getMetadata().getName(), metrics);
        }
        Map<String, Integer> podCounts = new HashMap<>();
        for (Pod pod : call(() -> k8s.pods().inAnyNamespace().list().getItems())) {
            String phase = pod.getStatus() == null ? null : pod.getStatus().getPhase();
            if (pod.getSpec().getNodeName() != null && !"Succeeded".equals(phase) && !"Failed".equals(phase)) {
                podCounts.merge(pod.getSpec().getNodeName(), 1, Integer::sum);
            }
        }
        List<NodeStatus> statuses = new ArrayList<>();
        for (Node node : nodes) {
            String name = node.getMetadata().getName();
            statuses.add(status(node, usage.get(name), podCounts.getOrDefault(name, 0)));
        }
        statuses.sort(Comparator.comparing(NodeStatus::role).thenComparing(NodeStatus::name));
        return statuses;
    }

    static NodeStatus status(Node node, NodeMetrics metrics, int pods) {
        Map<String, String> labels = node.getMetadata().getLabels() == null ? Map.of() : node.getMetadata().getLabels();
        boolean ready = node.getStatus().getConditions().stream()
                .filter(c -> "Ready".equals(c.getType()))
                .map(NodeCondition::getStatus)
                .anyMatch("True"::equals);
        Map<String, Quantity> allocatable = node.getStatus().getAllocatable();
        double cpuCores = amount(allocatable.get("cpu"));
        double memoryMiB = amount(allocatable.get("memory")) / 1024 / 1024;

        Double cpuUsed = null;
        Double memoryUsed = null;
        if (metrics != null && metrics.getUsage() != null) {
            cpuUsed = amount(metrics.getUsage().get("cpu"));
            memoryUsed = amount(metrics.getUsage().get("memory")) / 1024 / 1024;
        }
        return new NodeStatus(node.getMetadata().getName(),
                labels.containsKey(CONTROL_PLANE) ? "server" : "worker",
                ready, cpuCores, cpuUsed == null ? null : round3(cpuUsed), percent(cpuUsed, cpuCores),
                round(memoryMiB), memoryUsed == null ? null : round(memoryUsed), percent(memoryUsed, memoryMiB), pods);
    }

    private static Map<String, String> templateLabels(Deployment deployment) {
        Map<String, String> labels = deployment.getSpec().getTemplate().getMetadata().getLabels();
        return labels == null ? Map.of() : labels;
    }

    /** track 이 있으면 track (카나리), 없으면 color (블루그린) */
    static String slot(Map<String, String> labels) {
        if (labels == null) {
            return null;
        }
        return labels.containsKey("track") ? labels.get("track") : labels.get("color");
    }

    /** 703592323320.dkr.ecr.ap-northeast-2.amazonaws.com/lily-test:20261001-053104 → lily-test:20261001-053104 */
    static String shortImage(String image) {
        if (image == null) {
            return null;
        }
        int slash = image.lastIndexOf('/');
        return slash < 0 ? image : image.substring(slash + 1);
    }

    private static double amount(Quantity quantity) {
        return quantity == null ? 0 : Quantity.getAmountInBytes(quantity).doubleValue();
    }

    private static Double percent(Double used, double total) {
        return used == null || total <= 0 ? null : round(used / total * 100);
    }

    private static double round3(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (KubernetesClientException e) {
            log.warn("kubernetes request failed. code={} message={}", e.getCode(), e.getMessage());
            throw new ClusterUnavailableException("쿠버네티스 조회 실패", e);
        }
    }

    /** metrics-server 가 없거나 아직 값이 없으면 사용량만 빠진다 */
    private static <T> List<T> optionalMetrics(Supplier<List<T>> request) {
        try {
            List<T> items = request.get();
            return items == null ? List.of() : items;
        } catch (KubernetesClientException e) {
            log.info("metrics-server unavailable. message={}", e.getMessage());
            return List.of();
        }
    }
}
