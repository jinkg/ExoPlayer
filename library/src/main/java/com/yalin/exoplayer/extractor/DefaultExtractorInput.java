package com.yalin.exoplayer.extractor;

import com.yalin.exoplayer.upstream.DataSource;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public final class DefaultExtractorInput implements ExtractorInput {
    public DefaultExtractorInput(DataSource dataSource, long position, long length) {

    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException, InterruptedException {
        return 0;
    }

    @Override
    public boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        return false;
    }

    @Override
    public void readFully(byte[] target, int offset, int length) throws IOException, InterruptedException {

    }

    @Override
    public int skip(int length) throws IOException, InterruptedException {
        return 0;
    }

    @Override
    public boolean skipFully(int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        return false;
    }

    @Override
    public void skipFully(int length) throws IOException, InterruptedException {

    }

    @Override
    public boolean peekFully(byte[] target, int offset, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        return false;
    }

    @Override
    public void peekFully(byte[] target, int offset, int length) throws IOException, InterruptedException {

    }

    @Override
    public boolean advancePeekPosition(int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        return false;
    }

    @Override
    public void advancePeekPosition(int length) throws IOException, InterruptedException {

    }

    @Override
    public void resetPeekPosition() {

    }

    @Override
    public long getPeekPosition() {
        return 0;
    }

    @Override
    public long getPosition() {
        return 0;
    }

    @Override
    public long getLength() {
        return 0;
    }

    @Override
    public <E extends Throwable> void setRetryPosition(long position, E e) throws E {

    }
}
