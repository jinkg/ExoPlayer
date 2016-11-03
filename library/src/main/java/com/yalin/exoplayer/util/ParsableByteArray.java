package com.yalin.exoplayer.util;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public final class ParsableByteArray {
    public byte[] data;

    private int position;
    private int limit;

    public ParsableByteArray() {
    }

    public ParsableByteArray(int limit) {
        this.data = new byte[limit];
        this.limit = limit;
    }

    public ParsableByteArray(byte[] data) {
        this.data = data;
        limit = data.length;
    }

    public ParsableByteArray(byte[] data, int limit) {
        this.data = data;
        this.limit = limit;
    }

    public void reset(int limit) {
    }

    public int readUnsignedShort() {
        return (data[position++] & 0xFF) << 8
                | (data[position++] & 0xFF);
    }

    public int readInt() {
        return (data[position++] & 0xFF) << 24
                | (data[position++] & 0xFF) << 16
                | (data[position++] & 0xFF) << 8
                | (data[position++] & 0xFF);
    }

    public int readUnsignedIntToInt() {
        int result = readInt();
        if (result < 0) {
            throw new IllegalStateException("Top bit not zero: " + result);
        }
        return result;
    }

    public void setPosition(int position) {
        Assertions.checkArgument(position >= 0 && position <= limit);
        this.position = position;
    }
}
