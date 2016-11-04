package com.yalin.exoplayer.util;

import java.nio.charset.Charset;

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

    public void setLimit(int limit) {
        Assertions.checkArgument(limit >= 0 && limit <= data.length);
        this.limit = limit;
    }

    public void reset(int limit) {
        position = 0;
        limit = 0;
    }

    public void reset(byte[] data, int limit) {
        this.data = data;
        this.limit = limit;
        position = 0;
    }

    public void reset() {
        position = 0;
        limit = 0;
    }

    public int bytesLeft() {
        return limit - position;
    }

    public int limit() {
        return limit;
    }

    public int getPosition() {
        return position;
    }

    public int capacity() {
        return data == null ? 0 : data.length;
    }

    public void setPosition(int position) {
        Assertions.checkArgument(position >= 0 && position <= limit);
        this.position = position;
    }

    public void skipBytes(int bytes) {
        setPosition(position + bytes);
    }

    public void readBytes(ParsableBitArray bitArray, int length) {
        readBytes(bitArray.data, 0, length);
        bitArray.setPosition(0);
    }

    public void readBytes(byte[] buffer, int offset, int length) {
        System.arraycopy(data, position, buffer, offset, length);
        position += length;
    }

    public int readUnsignedShort() {
        return (data[position++] & 0xFF) << 8
                | (data[position++] & 0xFF);
    }

    public int readUnsignedByte() {
        return (data[position++] & 0xFF);
    }

    public long readUnsignedInt() {
        return (data[position++] & 0xFFL) << 24
                | (data[position++] & 0xFFL) << 16
                | (data[position++] & 0xFFL) << 8
                | (data[position++] & 0xFFL);
    }

    public int readUnsignedFixedPoint1616() {
        int result = (data[position++] & 0xFF) << 8
                | (data[position++] & 0xFF);
        position += 2; // Skip the non-integer portion.
        return result;
    }

    public short readShort() {
        return (short) ((data[position++] & 0xFF) << 8
                | (data[position++] & 0xFF));
    }


    public int readInt() {
        return (data[position++] & 0xFF) << 24
                | (data[position++] & 0xFF) << 16
                | (data[position++] & 0xFF) << 8
                | (data[position++] & 0xFF);
    }

    public long readLong() {
        return (data[position++] & 0xFFL) << 56
                | (data[position++] & 0xFFL) << 48
                | (data[position++] & 0xFFL) << 40
                | (data[position++] & 0xFFL) << 32
                | (data[position++] & 0xFFL) << 24
                | (data[position++] & 0xFFL) << 16
                | (data[position++] & 0xFFL) << 8
                | (data[position++] & 0xFFL);
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public String readString(int length) {
        return readString(length, Charset.defaultCharset());
    }

    public String readString(int length, Charset charset) {
        String result = new String(data, position, length, charset);
        position += length;
        return result;
    }

    public int readUnsignedIntToInt() {
        int result = readInt();
        if (result < 0) {
            throw new IllegalStateException("Top bit not zero: " + result);
        }
        return result;
    }

    public long readUnsignedLongToLong() {
        long result = readLong();
        if (result < 0) {
            throw new IllegalStateException("Top bit not zero: " + result);
        }
        return result;
    }

    public int readSynchSafeInt() {
        int b1 = readUnsignedByte();
        int b2 = readUnsignedByte();
        int b3 = readUnsignedByte();
        int b4 = readUnsignedByte();
        return (b1 << 21) | (b2 << 14) | (b3 << 7) | b4;
    }
}
