package com.cobbletracker.mod.client.theme;

public final class Anim {

    private Anim() {
    }

    public static float progress(long startMs, long durationMs) {
        if (durationMs <= 0) {
            return 1f;
        }
        float p = (System.currentTimeMillis() - startMs) / (float) durationMs;
        return p < 0 ? 0f : Math.min(1f, p);
    }

    public static float easeOut(float t) {
        t = t < 0 ? 0 : Math.min(1, t);
        float inv = 1 - t;
        return 1 - inv * inv * inv;
    }
}
