package com.lily.observer.cluster;

import java.util.List;

/** 쿠버네티스에서 앱 · 파드 · 서버 상태를 읽는다 (조회 전용) */
public interface ClusterSource {

    List<AppSummary> apps(String namespace);

    List<PodStatus> pods(String namespace, String app);

    List<NodeStatus> nodes();
}
