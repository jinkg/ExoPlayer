package com.yalin.exoplayer.util;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public final class ConditionVariable {
    private boolean isOpen;

    public synchronized boolean open() {
        if (isOpen) {
            return false;
        }
        isOpen = true;
        notifyAll();
        return true;
    }

    public synchronized boolean close() {
        boolean wasOpen = isOpen;
        isOpen = false;
        return wasOpen;
    }

    public synchronized void block() throws InterruptedException {
        while (!isOpen) {
            wait();
        }
    }
}
