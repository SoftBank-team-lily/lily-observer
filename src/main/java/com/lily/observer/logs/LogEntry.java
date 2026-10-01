package com.lily.observer.logs;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 앱 로그 한 줄. Fluent Bit 이 붙인 파드 정보에서 슬롯과 이미지 버전을 꺼내 둔다.
 *
 * @param slot  lily-cicd 슬롯 라벨 (track 또는 color). 없으면 null
 * @param image 이미지 이름:태그 (레지스트리 주소 제외). 어떤 배포에서 난 로그인지 본다
 */
public record LogEntry(
        @Schema(description = "로그 시각 (UTC)", example = "2026-10-01T06:22:49.953Z") Instant at,
        @Schema(description = "로그를 찍은 파드", example = "lily-test-green-7bfdd46749-7hm67") String pod,
        @Schema(description = "슬롯: blue · green · stable · canary", example = "green", nullable = true) String slot,
        @Schema(description = "이미지 이름:태그. 어느 배포에서 난 로그인지", example = "lily-test:20261001-053104", nullable = true) String image,
        @Schema(description = "로그 한 줄 (JSON 로그면 message · msg 필드)", example = "java.lang.IllegalStateException: chaos: forced application error") String message) {
}
