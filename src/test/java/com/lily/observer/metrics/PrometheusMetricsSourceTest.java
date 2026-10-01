package com.lily.observer.metrics;

import com.lily.observer.ObserverProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PrometheusMetricsSourceTest {

    private HttpServer server;
    private final List<String> queries = new ArrayList<>();
    private Function<String, String> responder;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            queries.add(exchange.getRequestURI().getPath() + "?" + query);
            byte[] body = responder.apply(query).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private PrometheusMetricsSource source() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        ObserverProperties properties = new ObserverProperties(null,
                new ObserverProperties.Prometheus(url, "%s-ingress"), null, null, null, null, null);
        return new PrometheusMetricsSource(properties, RestClient.builder());
    }

    @Test
    void trafficQueriesIngressOfTheAppAndComputesRates() {
        responder = query -> {
            if (query.contains("status=~\"5..\"")) {
                return vector("10");
            }
            if (query.contains("request_duration_seconds")) {
                return vector("0.02");
            }
            return vector("200");
        };

        TrafficMetrics traffic = source().traffic("default", "lily-test",
                Instant.parse("2026-10-01T06:00:00Z"), Duration.ofMinutes(5));

        assertThat(traffic.requestsPerMinute()).isCloseTo(40, within(0.001));   // 200 / 5분
        assertThat(traffic.errorRate()).isCloseTo(0.05, within(0.001));         // 10 / 200
        assertThat(traffic.avgLatencyMs()).isCloseTo(20, within(0.001));
        assertThat(queries).allMatch(q -> q.contains("namespace=\"default\",ingress=\"lily-test-ingress\""));
        assertThat(queries.get(0)).contains("time=1790834400");
    }

    @Test
    void trafficIsEmptyWhenThereAreNoRequests() {
        responder = query -> "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}";

        TrafficMetrics traffic = source().traffic("default", "lily-test", Instant.now(), Duration.ofMinutes(1));

        assertThat(traffic).isEqualTo(TrafficMetrics.empty());
        assertThat(queries).hasSize(1);
    }

    @Test
    void seriesMergesTheThreeQueriesByTimestamp() {
        responder = query -> {
            if (query.contains("status=~\"5..\"")) {
                return matrix("[1790834400,\"6\"]");                      // 두 번째 시각에는 에러 없음
            }
            if (query.contains("request_duration_seconds")) {
                return matrix("[1790834400,\"0.015\"],[1790834430,\"0.03\"]");
            }
            return matrix("[1790834400,\"60\"],[1790834430,\"30\"]");
        };

        List<TrafficPoint> series = source().series("default", "lily-test",
                Instant.parse("2026-10-01T06:00:00Z"), Instant.parse("2026-10-01T06:00:30Z"), Duration.ofSeconds(30));

        assertThat(series).hasSize(2);
        assertThat(series.get(0).at()).isEqualTo(Instant.parse("2026-10-01T06:00:00Z"));
        assertThat(series.get(0).errorRate()).isCloseTo(0.1, within(0.001));
        assertThat(series.get(0).avgLatencyMs()).isCloseTo(15, within(0.001));
        assertThat(series.get(1).requestsPerMinute()).isCloseTo(30, within(0.001));
        assertThat(series.get(1).errorRate()).isZero();
        assertThat(queries).allMatch(q -> q.startsWith("/api/v1/query_range?") && q.contains("step=30"));
    }

    @Test
    void prometheusDownBecomesMetricsUnavailable() {
        server.stop(0);

        assertThatThrownBy(() -> source().traffic("default", "lily-test", Instant.now(), Duration.ofMinutes(1)))
                .isInstanceOf(MetricsUnavailableException.class);
    }

    private static String vector(String value) {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":"
                + "[{\"metric\":{},\"value\":[1790834400,\"" + value + "\"]}]}}";
    }

    private static String matrix(String values) {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":"
                + "[{\"metric\":{},\"values\":[" + values + "]}]}}";
    }
}
