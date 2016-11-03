package com.yalin.exoplayer;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public class ParserException extends IOException {
    public ParserException() {
    }

    public ParserException(String message) {
        super(message);
    }

    public ParserException(Throwable cause) {
        super(cause);
    }

    public ParserException(String message, Throwable cause) {
        super(message, cause);
    }

}
