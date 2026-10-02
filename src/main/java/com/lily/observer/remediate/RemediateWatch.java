package com.lily.observer.remediate;

import com.lily.observer.ObserverProperties;
import com.lily.observer.cluster.AppSummary;
import com.lily.observer.cluster.ClusterSource;
import com.lily.observer.judge.RiskLevel;
import com.lily.observer.status.AppStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 상태 조회와 같은 판정을 주기마다 한다. CRITICAL 이 연속 기준에 닿으면 remediate 를 한 번 시도한다.
 * 스위치가 꺼져 있으면 세기는 하되 로그를 읽거나 frontend 로 보내지 않는다. 롤백은 하지 않는다.
 */
@Component
public class RemediateWatch implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RemediateWatch.class);
    private static final String NAMESPACE = "default";

    private final int consecutive;
    private final Duration interval;
    private final RemediateProperties remediate;
    private final ClusterSource cluster;
    private final AppStatusService status;
    private final RemediateSender sender;
    private final CriticalStreaks streaks = new CriticalStreaks();

    @Autowired
    public RemediateWatch(ObserverProperties observer, RemediateProperties remediate, ClusterSource cluster,
                          AppStatusService status, RemediateSender sender) {
        this(observer.judge().consecutive(), observer.watch().interval(), remediate, cluster, status, sender);
    }

    RemediateWatch(int consecutive, Duration interval, RemediateProperties remediate, ClusterSource cluster,
                   AppStatusService status, RemediateSender sender) {
        this.consecutive = consecutive;
        this.interval = interval;
        this.remediate = remediate;
        this.cluster = cluster;
        this.status = status;
        this.sender = sender;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::tick, interval);
    }

    void tick() {
        List<AppSummary> apps;
        try {
            apps = cluster.apps(NAMESPACE);
        } catch (RuntimeException e) {
            log.warn("watch apps failed: {}", e.getMessage());
            return;
        }
        for (AppSummary app : apps) {
            if (!NAMESPACE.equals(app.namespace())) {
                continue;
            }
            try {
                RiskLevel level = status.status(app.namespace(), app.app()).level();
                String key = app.namespace() + "/" + app.app();
                if (streaks.ready(key, level == RiskLevel.CRITICAL, consecutive) && remediate.enabled()) {
                    try {
                        sender.send(app.namespace(), app.app());
                        streaks.ack(key);
                    } catch (RuntimeException e) {
                        log.warn("remediate send failed: app={} message={}", app.app(), e.getMessage());
                    }
                }
            } catch (RuntimeException e) {
                log.warn("watch judge failed: app={} message={}", app.app(), e.getMessage());
            }
        }
    }
}
