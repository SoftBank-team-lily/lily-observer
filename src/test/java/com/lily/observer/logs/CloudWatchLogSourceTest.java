package com.lily.observer.logs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CloudWatchLogsException;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudWatchLogSourceTest {

    // Fluent Bit 이 /lily/apps 에 보낸 실제 레코드 모양 (2026-10-01 lily-test)
    private static final String RECORD = """
            {"time":"2026-10-01T06:22:49.953928178Z","stream":"stdout","_p":"F",
             "log":"java.lang.IllegalStateException: chaos: forced application error",
             "kubernetes":{"pod_name":"lily-test-green-7bfdd46749-7hm67","namespace_name":"default",
               "labels":{"app":"lily-test","color":"green","pod-template-hash":"7bfdd46749"},
               "container_image":"703592323320.dkr.ecr.ap-northeast-2.amazonaws.com/lily-test:20261001-053104"}}
            """;

    private final List<FilterLogEventsRequest> requests = new ArrayList<>();

    private CloudWatchLogSource source(List<FilterLogEventsResponse> pages) {
        CloudWatchLogsClient client = new CloudWatchLogsClient() {
            @Override
            public FilterLogEventsResponse filterLogEvents(FilterLogEventsRequest request) {
                requests.add(request);
                return pages.get(requests.size() - 1);
            }

            @Override
            public String serviceName() {
                return "logs";
            }

            @Override
            public void close() {
            }
        };
        return new CloudWatchLogSource(client, "/lily/apps", new ObjectMapper());
    }

    @Test
    void readsPodSlotAndImageFromFluentBitRecord() {
        CloudWatchLogSource source = source(List.of(page(null, event(1000, RECORD))));

        List<LogEntry> logs = source.recent("default", "lily-test", Instant.EPOCH, false, 10);

        assertThat(logs).containsExactly(new LogEntry(Instant.ofEpochMilli(1000),
                "lily-test-green-7bfdd46749-7hm67", "green", "lily-test:20261001-053104",
                "java.lang.IllegalStateException: chaos: forced application error"));
        assertThat(requests.get(0).logGroupName()).isEqualTo("/lily/apps");
        assertThat(requests.get(0).logStreamNamePrefix()).isEqualTo("default.lily-test.");
        assertThat(requests.get(0).filterPattern()).isNull();
    }

    @Test
    void canaryTrackWinsOverColorAndPlainTextIsKept() {
        String canary = RECORD.replace("\"color\":\"green\"", "\"track\":\"canary\",\"color\":\"green\"");
        CloudWatchLogSource source = source(List.of(page(null, event(1, canary), event(2, "not json"))));

        List<LogEntry> logs = source.recent("default", "lily-test", Instant.EPOCH, false, 10);

        assertThat(logs.get(0).slot()).isEqualTo("canary");
        assertThat(logs.get(1).message()).isEqualTo("not json");
        assertThat(logs.get(1).pod()).isNull();
    }

    @Test
    void keepsOnlyTheLatestEventsAcrossPages() {
        CloudWatchLogSource source = source(List.of(
                page("next", event(1, RECORD), event(2, RECORD)),
                page(null, event(3, RECORD), event(4, RECORD))));

        List<LogEntry> logs = source.recent("default", "lily-test", Instant.EPOCH, true, 3);

        assertThat(logs).extracting(LogEntry::at)
                .containsExactly(Instant.ofEpochMilli(2), Instant.ofEpochMilli(3), Instant.ofEpochMilli(4));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).nextToken()).isEqualTo("next");
        assertThat(requests.get(0).filterPattern()).contains("ERROR").contains("Exception");
    }

    @Test
    void awsErrorBecomesLogsUnavailable() {
        CloudWatchLogsClient failing = new CloudWatchLogsClient() {
            @Override
            public FilterLogEventsResponse filterLogEvents(FilterLogEventsRequest request) {
                throw CloudWatchLogsException.builder().message("AccessDenied").build();
            }

            @Override
            public String serviceName() {
                return "logs";
            }

            @Override
            public void close() {
            }
        };
        CloudWatchLogSource source = new CloudWatchLogSource(failing, "/lily/apps", new ObjectMapper());

        assertThatThrownBy(() -> source.recent("default", "lily-test", Instant.EPOCH, false, 10))
                .isInstanceOf(LogsUnavailableException.class);
    }

    @Test
    void shortImageDropsRegistry() {
        assertThat(CloudWatchLogSource.shortImage("1.dkr.ecr.ap-northeast-2.amazonaws.com/lily-test:v1"))
                .isEqualTo("lily-test:v1");
        assertThat(CloudWatchLogSource.shortImage("nginx:1.27")).isEqualTo("nginx:1.27");
        assertThat(CloudWatchLogSource.shortImage(null)).isNull();
    }

    private static FilteredLogEvent event(long millis, String message) {
        return FilteredLogEvent.builder().timestamp(millis).message(message).build();
    }

    private static FilterLogEventsResponse page(String nextToken, FilteredLogEvent... events) {
        return FilterLogEventsResponse.builder().events(events).nextToken(nextToken).build();
    }
}
