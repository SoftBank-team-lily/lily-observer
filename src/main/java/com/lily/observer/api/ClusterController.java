package com.lily.observer.api;

import com.lily.observer.cluster.AppSummary;
import com.lily.observer.cluster.ClusterSource;
import com.lily.observer.cluster.NodeStatus;
import com.lily.observer.cluster.PodStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 앱 목록 · 파드 상태 · 서버 지표. 쿠버네티스 API 와 metrics-server 에서 읽는다 */
@Tag(name = "클러스터", description = "배포된 앱 목록, 파드 상태, 서버 CPU · 메모리")
@Validated
@RestController
public class ClusterController {

    private final ClusterSource cluster;

    public ClusterController(ClusterSource cluster) {
        this.cluster = cluster;
    }

    @Operation(summary = "배포된 앱 목록",
            description = "lily-cicd 로 배포된 앱(슬롯 라벨이 있는 Deployment). 접속 주소, 배포 방식, 지금 트래픽을 받는 슬롯, 준비된 파드 수, 이미지.")
    @GetMapping("/api/apps")
    public List<AppSummary> apps(
            @Parameter(description = "네임스페이스")
            @RequestParam(defaultValue = "default") @Pattern(regexp = AppMetricsController.NAME) String namespace) {
        return cluster.apps(namespace);
    }

    @Operation(summary = "앱 파드 상태",
            description = "파드마다 Ready, 재시작 수, 슬롯, 이미지, 문제(CrashLoopBackOff 등), 마지막 재시작 이유(OOMKilled 등), CPU · 메모리 사용량.")
    @GetMapping("/api/apps/{app}/pods")
    public List<PodStatus> pods(
            @Parameter(description = "배포할 때 쓴 앱 이름", example = "lily-test")
            @PathVariable @Pattern(regexp = AppMetricsController.NAME) String app,
            @Parameter(description = "앱 네임스페이스")
            @RequestParam(defaultValue = "default") @Pattern(regexp = AppMetricsController.NAME) String namespace) {
        return cluster.pods(namespace, app);
    }

    @Operation(summary = "서버(노드) 상태",
            description = "서버 3대의 역할, Ready, CPU · 메모리 용량과 사용량(%), 파드 수. 사용량은 metrics-server 값이라 수십 초 늦을 수 있습니다.")
    @GetMapping("/api/nodes")
    public List<NodeStatus> nodes() {
        return cluster.nodes();
    }
}
