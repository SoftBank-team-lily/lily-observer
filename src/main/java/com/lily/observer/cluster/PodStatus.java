package com.lily.observer.cluster;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 앱 파드 하나의 상태 (파드 상태 패널).
 *
 * @param phase           Pending · Running · Succeeded · Failed · Unknown
 * @param ready           모든 컨테이너가 트래픽을 받을 준비가 됐는지 (readiness 통과)
 * @param restarts        컨테이너 재시작 횟수 합
 * @param slot            lily-cicd 슬롯 (track 또는 color). 없으면 null
 * @param image           이미지 이름:태그 (레지스트리 주소 제외)
 * @param problem         지금 문제 (CrashLoopBackOff · ImagePullBackOff · ErrImagePull · CreateContainerConfigError 등). 없으면 null
 * @param lastRestartReason 마지막 재시작 이유 (OOMKilled · Error 등). 재시작이 없으면 null
 * @param cpuMillicores   현재 CPU 사용량 (1000 = 1코어). metrics-server 가 없으면 null
 * @param memoryMiB       현재 메모리 사용량. metrics-server 가 없으면 null
 */
public record PodStatus(
        @Schema(example = "lily-test-green-7bfdd46749-7hm67") String name,
        @Schema(description = "Pending · Running · Succeeded · Failed · Unknown", example = "Running") String phase,
        @Schema(description = "트래픽을 받을 준비가 됐는지 (readiness 통과)", example = "true") boolean ready,
        @Schema(description = "컨테이너 재시작 횟수", example = "0") int restarts,
        @Schema(description = "슬롯: blue · green · stable · canary", example = "green", nullable = true) String slot,
        @Schema(description = "이미지 이름:태그", example = "lily-test:20261001-053104") String image,
        @Schema(description = "떠 있는 서버", example = "ip-172-31-15-165") String node,
        @Schema(description = "파드 시작 시각 (UTC)") Instant startedAt,
        @Schema(description = "지금 문제: CrashLoopBackOff · ImagePullBackOff 등. 없으면 null", nullable = true, example = "null") String problem,
        @Schema(description = "마지막 재시작 이유: OOMKilled · Error 등. 없으면 null", nullable = true, example = "null") String lastRestartReason,
        @Schema(description = "CPU 사용량 (1000 = 1코어)", nullable = true, example = "12.0") Double cpuMillicores,
        @Schema(description = "메모리 사용량 (MiB)", nullable = true, example = "240.5") Double memoryMiB) {
}
