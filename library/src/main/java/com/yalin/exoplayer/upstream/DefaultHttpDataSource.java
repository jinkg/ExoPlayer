package com.yalin.exoplayer.upstream;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.Predicate;
import com.yalin.exoplayer.util.Util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public class DefaultHttpDataSource implements HttpDataSource {

    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;

    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

    private static final String TAG = "DefaultHttpDataSource";
    private static final int MAX_REDIRECTS = 20;
    private static final long MAX_BYTES_TO_DRAIN = 2048;
    private static final Pattern CONTENT_RANGE_HEADER =
            Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");
    private static final AtomicReference<byte[]> skipBufferReference = new AtomicReference<>();

    private final boolean allowCrossProtocolRedirects;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final String userAgent;
    private final Predicate<String> contentTypePredicate;
    private final HashMap<String, String> requestProperties;
    private final TransferListener<? super DefaultHttpDataSource> listener;

    private DataSpec dataSpec;
    private HttpURLConnection connection;
    private InputStream inputStream;
    private boolean opened;

    private long bytesToSkip;
    private long bytesToRead;

    private long bytesSkipped;
    private long bytesRead;

    public DefaultHttpDataSource(String userAgent, Predicate<String> contentTypePredicate,
                                 TransferListener<? super DefaultHttpDataSource> listener) {
        this(userAgent, contentTypePredicate, listener, DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DEFAULT_READ_TIMEOUT_MILLIS);
    }

    public DefaultHttpDataSource(String userAgent, Predicate<String> contentTypePredicate,
                                 TransferListener<? super DefaultHttpDataSource> listener, int connectTimeoutMillis,
                                 int readTimeoutMillis) {
        this(userAgent, contentTypePredicate, listener, connectTimeoutMillis, readTimeoutMillis, false);
    }

    public DefaultHttpDataSource(String userAgent, Predicate<String> contentTypePredicate,
                                 TransferListener<? super DefaultHttpDataSource> listener, int connectTimeoutMillis,
                                 int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
        this.userAgent = Assertions.checkNotEmpty(userAgent);
        this.contentTypePredicate = contentTypePredicate;
        this.listener = listener;
        this.requestProperties = new HashMap<>();
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
    }

    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException {
        this.dataSpec = dataSpec;
        this.bytesRead = 0;
        this.bytesSkipped = 0;
        try {
            connection = makeConnection(dataSpec);
        } catch (IOException e) {
            throw new HttpDataSourceException("Unable to connect to " + dataSpec.uri.toString(), e,
                    dataSpec, HttpDataSourceException.TYPE_OPEN);
        }
        int responseCode;
        try {
            responseCode = connection.getResponseCode();
        } catch (IOException e) {
            closeConnectionQuietly();
            throw new HttpDataSourceException("Unbale to connect to " + dataSpec.uri.toString(), e,
                    dataSpec, HttpDataSourceException.TYPE_OPEN);
        }

        if (responseCode < 200 || responseCode > 299) {
            Map<String, List<String>> headers = connection.getHeaderFields();
            closeConnectionQuietly();
            InvalidResponseCodeException exception =
                    new InvalidResponseCodeException(responseCode, headers, dataSpec);
            if (responseCode == 416) {
                exception.initCause(new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE));
            }
            throw exception;
        }

        String contentType = connection.getContentType();
        if (contentTypePredicate != null && !contentTypePredicate.evaluate(contentType)) {
            closeConnectionQuietly();
            throw new InvalidContentTypeException(contentType, dataSpec);
        }

        bytesToSkip = responseCode == 200 && dataSpec.position != 0 ? dataSpec.position : 0;

        if ((dataSpec.flags & DataSpec.FLAG_ALLOW_GZIP) == 0) {
            if (dataSpec.length != C.LENGTH_UNSET) {
                bytesToRead = dataSpec.length;
            } else {
                long contentLength = getContentLength(connection);
                bytesToRead = contentLength != C.LENGTH_UNSET ? (contentLength - bytesToSkip)
                        : C.LENGTH_UNSET;
            }
        } else {
            bytesToRead = dataSpec.length;
        }

        try {
            inputStream = connection.getInputStream();
        } catch (IOException e) {
            closeConnectionQuietly();
            throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_OPEN);
        }

        opened = true;
        if (listener != null) {
            listener.onTransferStart(this, dataSpec);
        }

        return bytesToRead;
    }

    protected final long bytesRemaining() {
        return bytesToRead == C.LENGTH_UNSET ? bytesToRead : bytesToRead - bytesRead;
    }

    private HttpURLConnection makeConnection(DataSpec dataSpec) throws IOException {
        URL url = new URL(dataSpec.uri.toString());
        byte[] postBody = dataSpec.postBody;
        long position = dataSpec.position;
        long length = dataSpec.length;
        boolean allowGzip = (dataSpec.flags & DataSpec.FLAG_ALLOW_GZIP) != 0;

        if (!allowCrossProtocolRedirects) {
            return makeConnection(url, postBody, position, length, allowGzip, true);
        }
        int redirectCount = 0;
        while (redirectCount++ <= MAX_REDIRECTS) {
            HttpURLConnection connection = makeConnection(url, postBody, position, length, allowGzip, false);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MULT_CHOICE
                    || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                    || (postBody == null && (responseCode == 307
                    || responseCode == 308))) {
                postBody = null;
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                url = handleRedirect(url, location);
            } else {
                return connection;
            }
        }

        throw new NoRouteToHostException("Too many redirects: " + redirectCount);
    }

    private HttpURLConnection makeConnection(URL url, byte[] postBody, long position,
                                             long length, boolean allowGzip, boolean followRedirects) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        synchronized (requestProperties) {
            for (Map.Entry<String, String> property : requestProperties.entrySet()) {
                connection.setRequestProperty(property.getKey(), property.getValue());
            }
        }
        if (!(position == 0 && length == C.LENGTH_UNSET)) {
            String rangeRequest = "bytes=" + position + "-";
            if (length != C.LENGTH_UNSET) {
                rangeRequest += (position + length - 1);
            }
            connection.setRequestProperty("Range", rangeRequest);
        }
        connection.setRequestProperty("User-Agent", userAgent);
        if (!allowGzip) {
            connection.setRequestProperty("Accept-Encoding", "identity");
        }
        connection.setInstanceFollowRedirects(followRedirects);
        connection.setDoOutput(postBody != null);
        if (postBody != null) {
            connection.setRequestMethod("POST");
            if (postBody.length == 0) {
                connection.connect();
            } else {
                connection.setFixedLengthStreamingMode(postBody.length);
                connection.connect();
                OutputStream os = connection.getOutputStream();
                os.write(postBody);
                os.close();
            }
        } else {
            connection.connect();
        }
        return connection;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
        try {
            skipInternal();
            return readInternal(buffer, offset, readLength);
        } catch (IOException e) {
            throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_READ);
        }
    }

    @Override
    public Uri getUri() {
        return connection == null ? null : Uri.parse(connection.getURL().toString());
    }

    @Override
    public void close() throws HttpDataSourceException {
        try {
            if (inputStream != null) {
                maybeTerminateInputStream(connection, bytesRemaining());
                try {
                    inputStream.close();
                } catch (IOException e) {
                    throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_CLOSE);
                }
            }
        } finally {
            inputStream = null;
            closeConnectionQuietly();
            if (opened) {
                opened = false;
                if (listener != null) {
                    listener.onTransferEnd(this);
                }
            }
        }
    }

    @Override
    public void setRequestProperty(String name, String value) {
        Assertions.checkNotNull(name);
        Assertions.checkNotNull(value);
        synchronized (requestProperties) {
            requestProperties.put(name, value);
        }
    }

    @Override
    public void clearRequestProperty(String name) {
        Assertions.checkNotNull(name);
        synchronized (requestProperties) {
            requestProperties.remove(name);
        }
    }

    @Override
    public void clearAllRequestProperties() {
        synchronized (requestProperties) {
            requestProperties.clear();
        }
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return connection == null ? null : connection.getHeaderFields();
    }

    protected final HttpURLConnection getConnection() {
        return connection;
    }

    protected final long bytesSkipped() {
        return bytesSkipped;
    }

    protected final long bytesRead() {
        return bytesRead;
    }

    private int readInternal(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        }
        if (bytesToRead != C.LENGTH_UNSET) {
            long bytesRemaining = bytesToRead - bytesRead;
            if (bytesRemaining == 0) {
                return C.RESULT_END_OF_INPUT;
            }
            readLength = (int) Math.min(readLength, bytesRemaining);
        }

        int read = inputStream.read(buffer, offset, readLength);
        if (read == -1) {
            if (bytesToRead != C.LENGTH_UNSET) {
                throw new EOFException();
            }
            return C.RESULT_END_OF_INPUT;
        }
        bytesRead += read;
        if (listener != null) {
            listener.onBytesTransferred(this, read);
        }
        return read;
    }

    private void skipInternal() throws IOException {
        if (bytesSkipped == bytesToSkip) {
            return;
        }

        byte[] skipBuffer = skipBufferReference.getAndSet(null);
        if (skipBuffer == null) {
            skipBuffer = new byte[4096];
        }

        while (bytesSkipped != bytesToSkip) {
            int readLength = (int) Math.min(bytesToRead - bytesSkipped, skipBuffer.length);
            int read = inputStream.read(skipBuffer, 0, readLength);
            if (Thread.interrupted()) {
                throw new InterruptedIOException();
            }
            if (read == -1) {
                throw new EOFException();
            }
            bytesSkipped += read;
            if (listener != null) {
                listener.onBytesTransferred(this, read);
            }
        }

        skipBufferReference.set(skipBuffer);
    }

    private static long getContentLength(HttpURLConnection connection) {
        long contentLength = C.LENGTH_UNSET;
        String contentLengthHeader = connection.getHeaderField("Content-Length");
        if (!TextUtils.isEmpty(contentLengthHeader)) {
            try {
                contentLength = Long.parseLong(contentLengthHeader);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Unexpected Content-Length [" + contentLengthHeader + "]");
            }
        }
        String contentRangeHeader = connection.getHeaderField("Content-Range");
        if (!TextUtils.isEmpty(contentRangeHeader)) {
            Matcher matcher = CONTENT_RANGE_HEADER.matcher(contentLengthHeader);
            if (matcher.find()) {
                try {
                    long contentLengthFromRange =
                            Long.parseLong(matcher.group(2)) - Long.parseLong(matcher.group(1)) + 1;
                    if (contentLength < 0) {
                        contentLength = contentLengthFromRange;
                    } else if (contentLength != contentLengthFromRange) {
                        Log.w(TAG, "Inconsistent headers [" + contentLengthHeader + "] [" + contentRangeHeader
                                + "]");
                        contentLength = Math.max(contentLength, contentLengthFromRange);
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Unexpected Content-Range [" + contentRangeHeader + "]");
                }
            }
        }
        return contentLength;
    }

    private static URL handleRedirect(URL originalUrl, String location) throws IOException {
        if (location == null) {
            throw new ProtocolException("Null location redirect");
        }

        URL url = new URL(originalUrl, location);
        String protocol = url.getProtocol();
        if (!"https".equals(protocol) && !"http".equals(protocol)) {
            throw new ProtocolException("Unsupported protocol redirect: " + protocol);
        }
        return url;
    }

    private static void maybeTerminateInputStream(HttpURLConnection connection, long bytesRemaining) {
        if (Util.SDK_INT != 19 && Util.SDK_INT != 20) {
            return;
        }
        try {
            InputStream inputStream = connection.getInputStream();
            if (bytesRemaining == C.LENGTH_UNSET) {
                if (inputStream.read() == -1) {
                    return;
                }
            } else if (bytesRemaining <= MAX_BYTES_TO_DRAIN) {
                return;
            }
            String className = inputStream.getClass().getName();
            if (className.equals("com.android.okhttp.internal.http.HttpTransport$ChunkedInputStream")
                    || className.equals("com.android.okhttp.internal.http.HttpTransport$FixedLengthInputStream")) {
                Class<?> superclass = inputStream.getClass().getSuperclass();
                Method unexpectedEndOfInput = superclass.getDeclaredMethod("unexpectedEndOfInput");
                unexpectedEndOfInput.setAccessible(true);
                unexpectedEndOfInput.invoke(inputStream);
            }
        } catch (Exception ignored) {
        }
    }

    private void closeConnectionQuietly() {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Unexpected erro while disconnecting", e);
            }
            connection = null;
        }
    }
}
