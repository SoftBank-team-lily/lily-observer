package com.lily.observer.remediate;

import com.lily.observer.ObserverProperties;
import com.lily.observer.cluster.AppSummary;
import com.lily.observer.cluster.ClusterSource;
import com.lily.observer.cluster.NodeStatus;
import com.lily.observer.cluster.PodStatus;
import com.lily.observer.judge.RuleBasedRiskJudge;
import com.lily.observer.metrics.MetricsSource;
import com.lily.observer.metrics.TrafficMetrics;
import com.lily.observer.metrics.TrafficPoint;
import com.lily.observer.status.AppStatusService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RemediateWatchTest {

    private final List<String> sent = new ArrayList<>();
    private TrafficMetrics current = critical();

    @Test
    void secondCriticalSendsOnceAndDoesNotRollBack() {
        RemediateWatch watch = watch(true);

        watch.tick();
        watch.tick();
        watch.tick();

        assertThat(sent).containsExactly("default/blog");
    }

    @Test
    void disabledWatchCountsButDoesNotSend() {
        RemediateWatch watch = watch(false);

        watch.tick();
        watch.tick();

        assertThat(sent).isEmpty();
    }

    @Test
    void warningBreaksTheStreak() {
        RemediateWatch watch = watch(true);

        watch.tick();
        current = warning();
        watch.tick();
        current = critical();
        watch.tick();

        assertThat(sent).isEmpty();
    }

    private RemediateWatch watch(boolean enabled) {
        MetricsSource metrics = new MetricsSource() {
            @Override
            public TrafficMetrics traffic(String namespace, String app, Instant at, Duration window) {
                return window.equals(Duration.ofMinutes(1)) ? current : normal();
            }

            @Override
            public List<TrafficPoint> series(String namespace, String app, Instant from, Instant to, Duration step) {
                return List.of();
            }
        };
        AppStatusService status = new AppStatusService(metrics, new RuleBasedRiskJudge(new ObserverProperties(
                null, null, null, new ObserverProperties.Judge(20, 0.01, 0.02, 0.05, 2.0, 2), null, null, null)));
        ClusterSource cluster = new ClusterSource() {
            @Override
            public List<AppSummary> apps(String namespace) {
                return List.of(new AppSummary("blog", "default", null, "blue-green", "green", 1, 1, "blog:1", List.of()));
            }

            @Override
            public List<PodStatus> pods(String namespace, String app) {
                return List.of();
            }

            @Override
            public List<NodeStatus> nodes() {
                return List.of();
            }
        };
        RemediateSender sender = new RemediateSender(new RemediateProperties(enabled, "", ""), (namespace, app, since, errorsOnly, limit) -> {
            sent.add(namespace + "/" + app);
            return List.of();
        });
        return new RemediateWatch(2, Duration.ofSeconds(30), new RemediateProperties(enabled, "", ""), cluster, status, sender);
    }

    private static TrafficMetrics critical() {
        return new TrafficMetrics(60, 0.06, 10, 20);
    }

    private static TrafficMetrics warning() {
        return new TrafficMetrics(60, 0.03, 10, 20);
    }

    private static TrafficMetrics normal() {
        return new TrafficMetrics(60, 0, 10, 20);
    }
}
