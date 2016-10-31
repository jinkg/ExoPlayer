package com.yalin.exoplayer.util;

import android.os.HandlerThread;
import android.os.Process;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class PriorityHandlerThread extends HandlerThread {
    private final int priority;

    public PriorityHandlerThread(String name, int priority) {
        super(name, priority);
        this.priority = priority;
    }

    @Override
    public void run() {
        Process.setThreadPriority(priority);
        super.run();
    }
}
