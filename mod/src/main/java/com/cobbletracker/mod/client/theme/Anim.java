package com.cobbletracker.mod.client.theme;

/**
 * Easing helpers for the screen animations. Pure math on a 0..1 progress value taken from
 * {@code System.currentTimeMillis()} deltas, so nothing here needs a tick hook.
 */
public final class Anim {

    private Anim() {
    }

    /** Progress in [0,1] of an animation that started {@code startMs} ago and lasts {@code durationMs}. */
    public static float progress(long startMs, long durationMs) {
        if (durationMs <= 0) {
            return 1f;
        }
        float p = (System.currentTimeMillis() - startMs) / (float) durationMs;
        return p < 0 ? 0f : Math.min(1f, p);
    }

    /** Ease-out cubic - fast then settling, which is what a panel fading in wants. */
    public static float easeOut(float t) {
        t = t < 0 ? 0 : Math.min(1, t);
        float inv = 1 - t;
        return 1 - inv * inv * inv;
    }
}
