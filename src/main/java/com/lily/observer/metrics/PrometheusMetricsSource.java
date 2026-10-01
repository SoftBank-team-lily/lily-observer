package com.lily.observer.metrics;

import com.lily.observer.ObserverProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Prometheus HTTP API 로 앱별 요청 지표를 계산한다.
 *
 * <p>모든 앱 요청은 ingress-nginx 를 지나가고, Prometheus 가 그 입구의 숫자를 모은다
 * (deploy/k3s/prometheus.yaml). 그래서 앱이 Spring 이든 Node 든 같은 방식으로 본다.
 * lily-cicd 는 앱마다 Ingress 를 {@code {app}-ingress} 로 만든다 (observer.prometheus.ingress-name).
 */
@Component
public class PrometheusMetricsSource implements MetricsSource {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMetricsSource.class);

    private static final String REQUESTS = "nginx_ingress_controller_requests";
    private static final String DURATION = "nginx_ingress_controller_request_duration_seconds";
    private static final String FILTER = "namespace=\"%s\",ingress=\"%s\"";
    // 추이 그래프의 각 점은 그 시각 기준 최근 1분 값
    private static final String SERIES_RANGE = "[1m]";

    private final RestClient http;
    private final String ingressName;

    public PrometheusMetricsSource(ObserverProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder
                .requestFactory(requestFactory)
                .baseUrl(properties.prometheus().url())
                .build();
        this.ingressName = properties.prometheus().ingressName();
    }

    @Override
    public TrafficMetrics traffic(String namespace, String app, Instant at, Duration window) {
        String filter = filter(namespace, app);
        String range = "[" + window.toSeconds() + "s]";
        double perMinute = 60.0 / window.toSeconds();

        double total = query("sum(increase(" + REQUESTS + "{" + filter + "}" + range + "))", at) * perMinute;
        if (total <= 0) {
            return TrafficMetrics.empty();
        }
        double errors = query("sum(increase(" + REQUESTS + "{" + filter + ",status=~\"5..\"}" + range + "))", at)
                * perMinute;
        double latencySeconds = query(average(filter, range), at);
        double p95Seconds = query(p95(filter, range), at);
        return new TrafficMetrics(total, errors / total, latencySeconds * 1000, p95Seconds * 1000);
    }

    @Override
    public List<TrafficPoint> series(String namespace, String app, Instant from, Instant to, Duration step) {
        String filter = filter(namespace, app);
        Map<Long, Double> totals = queryRange(
                "sum(rate(" + REQUESTS + "{" + filter + "}" + SERIES_RANGE + ")) * 60", from, to, step);
        Map<Long, Double> errors = queryRange(
                "sum(rate(" + REQUESTS + "{" + filter + ",status=~\"5..\"}" + SERIES_RANGE + ")) * 60",
                from, to, step);
        Map<Long, Double> latencies = queryRange(average(filter, SERIES_RANGE), from, to, step);
        Map<Long, Double> p95s = queryRange(p95(filter, SERIES_RANGE), from, to, step);

        List<TrafficPoint> points = new ArrayList<>();
        totals.forEach((epochSecond, total) -> {
            double errorRate = total > 0 ? errors.getOrDefault(epochSecond, 0.0) / total : 0;
            double latencyMs = latencies.getOrDefault(epochSecond, 0.0) * 1000;
            double p95Ms = p95s.getOrDefault(epochSecond, 0.0) * 1000;
            points.add(new TrafficPoint(Instant.ofEpochSecond(epochSecond), total, errorRate, latencyMs, p95Ms));
        });
        return points;
    }

    private static String average(String filter, String range) {
        return "sum(rate(" + DURATION + "_sum{" + filter + "}" + range + "))"
                + " / sum(rate(" + DURATION + "_count{" + filter + "}" + range + "))";
    }

    /** ingress-nginx 응답 시간 히스토그램(구간별 개수)에서 95 퍼센타일을 추정한다 */
    private static String p95(String filter, String range) {
        return "histogram_quantile(0.95, sum by (le) (rate(" + DURATION + "_bucket{" + filter + "}" + range + ")))";
    }

    private String filter(String namespace, String app) {
        return String.format(FILTER, namespace, String.format(ingressName, app));
    }

    /** 결과가 없으면 0. Prometheus 가 죽어 있으면 예외를 던져 이번 판정을 건너뛰게 한다 */
    double query(String promql, Instant at) {
        QueryResponse response = get(promql, uri -> uri.path("/api/v1/query")
                .queryParam("query", "{q}")
                .queryParam("time", at.getEpochSecond())
                .build(promql));
        if (response.data() == null || response.data().result() == null || response.data().result().isEmpty()) {
            return 0;
        }
        List<Object> value = response.data().result().get(0).value();
        return value == null || value.size() < 2 ? 0 : number(value.get(1));
    }

    /** 시각(epoch 초) → 값. 값이 없는 시각은 빠진다 */
    Map<Long, Double> queryRange(String promql, Instant from, Instant to, Duration step) {
        QueryResponse response = get(promql, uri -> uri.path("/api/v1/query_range")
                .queryParam("query", "{q}")
                .queryParam("start", from.getEpochSecond())
                .queryParam("end", to.getEpochSecond())
                .queryParam("step", step.toSeconds())
                .build(promql));
        Map<Long, Double> points = new TreeMap<>();
        if (response.data() == null || response.data().result() == null || response.data().result().isEmpty()) {
            return points;
        }
        List<List<Object>> values = response.data().result().get(0).values();
        if (values == null) {
            return points;
        }
        for (List<Object> pair : values) {
            if (pair.size() >= 2) {
                points.put(((Number) pair.get(0)).longValue(), number(pair.get(1)));
            }
        }
        return points;
    }

    private QueryResponse get(String promql, Function<UriBuilder, URI> uri) {
        try {
            QueryResponse response = http.get().uri(uri).retrieve().body(QueryResponse.class);
            return response == null ? new QueryResponse(null, null) : response;
        } catch (RestClientException e) {
            log.warn("prometheus query failed. query={} message={}", promql, e.getMessage());
            throw new MetricsUnavailableException("prometheus 조회 실패", e);
        }
    }

    private static double number(Object raw) {
        double parsed = Double.parseDouble(String.valueOf(raw));
        return Double.isNaN(parsed) || Double.isInfinite(parsed) ? 0 : parsed;
    }

    record QueryResponse(String status, Data data) {}

    record Data(String resultType, List<Result> result) {}

    /** 즉시 조회는 value, 구간 조회는 values */
    record Result(Map<String, String> metric, List<Object> value, List<List<Object>> values) {}
}
