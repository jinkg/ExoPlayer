package com.yalin.exoplayer.upstream;

import android.net.Uri;
import android.support.annotation.IntDef;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.util.Assertions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public final class DataSpec {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {FLAG_ALLOW_GZIP})
    public @interface Flags {
    }

    public static final int FLAG_ALLOW_GZIP = 1;

    public final Uri uri;

    public final byte[] postBody;

    public final long absoluteStreamPosition;

    public final long position;

    public final long length;

    public final String key;

    @Flags
    public final int flags;

    public DataSpec(Uri uri) {
        this(uri, 0);
    }

    public DataSpec(Uri uri, @Flags int flags) {
        this(uri, 0, C.LENGTH_UNSET, null, flags);
    }

    public DataSpec(Uri uri, long absoluteStreamPosition, long length, String key) {
        this(uri, absoluteStreamPosition, absoluteStreamPosition, length, key, 0);
    }

    public DataSpec(Uri uri, long absoluteStreamPosition, long length, String key, @Flags int flags) {
        this(uri, absoluteStreamPosition, absoluteStreamPosition, length, key, flags);
    }

    public DataSpec(Uri uri, long absoluteStreamPosition, long position, long length, String key,
                    @Flags int flags) {
        this(uri, null, absoluteStreamPosition, position, length, key, flags);
    }

    public DataSpec(Uri uri, byte[] postBody, long absoluteStreamPosition, long position, long length,
                    String key, @Flags int flags) {
        Assertions.checkArgument(absoluteStreamPosition >= 0);
        Assertions.checkArgument(position >= 0);
        Assertions.checkArgument(length > 0 || length == C.LENGTH_UNSET);
        this.uri = uri;
        this.postBody = postBody;
        this.absoluteStreamPosition = absoluteStreamPosition;
        this.position = position;
        this.length = length;
        this.key = key;
        this.flags = flags;
    }

    @Override
    public String toString() {
        return "DataSpec{" +
                "uri=" + uri +
                ", postBody=" + Arrays.toString(postBody) +
                ", absoluteStreamPosition=" + absoluteStreamPosition +
                ", position=" + position +
                ", length=" + length +
                ", key='" + key + '\'' +
                ", flags=" + flags +
                '}';
    }
}
