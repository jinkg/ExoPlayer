package com.yalin.exoplayer.upstream;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class Loader implements LoaderErrorThrower {
    public static final class UnexpectedLoaderException extends IOException {
        public UnexpectedLoaderException(Exception cause) {
            super("Unexpected " + cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
        }
    }

    public interface Loadable {
        void cancelLoad();

        boolean isLoadCanceled();

        void load() throws IOException, InterruptedException;
    }

    public interface Callback<T extends Loadable> {
        void onLoadCompleted(T loadable, long elapsedRealtimeMs, long loadDurationMs);

        void onLoadCanceld(T loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released);

        int onLoadError(T loadable, long elapsedRealtimeMs, long loadDurationMs, IOException error);

    }

    @Override
    public void maybeThrowError() throws IOException {

    }

    @Override
    public void maybeThrowError(int minRetryCount) throws IOException {

    }
}
