package com.lily.observer.logs;

import java.time.Instant;

/**
 * 앱 로그 한 줄. Fluent Bit 이 붙인 파드 정보에서 슬롯과 이미지 버전을 꺼내 둔다.
 *
 * @param slot  lily-cicd 슬롯 라벨 (track 또는 color). 없으면 null
 * @param image 이미지 이름:태그 (레지스트리 주소 제외). 어떤 배포에서 난 로그인지 본다
 */
public record LogEntry(Instant at, String pod, String slot, String image, String message) {
}
