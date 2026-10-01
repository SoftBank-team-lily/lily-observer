package com.lily.observer.logs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lily.observer.ObserverProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * CloudWatch Logs 에서 한 앱의 로그를 읽는다.
 *
 * <p>Fluent Bit 은 파드마다 스트림 {@code {namespace}.{app}.{pod}} 를 만든다 (deploy/k3s/fluent-bit.yaml).
 * 그래서 스트림 이름 접두사 {@code {namespace}.{app}.} 로 한 앱의 모든 파드 로그를 모은다.
 * 이벤트 본문은 Fluent Bit 레코드 JSON 이다: {@code {"log": "...", "kubernetes": {"pod_name", "labels", "container_image"}}}.
 *
 * <p>노드 IAM 역할에 {@code logs:FilterLogEvents} 권한이 있어야 한다.
 */
public class CloudWatchLogSource implements LogSource, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(CloudWatchLogSource.class);

    // 대소문자 구분. 어느 하나라도 들어 있으면 고른다
    private static final String ERROR_PATTERN = "?ERROR ?Exception ?Error ?panic ?Traceback";
    // 구간이 길어도 API 를 너무 많이 부르지 않게 한다
    private static final int MAX_PAGES = 10;

    private final CloudWatchLogsClient client;
    private final String logGroup;
    private final ObjectMapper json;

    public CloudWatchLogSource(ObserverProperties properties, ObjectMapper json) {
        this(CloudWatchLogsClient.builder().region(Region.of(properties.logs().region())).build(),
                properties.logs().logGroup(), json);
    }

    CloudWatchLogSource(CloudWatchLogsClient client, String logGroup, ObjectMapper json) {
        this.client = client;
        this.logGroup = logGroup;
        this.json = json;
    }

    @Override
    public List<LogEntry> recent(String namespace, String app, Instant since, boolean errorsOnly, int limit) {
        FilterLogEventsRequest.Builder request = FilterLogEventsRequest.builder()
                .logGroupName(logGroup)
                .logStreamNamePrefix(namespace + "." + app + ".")
                .startTime(since.toEpochMilli());
        if (errorsOnly) {
            request.filterPattern(ERROR_PATTERN);
        }

        // CloudWatch 는 오래된 순으로 준다. 최근 limit 개만 남긴다
        Deque<LogEntry> latest = new ArrayDeque<>(limit);
        String nextToken = null;
        try {
            for (int page = 0; page < MAX_PAGES; page++) {
                FilterLogEventsResponse response = client.filterLogEvents(request.nextToken(nextToken).build());
                for (FilteredLogEvent event : response.events()) {
                    if (latest.size() == limit) {
                        latest.removeFirst();
                    }
                    latest.addLast(toEntry(event));
                }
                nextToken = response.nextToken();
                if (nextToken == null) {
                    break;
                }
            }
        } catch (SdkException e) {
            log.warn("cloudwatch logs query failed. app={} message={}", app, e.getMessage());
            throw new LogsUnavailableException("cloudwatch logs 조회 실패", e);
        }
        return new ArrayList<>(latest);
    }

    LogEntry toEntry(FilteredLogEvent event) {
        Instant at = Instant.ofEpochMilli(event.timestamp());
        JsonNode record;
        try {
            record = json.readTree(event.message());
        } catch (Exception e) {
            return new LogEntry(at, null, null, null, event.message());
        }
        if (record == null || !record.isObject()) {
            return new LogEntry(at, null, null, null, event.message());
        }
        JsonNode kubernetes = record.path("kubernetes");
        JsonNode labels = kubernetes.path("labels");
        String slot = text(labels, "track");
        if (slot == null) {
            slot = text(labels, "color");
        }
        String message = text(record, "log");
        return new LogEntry(at,
                text(kubernetes, "pod_name"),
                slot,
                shortImage(text(kubernetes, "container_image")),
                message != null ? message : event.message());
    }

    /** 703592323320.dkr.ecr.ap-northeast-2.amazonaws.com/lily-test:20261001-053104 → lily-test:20261001-053104 */
    static String shortImage(String image) {
        if (image == null) {
            return null;
        }
        int slash = image.lastIndexOf('/');
        return slash < 0 ? image : image.substring(slash + 1);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    @Override
    public void destroy() {
        client.close();
    }
}
