package com.yalin.exoplayer.upstream;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.TraceUtil;
import com.yalin.exoplayer.util.Util;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

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

        void onLoadCanceled(T loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released);

        int onLoadError(T loadable, long elapsedRealtimeMs, long loadDurationMs, IOException error);

    }

    public static final int RETRY = 0;
    public static final int RETRY_RESET_ERROR_COUNT = 1;
    public static final int DONT_RETRY = 2;
    public static final int DONT_RETRY_FATAL = 3;

    private static final int MSG_START = 0;
    private static final int MSG_CANCEL = 1;
    private static final int MSG_END_OF_SOURCE = 2;
    private static final int MSG_IO_EXCEPTION = 3;
    private static final int MSG_FATAL_ERROR = 4;

    private final ExecutorService downloadExecutorService;

    private LoadTask<? extends Loadable> currentTask;
    private IOException fatalError;

    public Loader(String threadName) {
        this.downloadExecutorService = Util.newSingleThreadExecutor(threadName);
    }

    public <T extends Loadable> long startLoading(T loadable, Callback<T> callback,
                                                  int defaultMinRetryCount) {
        Looper looper = Looper.myLooper();
        Assertions.checkState(looper != null);
        long startTimeMs = SystemClock.elapsedRealtime();
        new LoadTask<>(looper, loadable, callback, defaultMinRetryCount, startTimeMs).start(0);
        return startTimeMs;
    }

    public boolean isLoading() {
        return currentTask != null;
    }

    public void cancelLoading() {
        currentTask.cancel(false);
    }

    public void release() {
        release(null);
    }

    public void release(Runnable postLoadAction) {
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        if (postLoadAction != null) {
            downloadExecutorService.submit(postLoadAction);
        }
        downloadExecutorService.shutdown();
    }

    @Override
    public void maybeThrowError() throws IOException {
        maybeThrowError(Integer.MIN_VALUE);
    }

    @Override
    public void maybeThrowError(int minRetryCount) throws IOException {
        if (fatalError != null) {
            throw fatalError;
        } else if (currentTask != null) {
            currentTask.maybeThrowError(minRetryCount == Integer.MIN_VALUE
                    ? currentTask.defaultMinRetryCount : minRetryCount);
        }
    }

    private final class LoadTask<T extends Loadable> extends Handler implements Runnable {

        private static final String TAG = "LoadTask";

        private final T loadable;
        private final Loader.Callback<T> callback;
        public final int defaultMinRetryCount;
        private final long startTimeMs;

        private IOException currentError;
        private int errorCount;

        private volatile Thread executorThread;
        private volatile boolean released;

        public LoadTask(Looper looper, T loadable, Loader.Callback<T> callback,
                        int defaultMinRetryCount, long startTimeMs) {
            super(looper);
            this.loadable = loadable;
            this.callback = callback;
            this.defaultMinRetryCount = defaultMinRetryCount;
            this.startTimeMs = startTimeMs;
        }

        public void maybeThrowError(int minRetryCount) throws IOException {
            if (currentError != null && errorCount > minRetryCount) {
                throw currentError;
            }
        }

        public void start(long delayMillis) {
            Assertions.checkState(currentTask == null);
            currentTask = this;
            if (delayMillis > 0) {
                sendEmptyMessageDelayed(MSG_START, delayMillis);
            } else {
                submitToExecutor();
            }
        }

        @Override
        public void run() {
            try {
                executorThread = Thread.currentThread();
                if (!loadable.isLoadCanceled()) {
                    TraceUtil.beginSection("load:" + loadable.getClass().getSimpleName());
                    try {
                        loadable.load();
                    } finally {
                        TraceUtil.endSection();
                    }
                }
                if (!released) {
                    sendEmptyMessage(MSG_END_OF_SOURCE);
                }
            } catch (IOException e) {
                if (!released) {
                    obtainMessage(MSG_IO_EXCEPTION, e).sendToTarget();
                }
            } catch (InterruptedException e) {
                Assertions.checkState(loadable.isLoadCanceled());
                if (!released) {
                    sendEmptyMessage(MSG_END_OF_SOURCE);
                }
            } catch (Exception e) {
                Log.e(TAG, "Unexpected exception loading stream", e);
                if (!released) {
                    obtainMessage(MSG_IO_EXCEPTION, new UnexpectedLoaderException(e)).sendToTarget();
                }
            } catch (Error e) {
                Log.e(TAG, "Unexpected error loading stream", e);
                if (!released) {
                    obtainMessage(MSG_FATAL_ERROR, e).sendToTarget();
                }
                throw e;
            }
        }

        @Override
        public void handleMessage(Message msg) {
            if (released) {
                return;
            }
            if (msg.what == MSG_START) {
                submitToExecutor();
                return;
            }
            if (msg.what == MSG_FATAL_ERROR) {
                throw (Error) msg.obj;
            }
            finish();
            long nowMs = SystemClock.elapsedRealtime();
            long durationMs = nowMs - startTimeMs;
            if (loadable.isLoadCanceled()) {
                callback.onLoadCanceled(loadable, nowMs, durationMs, false);
                return;
            }
            switch (msg.what) {
                case MSG_CANCEL:
                    callback.onLoadCanceled(loadable, nowMs, durationMs, false);
                    break;
                case MSG_END_OF_SOURCE:
                    callback.onLoadCompleted(loadable, nowMs, durationMs);
                    break;
                case MSG_IO_EXCEPTION:
                    currentError = (IOException) msg.obj;
                    int retryAction = callback.onLoadError(loadable, nowMs, durationMs, currentError);
                    if (retryAction == DONT_RETRY_FATAL) {
                        fatalError = currentError;
                    } else if (retryAction != DONT_RETRY) {
                        errorCount = retryAction == RETRY_RESET_ERROR_COUNT ? 1 : errorCount + 1;
                        start(getRetryDelayMilis());
                    }
                    break;
            }
        }

        public void cancel(boolean released) {
            this.released = released;
            currentError = null;
            if (hasMessages(MSG_START)) {
                removeMessages(MSG_START);
                if (!released) {
                    sendEmptyMessage(MSG_CANCEL);
                }
            } else {
                loadable.cancelLoad();
                if (executorThread != null) {
                    executorThread.interrupt();
                }
            }
            if (released) {
                finish();
                long nowMs = SystemClock.elapsedRealtime();
                callback.onLoadCanceled(loadable, nowMs, nowMs - startTimeMs, true);
            }
        }

        private void submitToExecutor() {
            currentError = null;
            downloadExecutorService.submit(currentTask);
        }

        private void finish() {
            currentTask = null;
        }

        private long getRetryDelayMilis() {
            return Math.min((errorCount - 1) * 1000, 5000);
        }
    }
}
