package com.yalin.exoplayer.upstream;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public final class Allocation {
    public final byte[] data;

    private final int offset;

    public Allocation(byte[] data, int offset) {
        this.data = data;
        this.offset = offset;
    }

    public int translateOffset(int offset) {
        return this.offset + offset;
    }
}
