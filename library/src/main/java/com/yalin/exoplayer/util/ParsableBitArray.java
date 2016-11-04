package com.yalin.exoplayer.util;

/**
 * 作者：YaLin
 * 日期：2016/11/4.
 */

public final class ParsableBitArray {
    public byte[] data;

    private int byteOffset;
    private int bitOffset;
    private int byteLimit;

    public ParsableBitArray() {
    }

    public ParsableBitArray(byte[] data) {
        this(data, data.length);
    }

    public ParsableBitArray(byte[] data, int limit) {
        this.data = data;
        byteLimit = limit;
    }

    public void reset(byte[] data) {
        reset(data, data.length);
    }

    public void reset(byte[] data, int limit) {
        this.data = data;
        byteOffset = 0;
        bitOffset = 0;
        byteLimit = limit;
    }

    public int getPosition() {
        return byteOffset * 8 + bitOffset;
    }

    public void setPosition(int position) {
        byteOffset = position / 8;
        bitOffset = position - (byteOffset * 8);
        assertValidOffset();
    }

    public void skipBits(int n) {
        byteOffset += (n / 8);
        bitOffset += (n % 8);
        if (bitOffset > 7) {
            byteOffset++;
            bitOffset -= 8;
        }
        assertValidOffset();
    }

    public boolean readBit() {
        return readBits(1) == 1;
    }

    public int readBits(int numBits) {
        if (numBits == 0) {
            return 0;
        }

        int returnValue = 0;

        int wholeBytes = (numBits / 8);
        for (int i = 0; i < wholeBytes; i++) {
            int byteValue;
            if (bitOffset != 0) {
                byteValue = ((data[byteOffset] & 0xFF) << bitOffset)
                        | ((data[byteOffset + 1] & 0xFF) >>> (8 - bitOffset));
            } else {
                byteValue = data[byteOffset];
            }
            numBits -= 8;
            returnValue |= (byteValue & 0xFF) << numBits;
            byteOffset++;
        }

        if (numBits > 0) {
            int nextBit = bitOffset + numBits;
            byte writeMask = (byte) (0xFF >> (8 - numBits));

            if (nextBit > 8) {
                returnValue |= ((((data[byteOffset] & 0xFF) << (nextBit - 8)
                        | ((data[byteOffset + 1] & 0xFF) >> (16 - nextBit))) & writeMask));
                byteOffset++;
            } else {
                returnValue |= (((data[byteOffset] & 0xFF) >> (8 - nextBit)) & writeMask);
                if (nextBit == 8) {
                    byteOffset++;
                }
            }

            bitOffset = nextBit % 8;
        }

        assertValidOffset();
        return returnValue;
    }

    private void assertValidOffset() {
        Assertions.checkState(byteOffset >= 0
                && (bitOffset >= 0 && bitOffset < 8)
                && (byteOffset < byteLimit || (byteOffset == byteLimit && bitOffset == 0)));
    }

}
