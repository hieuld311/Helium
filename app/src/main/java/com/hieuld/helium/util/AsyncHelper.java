package com.hieuld.helium.util;

import android.os.Handler;
import android.os.Process;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A helper class to run tasks in the background and return results to the main thread.
 */
public class AsyncHelper {
    private final Handler mHandler = new Handler();
    private final List<OnDone<?>> mStrongRefs = new ArrayList<>();
    private final AtomicBoolean mDestroyed = new AtomicBoolean(false);

    /** Interface for receiving the task result. */
    public interface OnDone<T> {
        void onTaskDone(T result);
    }

    /** Abstract class representing the background work to be done. */
    public static abstract class Task<T> {
        public abstract T run();
    }

    /** Starts a background task and provides a callback for the result. */
    public <T> void run(Task<T> task, final OnDone<T> onDone) {
        final OnDone<T> wrapper = new OnDone<T>() {
            @Override
            public void onTaskDone(T result) {
                mStrongRefs.remove(this);
                if (onDone != null) {
                    onDone.onTaskDone(result);
                }
            }
        };

        this.mStrongRefs.add(wrapper);
        new TaskRunner<>(task, new WeakReference<>(wrapper), this.mDestroyed, this.mHandler).start();
    }

    /** Cancels all pending callbacks and clears references. */
    public void release() {
        synchronized (this.mDestroyed) {
            if (this.mDestroyed.getAndSet(true)) {
                return;
            }
            this.mHandler.removeCallbacksAndMessages(null);
            this.mStrongRefs.clear();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        release();
    }

    private static class TaskRunner<T> extends Thread {
        private final AtomicBoolean mDestroyed;
        private final Handler mHandler;
        private final WeakReference<OnDone<T>> mHandlerRef;
        private final Task<T> mTask;

        public TaskRunner(Task<T> task, WeakReference<OnDone<T>> handlerRef, AtomicBoolean destroyed, Handler handler) {
            this.mTask = task;
            this.mHandlerRef = handlerRef;
            this.mDestroyed = destroyed;
            this.mHandler = handler;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            T result = this.mTask.run();

            synchronized (this.mDestroyed) {
                if (this.mDestroyed.get()) {
                    return;
                }
                OnDone<T> onDone = this.mHandlerRef.get();
                if (onDone != null) {
                    this.mHandler.post(new CallbackRunnable<>(onDone, result));
                }
            }
        }
    }

    private static class CallbackRunnable<T> implements Runnable {
        private final OnDone<T> mOnDone;
        private final T mResult;

        public CallbackRunnable(OnDone<T> onDone, T result) {
            this.mOnDone = onDone;
            this.mResult = result;
        }

        @Override
        public void run() {
            if (this.mOnDone != null) {
                this.mOnDone.onTaskDone(this.mResult);
            }
        }
    }
}
