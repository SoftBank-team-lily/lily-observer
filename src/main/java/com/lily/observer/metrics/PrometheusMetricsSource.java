package com.lily.observer.metrics;

import com.lily.observer.ObserverProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Prometheus HTTP API 로 슬롯별 지표를 계산한다.
 *
 * <p>lily-cicd 는 파드에 {@code app} 과 슬롯 라벨({@code track}: stable/canary, {@code color}: blue/green)을 붙인다.
 * canary 는 Service 하나 뒤에서 파드 수 비율로 트래픽을 나누기 때문에 Nginx 지표로는 버전을 구분할 수 없다.
 * 그래서 Prometheus 가 파드마다 {@code /actuator/prometheus} 를 긁고, 두 슬롯 라벨을 {@code slot} 하나로 합친다
 * (deploy/k3s/prometheus.yaml 의 relabel 규칙).
 */
@Component
public class PrometheusMetricsSource implements MetricsSource {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMetricsSource.class);

    // probe 요청은 에러율 계산에서 뺀다
    private static final String FILTER = "namespace=\"%s\",app=\"%s\",slot=\"%s\",uri!~\"/actuator.*\"";

    private final RestClient http;
    private final String metric;

    public PrometheusMetricsSource(ObserverProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.prometheus().url())
                .build();
        this.metric = properties.prometheus().requestMetric();
    }

    @Override
    public SlotMetrics slot(String namespace, String app, String slot) {
        String filter = String.format(FILTER, namespace, app, slot);
        double total = query("sum(rate(" + metric + "_count{" + filter + "}[1m])) * 60");
        if (total <= 0) {
            return SlotMetrics.empty(slot);
        }
        double errors = query("sum(rate(" + metric + "_count{" + filter + ",status=~\"5..\"}[1m])) * 60");
        double latencySeconds = query("sum(rate(" + metric + "_sum{" + filter + "}[1m]))"
                + " / sum(rate(" + metric + "_count{" + filter + "}[1m]))");
        return new SlotMetrics(slot, total, errors / total, latencySeconds * 1000);
    }

    /** 결과가 없으면 0. Prometheus 가 죽어 있으면 예외를 던져 이번 판정을 건너뛰게 한다 */
    double query(String promql) {
        QueryResponse response;
        try {
            response = http.get()
                    .uri(uri -> uri.path("/api/v1/query").queryParam("query", "{q}").build(promql))
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
