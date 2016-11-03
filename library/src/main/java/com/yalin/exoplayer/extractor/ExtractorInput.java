package com.yalin.exoplayer.extractor;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public interface ExtractorInput {
    int read(byte[] target, int offset, int length) throws IOException, InterruptedException;

    boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
            throws IOException, InterruptedException;

    void readFully(byte[] target, int offset, int length) throws IOException, InterruptedException;

    int skip(int length) throws IOException, InterruptedException;

    boolean skipFully(int length, boolean allowEndOfInput) throws IOException, InterruptedException;

    void skipFully(int length) throws IOException, InterruptedException;

    boolean peekFully(byte[] target, int offset, int length, boolean allowEndOfInput)
            throws IOException, InterruptedException;

    void peekFully(byte[] target, int offset, int length) throws IOException, InterruptedException;

    boolean advancePeekPosition(int length, boolean allowEndOfInput)
            throws IOException, InterruptedException;

    void advancePeekPosition(int length) throws IOException, InterruptedException;

    void resetPeekPosition();

    long getPeekPosition();

    long getPosition();

    long getLength();

    <E extends Throwable> void setRetryPosition(long position, E e) throws E;
}
