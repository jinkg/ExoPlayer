package com.yalin.exoplayer.upstream;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface Allocator {

    Allocation allocate();

    void release(Allocation allocation);

    void release(Allocation[] allocations);

    void trim();

    int getTotalBytesAllocated();

    int getIndividualAllocationLength();
}
