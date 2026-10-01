package com.lily.observer.metrics;

import com.lily.observer.ObserverProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    private final RestClient http;
    private final String ingressName;

    public PrometheusMetricsSource(ObserverProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.prometheus().url())
                .build();
        this.ingressName = properties.prometheus().ingressName();
    }

    @Override
    public TrafficMetrics traffic(String namespace, String app, Instant at, Duration window) {
        String filter = String.format(FILTER, namespace, String.format(ingressName, app));
        String range = "[" + window.toSeconds() + "s]";
        double perMinute = 60.0 / window.toSeconds();

        double total = query("sum(increase(" + REQUESTS + "{" + filter + "}" + range + "))", at) * perMinute;
        if (total <= 0) {
            return TrafficMetrics.empty();
        }
        double errors = query("sum(increase(" + REQUESTS + "{" + filter + ",status=~\"5..\"}" + range + "))", at)
                * perMinute;
        double latencySeconds = query("sum(rate(" + DURATION + "_sum{" + filter + "}" + range + "))"
                + " / sum(rate(" + DURATION + "_count{" + filter + "}" + range + "))", at);
        return new TrafficMetrics(total, errors / total, latencySeconds * 1000);
    }

    /** 결과가 없으면 0. Prometheus 가 죽어 있으면 예외를 던져 이번 판정을 건너뛰게 한다 */
    double query(String promql, Instant at) {
        QueryResponse response;
        try {
            response = http.get()
                    .uri(uri -> uri.path("/api/v1/query")
                            .queryParam("query", "{q}")
                            .queryParam("time", at.getEpochSecond())
                            .build(promql))
                    .retrieve()
                    .body(QueryResponse.class);
        } catch (RestClientException e) {
            log.warn("prometheus query failed. query={} message={}", promql, e.getMessage());
            throw new MetricsUnavailableException("prometheus 조회 실패", e);
        }
        if (response == null || response.data() == null || response.data().result() == null
                || response.data().result().isEmpty()) {
            return 0;
        }
        List<Object> value = response.data().result().get(0).value();
        if (value == null || value.size() < 2) {
            return 0;
        }
        double parsed = Double.parseDouble(String.valueOf(value.get(1)));
        return Double.isNaN(parsed) || Double.isInfinite(parsed) ? 0 : parsed;
    }

    record QueryResponse(String status, Data data) {}

    record Data(String resultType, List<Result> result) {}

    record Result(Map<String, String> metric, List<Object> value) {}
}
