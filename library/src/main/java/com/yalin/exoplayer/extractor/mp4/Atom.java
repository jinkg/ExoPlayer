package com.yalin.exoplayer.extractor.mp4;

import com.yalin.exoplayer.util.Util;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

abstract class Atom {

    public static final int HEADER_SIZE = 8;

    public static final int FULL_HEADER_SIZE = 12;

    public static final int LONG_HEADER_SIZE = 16;

    public static final int LONG_SIZE_PREFIX = 1;

    public static final int TYPE_pssh = Util.getIntegerCodeForString("pssh");
}
