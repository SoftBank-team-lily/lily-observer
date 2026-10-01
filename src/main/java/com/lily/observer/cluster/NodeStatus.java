package com.lily.observer.cluster;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 서버(노드) 하나의 상태.
 *
 * @param role          server (control-plane) 또는 worker
 * @param ready         쿠버네티스가 이 서버를 정상으로 보는지
 * @param cpuCores      파드가 쓸 수 있는 CPU 코어 수 (allocatable)
 * @param cpuUsedCores  지금 쓰는 코어 수. metrics-server 가 없으면 null
 * @param cpuPercent    cpuUsedCores / cpuCores x 100
 * @param memoryMiB     파드가 쓸 수 있는 메모리
 * @param memoryUsedMiB 지금 쓰는 메모리
 * @param memoryPercent memoryUsedMiB / memoryMiB x 100
 * @param pods          이 서버에서 도는 파드 수
 */
public record NodeStatus(
        @Schema(example = "ip-172-31-10-248") String name,
        @Schema(description = "server (control-plane) · worker", example = "server") String role,
        @Schema(description = "쿠버네티스가 정상으로 보는지", example = "true") boolean ready,
        @Schema(description = "파드가 쓸 수 있는 CPU 코어 수", example = "2.0") double cpuCores,
        @Schema(description = "지금 쓰는 코어 수 (소수 셋째 자리)", nullable = true, example = "0.084") Double cpuUsedCores,
        @Schema(description = "CPU 사용률 (%)", nullable = true, example = "20.0") Double cpuPercent,
        @Schema(description = "파드가 쓸 수 있는 메모리 (MiB)", example = "3800.0") double memoryMiB,
        @Schema(description = "지금 쓰는 메모리 (MiB)", nullable = true, example = "1520.0") Double memoryUsedMiB,
        @Schema(description = "메모리 사용률 (%)", nullable = true, example = "40.0") Double memoryPercent,
        @Schema(description = "도는 파드 수", example = "14") int pods) {
}
