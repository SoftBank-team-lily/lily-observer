package com.lily.observer.remediate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CriticalStreaksTest {

    private final CriticalStreaks streaks = new CriticalStreaks();

    @Test
    void firesOnceWhenCriticalReachesTheThreshold() {
        assertThat(streaks.ready("default/blog", true, 2)).isFalse();
        assertThat(streaks.ready("default/blog", true, 2)).isTrue();
        streaks.ack("default/blog");
        assertThat(streaks.ready("default/blog", true, 2)).isFalse();
    }

    @Test
    void aLowerLevelStartsTheCountAgain() {
        streaks.ready("default/blog", true, 2);
        streaks.ready("default/blog", false, 2);

        assertThat(streaks.ready("default/blog", true, 2)).isFalse();
        assertThat(streaks.ready("default/blog", true, 2)).isTrue();
    }

    @Test
    void failedSendCanRetryOnTheNextCriticalTick() {
        streaks.ready("default/blog", true, 2);
        assertThat(streaks.ready("default/blog", true, 2)).isTrue();

        assertThat(streaks.ready("default/blog", true, 2)).isTrue();
    }
}
