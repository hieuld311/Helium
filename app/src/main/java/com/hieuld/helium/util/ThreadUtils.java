package com.hieuld.helium.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Utility class for thread management and synchronization.
 */
public class ThreadUtils {

    /**
     * Executes a task on the main thread and blocks the calling thread
     * until the result is returned.
     *
     * @param handler  The Handler associated with the main thread.
     * @param callable The task to be executed.
     * @return The result of the task.
     */
    public static <T> T runOnMainThread(Handler handler, Callable<T> callable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        FutureTask<T> futureTask = new FutureTask<>(callable);
        handler.post(futureTask);
        try {
            return futureTask.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
