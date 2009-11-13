package com.android.mms.util;

import java.util.Stack;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class TaskStack {
    private final ScheduledThreadPoolExecutor mAsyncLoader;
    private final Stack<Runnable> mThingsToLoad;

    public TaskStack() {
        mAsyncLoader = new ScheduledThreadPoolExecutor(1);
        mThingsToLoad = new Stack<Runnable>();
    }

    private final Runnable mPopStackRunnable = new Runnable() {
        public void run() {
            Runnable r = null;
            synchronized (mThingsToLoad) {
                if (!mThingsToLoad.empty()) {
                    r = mThingsToLoad.pop();
                }
            }
            if (r != null) {
                r.run();
            }
        }
    };

    public void push(Runnable r) {
        synchronized (mThingsToLoad) {
            mThingsToLoad.push(r);
        }
        mAsyncLoader.execute(mPopStackRunnable);
    }

    public void clear() {
        synchronized (mThingsToLoad) {
            mThingsToLoad.clear();
        }
    }
}
