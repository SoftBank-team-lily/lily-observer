package com.lily.observer.remediate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 앱마다 CRITICAL 이 연속으로 몇 번 나왔는지. 기준에 닿은 틱에만 보내고, 등급이 내려가면 다시 센다.
 */
public final class CriticalStreaks {

    private final Map<String, Integer> count = new HashMap<>();
    private final Set<String> sent = new HashSet<>();

    /** 이번 틱에 remediate 를 시도할 차례면 true. 끄고 켜도, 아직 안 보냈으면 다음 CRITICAL 틱에 보낸다. */
    public boolean ready(String key, boolean critical, int consecutive) {
        if (consecutive < 1) {
            return false;
        }
        if (!critical) {
            count.remove(key);
            sent.remove(key);
            return false;
        }
        int next = count.merge(key, 1, (prev, one) -> Math.min(prev + one, consecutive));
        return next >= consecutive && !sent.contains(key);
    }

    public void ack(String key) {
        sent.add(key);
    }
}
