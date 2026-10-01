package com.lily.observer.judge;

/**
 * 위험도 0~3 과 조치. 대시보드 패널 색도 이 값을 그대로 쓴다.
 * HOLD 는 요청이 적어서 판정을 미룬 상태다.
 */
public enum RiskLevel {
    HOLD(-1, "판정 보류", "gray"),
    NORMAL(0, "없음", "green"),
    NOTICE(1, "알림", "green"),
    WARNING(2, "주의 알림", "yellow"),
    CRITICAL(3, "롤백", "red");

    private final int score;
    private final String action;
    private final String color;

    RiskLevel(int score, String action, String color) {
        this.score = score;
        this.action = action;
        this.color = color;
    }

    public int score() {
        return score;
    }

    public String action() {
        return action;
    }

    public String color() {
        return color;
    }
}
