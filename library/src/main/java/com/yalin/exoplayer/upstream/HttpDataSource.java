package com.yalin.exoplayer.upstream;

import android.support.annotation.IntDef;
import android.text.TextUtils;


import com.yalin.exoplayer.util.Predicate;
import com.yalin.exoplayer.util.Util;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public interface HttpDataSource extends DataSource {
    interface Factory extends DataSource.Factory {
        @Override
        HttpDataSource createDataSource();
    }

    Predicate<String> REJECT_PAYWALL_TYPES = new Predicate<String>() {
        @Override
        public boolean evaluate(String contentType) {
            contentType = Util.toLowerInvariant(contentType);
            return !TextUtils.isEmpty(contentType)
                    && (!contentType.contains("text") || contentType.contains("text/vtt"))
                    && !contentType.contains("html") && !contentType.contains("xml");
        }
    };

    class HttpDataSourceException extends IOException {

        @Retention(RetentionPolicy.SOURCE)
        @IntDef({TYPE_OPEN, TYPE_READ, TYPE_CLOSE})
        public @interface Type {
        }

        public static final int TYPE_OPEN = 1;
        public static final int TYPE_READ = 2;
        public static final int TYPE_CLOSE = 3;

        @Type
        public final int type;

        public final DataSpec dataSpec;

        public HttpDataSourceException(String message, DataSpec dataSpec, @Type int type) {
            super(message);
            this.dataSpec = dataSpec;
            this.type = type;
        }

        public HttpDataSourceException(IOException cause, DataSpec dataSpec, @Type int type) {
            super(cause);
            this.dataSpec = dataSpec;
            this.type = type;
        }

        public HttpDataSourceException(String message, IOException cause, DataSpec dataSpec,
                                       @Type int type) {
            super(message, cause);
            this.dataSpec = dataSpec;
            this.type = type;
        }
    }

    final class InvalidContentTypeException extends HttpDataSourceException {
        public final String contentType;

        public InvalidContentTypeException(String contentType, DataSpec dataSpec) {
            super("Invalid content type: " + contentType, dataSpec, TYPE_OPEN);
            this.contentType = contentType;
        }
    }

    final class InvalidResponseCodeException extends HttpDataSourceException {
        public final int responseCode;

        public final Map<String, List<String>> headerFields;

        public InvalidResponseCodeException(int responseCode, Map<String, List<String>> headerFields,
                                            DataSpec dataSpec) {
            super("Response code: " + responseCode, dataSpec, TYPE_OPEN);
            this.responseCode = responseCode;
            this.headerFields = headerFields;
        }
    }

    @Override
    long open(DataSpec dataSpec) throws HttpDataSourceException;

    @Override
    int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException;

    @Override
    void close() throws HttpDataSourceException;

    void setRequestProperty(String name, String value);

    void clearRequestProperty(String name);

    void clearAllRequestProperties();

    Map<String, List<String>> getResponseHeaders();
}
