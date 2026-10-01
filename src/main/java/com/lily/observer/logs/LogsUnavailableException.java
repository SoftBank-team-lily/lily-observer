package com.lily.observer.logs;

/** 로그 저장소에 닿지 못했거나 로그 조회가 꺼져 있을 때 */
public class LogsUnavailableException extends RuntimeException {

    public LogsUnavailableException(String message) {
        super(message);
    }

    public LogsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
