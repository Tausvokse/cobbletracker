package com.cobbletracker.common.tracker;

import java.util.function.Supplier;

public final class CommandHookGuard {
    public enum Origin { NORMAL, COMMAND_HOOK }

    private boolean running;

    public Origin captureOrigin() {
        return running ? Origin.COMMAND_HOOK : Origin.NORMAL;
    }

    public <T> T withOrigin(Origin origin, Supplier<T> action) {
        boolean previous = running;
        running = previous || origin == Origin.COMMAND_HOOK;
        try {
            return action.get();
        } finally {
            running = previous;
        }
    }

    public boolean run(Origin origin, Runnable action) {
        if (running || origin == Origin.COMMAND_HOOK) return false;
        running = true;
        try {
            action.run();
            return true;
        } finally {
            running = false;
        }
    }
}
