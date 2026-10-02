package com.lily.observer.diagnosis;

import com.lily.observer.ObserverProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coalesce requests by namespace/app and keep at most 128 snapshots for 30 seconds per process. */
@Service
public class DiagnosisService {
    private record Scope(String namespace, String app) {}
    private record Cached(Instant expires, Diagnosis.Result result) {}
    private final Map<Scope, Cached> cache = new LinkedHashMap<>();
    private final Map<Scope, CompletableFuture<Diagnosis.Result>> pending = new HashMap<>();
    private final DiagnosisProperties properties;
    private final boolean authenticated;
    private final DiagnosisCollector collector;
    private final DiagnosisClient client;
    private final Clock clock;
    private final Duration timeout;
    // No queue: even a source that ignores interruption can occupy at most four workers.
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofVirtual().name("diagnosis-", 0).factory());

    @Autowired
    public DiagnosisService(DiagnosisProperties properties, ObserverProperties observer,
                            DiagnosisCollector collector, DiagnosisClient client) {
        this(properties, observer.apiToken(), collector, client, Clock.systemUTC());
    }

    DiagnosisService(DiagnosisProperties properties, String observerToken, DiagnosisCollector collector,
                     DiagnosisClient client, Clock clock) {
        this(properties, observerToken, collector, client, clock, Duration.ofSeconds(20));
    }

    DiagnosisService(DiagnosisProperties properties, String observerToken, DiagnosisCollector collector,
                     DiagnosisClient client, Clock clock, Duration timeout) {
        this.properties = properties;
        this.authenticated = observerToken != null && !observerToken.isBlank();
        this.collector = collector;
        this.client = client;
        this.clock = clock;
        this.timeout = timeout;
    }

    public Diagnosis.Result diagnose(String namespace, String app) {
        if (!properties.enabled() || !authenticated || properties.apiToken() == null || properties.apiToken().isBlank()) {
            return Diagnosis.Result.unavailable(app, namespace, "disabled", "진단 연결과 인증 설정이 필요합니다.");
        }
        Scope scope = new Scope(namespace, app);
        CompletableFuture<Diagnosis.Result> future;
        boolean owner = false;
        synchronized (this) {
            cache.entrySet().removeIf(e -> !e.getValue().expires().isAfter(clock.instant()));
            Cached saved = cache.get(scope);
            if (saved != null) return saved.result();
            future = pending.get(scope);
            if (future == null) {
                if (pending.size() >= 4) return Diagnosis.Result.unavailable(app, namespace, "busy", "다른 진단이 진행 중입니다. 잠시 후 다시 요청하세요.");
                future = new CompletableFuture<>();
                pending.put(scope, future);
                owner = true;
            }
        }
        if (!owner) return await(future, namespace, app);
        Diagnosis.Result result = run(namespace, app);
        synchronized (this) {
            // Briefly cache failures too, so a broken upstream cannot cause a retry storm.
            Duration ttl = Duration.ofSeconds("ready".equals(result.state()) ? 30 : 5);
            if (cache.size() >= 128) cache.remove(cache.keySet().iterator().next());
            cache.put(scope, new Cached(clock.instant().plus(ttl), result));
            future.complete(result);
            pending.remove(scope);
        }
        return result;
    }

    private Diagnosis.Result run(String namespace, String app) {
        Future<Diagnosis.Result> task = null;
        AtomicBoolean cancelled = new AtomicBoolean();
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            task = workers.submit(() -> collect(namespace, app, deadline, cancelled));
            return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            return Diagnosis.Result.unavailable(app, namespace, "busy", "관측 작업이 진행 중입니다. 잠시 후 다시 요청하세요.");
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return Diagnosis.Result.unavailable(app, namespace, "unavailable", "진단 요청이 중단되었습니다.");
        } catch (TimeoutException ignored) {
            return Diagnosis.Result.unavailable(app, namespace, "unavailable", "관측·진단 응답 시간이 초과되었습니다.");
        } catch (ExecutionException ignored) {
            return Diagnosis.Result.unavailable(app, namespace, "unavailable", "관측 자료를 수집할 수 없습니다.");
        } finally {
            if (task != null && !task.isDone()) {
                cancelled.set(true);
                task.cancel(true);
            }
        }
    }

    private Diagnosis.Result await(CompletableFuture<Diagnosis.Result> future, String namespace, String app) {
        try {
            return future.get(timeout.plusSeconds(1).toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ignored) { /* bounded wait; owner retains the result */ }
        return Diagnosis.Result.unavailable(app, namespace, "busy", "진단이 진행 중입니다. 잠시 후 다시 요청하세요.");
    }

    private Diagnosis.Result collect(String namespace, String app, long deadline, AtomicBoolean cancelled) {
        var request = collector.collect(namespace, app);
        Diagnosis.Analysis analysis = null;
        if (!request.evidence().isEmpty() && !cancelled.get() && System.nanoTime() - deadline < 0
                && !Thread.currentThread().isInterrupted()) {
            try { analysis = client.analyze(request); } catch (RuntimeException ignored) { /* preserve evidence */ }
        }
        return new Diagnosis.Result(app, namespace, request.observedAt(), analysis == null ? "unavailable" : "ready",
                request.evidence(), request.missingSources(), analysis,
                analysis == null ? "수집 자료가 없거나 진단 서비스에 연결할 수 없습니다." : "진단은 검토 제안이며 운영 변경을 실행하지 않습니다.");
    }

    @PreDestroy
    public void close() { workers.shutdownNow(); }
}
