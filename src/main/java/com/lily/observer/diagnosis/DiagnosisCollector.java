package com.lily.observer.diagnosis;

import com.lily.observer.ObserverProperties;
import com.lily.observer.cluster.ClusterSource;
import com.lily.observer.cluster.PodStatus;
import com.lily.observer.logs.LogEntry;
import com.lily.observer.logs.LogSource;
import com.lily.observer.metrics.TrafficMetrics;
import com.lily.observer.status.AppStatusService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

@Component
public class DiagnosisCollector {
    private final AppStatusService status;
    private final ClusterSource cluster;
    private final LogSource logs;
    private final ObserverProperties.Judge rules;

    public DiagnosisCollector(AppStatusService status, ClusterSource cluster, LogSource logs,
                              ObserverProperties properties) {
        this.status = status;
        this.cluster = cluster;
        this.logs = logs;
        this.rules = properties.judge();
    }

    public Diagnosis.Request collect(String namespace, String app) {
        Instant now = Instant.now();
        List<Diagnosis.Evidence> evidence = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        checkInterrupted();
        try {
            var found = cluster.apps(namespace).stream()
                    .filter(a -> app.equals(a.app()) && namespace.equals(a.namespace())).findFirst();
            if (found.isEmpty()) missing.add("deployment");
            else {
                var a = found.get();
                add(evidence, "deployment", "deployment", "deployment", "Image=" + a.image()
                        + "; strategy=" + a.strategy() + "; activeSlot=" + a.activeSlot()
                        + "; readyReplicas=" + a.readyReplicas() + "/" + a.replicas()
                        + " (all slots; not a deployment change history)");
            }
        } catch (RuntimeException ignored) { missing.add("deployment"); }
        checkInterrupted();
        try {
            var s = status.status(namespace, app);
            var m = s.current();
            if (!valid(m)) throw new IllegalStateException("No valid metrics");
            add(evidence, "status", "status", "observation", "Observed rule level=" + s.level()
                    + "; at=" + s.judgedAt() + "; this is a symptom, not a root cause");
            String signal = m.requestsPerMinute() < rules.minRequests() ? "low_traffic"
                    : m.errorRate() >= rules.noticeErrorRate() ? "high_error_rate" : "observation";
            add(evidence, "metrics", "metrics", signal, String.format(Locale.ROOT,
                    "Last 1m: requests/min=%.2f; 5xx=%.2f%%; average=%.2fms; p95=%.2fms. Minimum requests/min for ratios=%d",
                    m.requestsPerMinute(), m.errorRate() * 100, m.avgLatencyMs(), m.p95LatencyMs(), rules.minRequests()));
            var b = s.baseline();
            if (valid(b) && m.requestsPerMinute() >= rules.minRequests()
                    && b.requestsPerMinute() >= rules.minRequests() && b.p95LatencyMs() > 0
                    && m.p95LatencyMs() >= b.p95LatencyMs() * rules.latencyRatio()) {
                add(evidence, "latency", "metrics", "high_latency", String.format(Locale.ROOT,
                        "p95 %.2fms vs baseline %.2fms (15m–5m ago), threshold ratio %.2f. No capacity limit evidence.",
                        m.p95LatencyMs(), b.p95LatencyMs(), rules.latencyRatio()));
            }
        } catch (RuntimeException ignored) { missing.addAll(List.of("status", "metrics")); }
        checkInterrupted();
        try {
            var pods = cluster.pods(namespace, app);
            if (pods.isEmpty()) missing.add("pods");
            pods.stream().sorted(Comparator.comparing((PodStatus p) -> p.ready() && p.restarts() == 0))
                    .limit(8).forEach(p -> add(evidence, "pod-" + (evidence.size() + 1), "pods", podSignal(p),
                            "Pod=" + p.name() + "; phase=" + p.phase() + "; ready=" + p.ready()
                                    + "; cumulative restarts=" + p.restarts() + "; lastRestartReason=" + p.lastRestartReason()
                                    + "; problem=" + p.problem() + "; CPU millicores=" + p.cpuMillicores()
                                    + "; memory MiB=" + p.memoryMiB() + "; limits not collected"));
        } catch (RuntimeException ignored) { missing.add("pods"); }
        checkInterrupted();
        try {
            var entries = logs.recent(namespace, app, now.minus(Duration.ofMinutes(15)), true, 100);
            entries.stream().sorted(Comparator.comparing(LogEntry::at, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(10).forEach(l -> add(evidence, "log-" + (evidence.size() + 1), "logs", logSignal(l.message()),
                            "At=" + l.at() + "; pod=" + l.pod() + "; untrusted log excerpt: " + l.message()));
        } catch (RuntimeException ignored) { missing.add("logs"); }
        return new Diagnosis.Request(app, namespace, now, List.copyOf(evidence), List.copyOf(missing));
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Diagnosis collection interrupted");
    }

    private static void add(List<Diagnosis.Evidence> evidence, String id, String source, String signal, String text) {
        evidence.add(new Diagnosis.Evidence(id, source, signal, DiagnosisMask.clean(text, 2000)));
    }

    private static boolean valid(TrafficMetrics m) {
        return m != null && Double.isFinite(m.requestsPerMinute()) && m.requestsPerMinute() >= 0
                && Double.isFinite(m.errorRate()) && m.errorRate() >= 0 && m.errorRate() <= 1
                && Double.isFinite(m.avgLatencyMs()) && m.avgLatencyMs() >= 0
                && Double.isFinite(m.p95LatencyMs()) && m.p95LatencyMs() >= 0;
    }

    private static String podSignal(PodStatus p) {
        if ("OOMKilled".equals(p.lastRestartReason()) || "OOMKilled".equals(p.problem())) return "oom";
        if ("CreateContainerConfigError".equals(p.problem()) || "InvalidImageName".equals(p.problem())) return "config_error";
        if (!p.ready()) return "pod_not_ready";
        return p.restarts() > 0 ? "restarts" : "observation";
    }

    private static String logSignal(String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (m.contains("outofmemoryerror") || m.contains("oomkilled")) return "oom";
        if (m.contains("sqlexception") || m.contains("psqlexception") || m.contains("sqlstate")) return "database_error";
        if (m.contains("could not resolve placeholder") || m.contains("configurationpropertiesbindexception")) return "config_error";
        if (m.contains("nullpointerexception") || m.contains("indexoutofboundsexception")) return "code_error";
        return "observation";
    }
}
