package com.lily.observer.watch;

public enum WatchState {
    /** 감시 중 */
    WATCHING,
    /** 감시 기간 동안 위험 없음 */
    PASSED,
    /** 위험 판정으로 롤백함 (롤백이 꺼져 있으면 롤백했을 것으로 기록) */
    ROLLED_BACK,
    /** lily-cicd 가 배포 실패를 알림 */
    DEPLOY_FAILED,
    /** 같은 앱의 새 배포가 들어와 대체됨 */
    REPLACED
}
