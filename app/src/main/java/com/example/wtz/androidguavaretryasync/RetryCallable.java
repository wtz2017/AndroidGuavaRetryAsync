package com.example.wtz.androidguavaretryasync;

import android.util.Log;

import com.github.rholder.retry.async.AsyncCallResult;
import com.github.rholder.retry.async.AsyncCallable;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;

import static java.lang.Thread.sleep;

public class RetryCallable implements Callable<Boolean>, AsyncCallable<Boolean> {
    private final static String TAG = "RetryCallable";
    private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss,SSS");
    private int times = 1;

    @Override
    public Boolean call() throws Exception {
        Log.d(TAG, "sync-call..." + df.format(new Date()) + ", invoke thread id:" + Thread.currentThread().getId());

        int thisTimes = times++;

        if (thisTimes == 1) {
            throw new NullPointerException();
        } else if (thisTimes == 2) {
            throw new IOException();
        } else if (thisTimes == 3) {
            throw new ArithmeticException();
        } else if (thisTimes == 4) {
            sleep(10000);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void call(AsyncCallResult<Boolean> result) throws Exception {
        Log.d(TAG, "async-call..." + df.format(new Date()) + ", invoke thread id:" + Thread.currentThread().getId());

        int thisTimes = times++;

        if (thisTimes == 1) {
            throw new NullPointerException();
        } else if (thisTimes == 2) {
            throw new IOException();
        } else if (thisTimes == 3) {
            throw new ArithmeticException();
        } else {
            result.onResult(true);
        }
    }
}
