package com.lily.observer.diagnosis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.lily.observer.diagnosis.DiagnosisFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiagnosisServiceTest {
    private final DiagnosisCollector collector = mock(DiagnosisCollector.class);
    private final DiagnosisClient client = mock(DiagnosisClient.class);
    private final MutableClock clock = new MutableClock();
    private DiagnosisService service;

    @BeforeEach
    void setup() {
        service = new DiagnosisService(enabled(), "observer-token", collector, client, clock);
        when(collector.collect(anyString(), anyString())).thenAnswer(i -> request(i.getArgument(0), i.getArgument(1)));
        when(client.analyze(any())).thenAnswer(i -> analysis(i.getArgument(0)));
    }

    @AfterEach
    void stopWorkers() { service.close(); }

    @Test
    void disabledOrMissingTokensPreventAllCollectionAndModelCalls() {
        var disabled = new DiagnosisService(new DiagnosisProperties(false, "http://builder.test", "token"),
                "observer-token", collector, client, clock);
        var noObserverToken = new DiagnosisService(enabled(), " ", collector, client, clock);
        var noBuilderToken = new DiagnosisService(new DiagnosisProperties(true, "http://builder.test", ""),
                "observer-token", collector, client, clock);
        assertThat(disabled.diagnose("default", "blog").state()).isEqualTo("disabled");
        assertThat(noObserverToken.diagnose("default", "blog").state()).isEqualTo("disabled");
        assertThat(noBuilderToken.diagnose("default", "blog").state()).isEqualTo("disabled");
        verifyNoInteractions(collector, client);
    }

    @Test
    void cachesSuccessfulResultsForExactlyThirtySeconds() {
        var first = service.diagnose("default", "blog");
        clock.advance(Duration.ofSeconds(29));
        assertThat(service.diagnose("default", "blog")).isSameAs(first);
        verify(collector).collect("default", "blog");
        clock.advance(Duration.ofSeconds(1));
        assertThat(service.diagnose("default", "blog").state()).isEqualTo("ready");
        verify(collector, times(2)).collect("default", "blog");
    }

    @Test
    void cacheIsIsolatedByNamespaceAndApp() {
        service.diagnose("default", "blog");
        var otherNamespace = service.diagnose("tenant", "blog");
        var otherApp = service.diagnose("default", "shop");
        assertThat(otherNamespace.analysis().namespace()).isEqualTo("tenant");
        assertThat(otherApp.analysis().app()).isEqualTo("shop");
        verify(client, times(3)).analyze(any());
    }

    @Test
    void modelFailurePreservesEvidenceAndRetriesAfterFiveSeconds() {
        doThrow(new IllegalStateException("token=private")).when(client).analyze(any());
        var first = service.diagnose("default", "blog");
        assertThat(first.state()).isEqualTo("unavailable");
        assertThat(first.evidence()).isEqualTo(request("default", "blog").evidence());
        assertThat(first.toString()).doesNotContain("private");
        clock.advance(Duration.ofSeconds(4));
        assertThat(service.diagnose("default", "blog")).isSameAs(first);
        clock.advance(Duration.ofSeconds(1));
        service.diagnose("default", "blog");
        verify(client, times(2)).analyze(any());
    }

    @Test
    void noEvidenceDoesNotCallTheModel() {
        when(collector.collect("default", "blog")).thenReturn(new Diagnosis.Request("blog", "default", NOW,
                List.of(), List.of("status", "metrics", "pods", "logs", "deployment")));
        assertThat(service.diagnose("default", "blog").state()).isEqualTo("unavailable");
        verifyNoInteractions(client);
    }

    @Test
    void collectionFailureDoesNotLeakDetailsAndReleasesPendingSlot() {
        when(collector.collect("default", "blog")).thenThrow(new IllegalStateException("private cluster URL"))
                .thenReturn(request("default", "blog"));
        assertThat(service.diagnose("default", "blog").toString()).doesNotContain("private cluster");
        clock.advance(Duration.ofSeconds(5));
        assertThat(service.diagnose("default", "blog").state()).isEqualTo("ready");
    }

    @Test
    void coalescesConcurrentRequestsForTheSameScope() throws Exception {
        CountDownLatch collecting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(collector.collect("default", "blog")).thenAnswer(i -> {
            collecting.countDown();
            if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            return request("default", "blog");
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.diagnose("default", "blog"));
            assertThat(collecting.await(2, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.diagnose("default", "blog"));
            release.countDown();
            assertThat(second.get(2, TimeUnit.SECONDS)).isSameAs(first.get(2, TimeUnit.SECONDS));
            verify(collector).collect("default", "blog");
            verify(client).analyze(any());
        } finally { release.countDown(); }
    }

    @Test
    void capsConcurrentDistinctScopesAtFourAndAcceptsWorkAfterCompletion() throws Exception {
        CountDownLatch collecting = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        when(collector.collect(anyString(), anyString())).thenAnswer(i -> {
            collecting.countDown();
            if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            return request(i.getArgument(0), i.getArgument(1));
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(i -> executor.submit(() -> service.diagnose("default", "blog-" + i))).toList();
            assertThat(collecting.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(service.diagnose("default", "fifth").state()).isEqualTo("busy");
            verify(collector, never()).collect("default", "fifth");
            release.countDown();
            for (var task : tasks) assertThat(task.get(2, TimeUnit.SECONDS).state()).isEqualTo("ready");
            assertThat(service.diagnose("default", "fifth").state()).isEqualTo("ready");
        } finally { release.countDown(); }
    }

    @Test
    void timeoutDoesNotQueueMoreWorkOrCallModelAfterUninterruptibleCollectionReturns() throws Exception {
        service.close();
        service = new DiagnosisService(enabled(), "observer-token", collector, client, clock, Duration.ofMillis(150));
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(4);
        AtomicInteger active = new AtomicInteger();
        when(collector.collect(anyString(), anyString())).thenAnswer(i -> {
            active.incrementAndGet();
            try {
                boolean released = false;
                while (!released) {
                    try { released = release.await(3, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { /* mimic a driver consuming cancellation */ }
                }
                return request(i.getArgument(0), i.getArgument(1));
            } finally {
                active.decrementAndGet();
                completed.countDown();
            }
        });
        try {
            for (int i = 0; i < 4; i++) {
                var result = service.diagnose("default", "slow-" + i);
                assertThat(result.state()).isEqualTo("unavailable");
                assertThat(result.message()).contains("초과");
            }
            assertThat(active.get()).isEqualTo(4);
            assertThat(service.diagnose("default", "fifth").state()).isEqualTo("busy");
            verify(collector, never()).collect("default", "fifth");
        } finally {
            release.countDown();
        }
        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        // Let the service finish the post-collection cancellation check, not just the mocked source.
        service.close();
        verify(client, after(100).never()).analyze(any());
    }

    @Test
    void timeoutReleasesOwnerAndDuplicateWaitersWithoutStartingSecondCollection() throws Exception {
        service.close();
        service = new DiagnosisService(enabled(), "observer-token", collector, client, clock, Duration.ofMillis(200));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(collector.collect("default", "blog")).thenAnswer(i -> {
            started.countDown();
            release.await(3, TimeUnit.SECONDS);
            return request("default", "blog");
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.diagnose("default", "blog"));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.diagnose("default", "blog"));
            var result = first.get(2, TimeUnit.SECONDS);
            assertThat(result.state()).isEqualTo("unavailable");
            assertThat(second.get(2, TimeUnit.SECONDS)).isSameAs(result);
            verify(collector).collect("default", "blog");
            verifyNoInteractions(client);
        } finally { release.countDown(); }
    }

    private static final class MutableClock extends Clock {
        private Instant now = NOW;
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
