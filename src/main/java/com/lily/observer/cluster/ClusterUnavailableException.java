package com.lily.observer.cluster;

/** 쿠버네티스 API 에 닿지 못했거나 권한이 없을 때 */
public class ClusterUnavailableException extends RuntimeException {

    public ClusterUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
