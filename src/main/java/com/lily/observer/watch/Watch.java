package com.lily.observer.watch;

import java.time.Instant;

/**
 * 배포 한 건의 감시 상태. lily-cicd 가 트래픽 전환을 끝내고 attached 를 부르면 생긴다.
 *
 * @param targetSlot   새 버전이 올라간 슬롯 (canary, blue, green, 첫 배포면 stable)
 * @param baselineSlot 비교할 기존 버전 슬롯. canary 면 stable, 나머지는 null
 */
public record Watch(
        String app,
        String namespace,
        String version,
        Strategy strategy,
        String targetSlot,
        String baselineSlot,
        Instant startedAt,
        WatchState state) {

    public Watch withState(WatchState next) {
        return new Watch(app, namespace, version, strategy, targetSlot, baselineSlot, startedAt, next);
    }

    /** lily-cicd 슬롯 이름으로 전략을 알아낸다 */
    public static Strategy strategyOf(String slot) {
        return switch (slot == null ? "" : slot) {
            case "stable", "canary" -> Strategy.CANARY;
            case "blue", "green" -> Strategy.BLUE_GREEN;
            default -> throw new IllegalArgumentException("알 수 없는 슬롯: " + slot);
        };
    }

    public enum Strategy {
        CANARY("track"),
        BLUE_GREEN("color");

        /** lily-cicd 가 파드에 붙이는 슬롯 라벨 이름 */
        private final String slotLabel;

        Strategy(String slotLabel) {
            this.slotLabel = slotLabel;
        }

        public String slotLabel() {
            return slotLabel;
        }
    }
}
