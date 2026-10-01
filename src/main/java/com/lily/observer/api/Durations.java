package com.lily.observer.api;

import org.springframework.boot.convert.DurationStyle;

import java.time.Duration;

/** 쿼리 파라미터 기간 값 ("30s", "10m", "1h"). Spring MVC 는 이 형식을 바로 읽지 못한다 */
final class Durations {

    private Durations() {
    }

    static Duration parse(String name, String value, Duration min, Duration max) {
        Duration duration;
        try {
            duration = DurationStyle.SIMPLE.parse(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(name + " 는 30s, 10m, 1h 형식이어야 합니다: " + value);
        }
        if (duration.compareTo(min) < 0) {
            return min;
        }
        return duration.compareTo(max) > 0 ? max : duration;
    }

    static class BadRequestException extends RuntimeException {
        BadRequestException(String message) {
            super(message);
        }
    }
}
