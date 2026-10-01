package com.lily.observer.cluster;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * lily-cicd 로 배포된 앱 하나 (앱 목록).
 *
 * @param url           접속 주소 (Ingress 호스트). Ingress 가 없으면 null
 * @param strategy      blue-green · canary
 * @param activeSlot    지금 트래픽을 받는 슬롯. 블루그린은 Service 가 가리키는 색, 카나리는 stable
 * @param readyReplicas 준비된 파드 수 (모든 슬롯 합)
 * @param replicas      원하는 파드 수 (모든 슬롯 합)
 * @param image         지금 트래픽을 받는 슬롯의 이미지 (레지스트리 주소 제외)
 */
public record AppSummary(
        @Schema(description = "앱 이름 (다른 API 의 {app})", example = "lily-test") String app,
        @Schema(example = "default") String namespace,
        @Schema(description = "접속 주소", nullable = true, example = "https://lily-test.apps.lilycloud.kr") String url,
        @Schema(description = "blue-green · canary", example = "blue-green") String strategy,
        @Schema(description = "지금 트래픽을 받는 슬롯", nullable = true, example = "green") String activeSlot,
        @Schema(description = "준비된 파드 수 (모든 슬롯 합)", example = "1") int readyReplicas,
        @Schema(description = "원하는 파드 수 (모든 슬롯 합)", example = "1") int replicas,
        @Schema(description = "지금 트래픽을 받는 슬롯의 이미지", nullable = true, example = "lily-test:20261001-053104") String image,
        @Schema(description = "슬롯별 Deployment") List<SlotDeployment> deployments) {

    /** 슬롯별 Deployment. 0 으로 내려간 이전 슬롯도 보인다 */
    public record SlotDeployment(
            @Schema(example = "lily-test-green") String name,
            @Schema(example = "green") String slot,
            @Schema(example = "1") int readyReplicas,
            @Schema(example = "1") int replicas,
            @Schema(example = "lily-test:20261001-053104") String image) {}
}
