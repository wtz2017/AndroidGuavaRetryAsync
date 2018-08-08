package com.example.wtz.androidguavaretryasync;

import android.util.Log;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.AttemptTimeLimiters;
import com.github.rholder.retry.BlockStrategies;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.github.rholder.retry.async.AsyncRetryer;
import com.google.common.base.Predicates;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.TimeUnit;

public class RetryStrategyManager {

    private final String TAG = "RetryStrategyManager";

    private volatile static RetryStrategyManager instance;

    private RetryStrategyManager() {
    }

    public static RetryStrategyManager getInstance() {
        if (instance == null) {
            synchronized (RetryStrategyManager.class) {
                if (instance == null)
                    instance = new RetryStrategyManager();
            }
        }
        return instance;
    }

    public Retryer<Boolean> getSyncRetryer(String tag) {
        return RetryerBuilder.<Boolean>newBuilder()
                // 抛出runtime异常、checked异常时都会重试，但是抛出error不会重试
                .retryIfException()
                // 结果返回false也需要重试
                .retryIfResult(Predicates.equalTo(false))
                // 设置重试之间等待延时策略
//                .withWaitStrategy(WaitStrategies.noWait())
                .withWaitStrategy(WaitStrategies.fixedWait(3, TimeUnit.SECONDS))
//                .withWaitStrategy(WaitStrategies.randomWait(5, TimeUnit.SECONDS))
//                .withWaitStrategy(WaitStrategies.randomWait(2, TimeUnit.SECONDS, 5, TimeUnit.SECONDS))
//                .withWaitStrategy(WaitStrategies.incrementingWait(3, TimeUnit.SECONDS, 2, TimeUnit.SECONDS))
//                // 30*1000*(斐波拉契数列：1, 1, 2, 3, 5, 8, 13, 21, 34, 55...),最大30分钟
//                .withWaitStrategy(WaitStrategies.fibonacciWait(30000, 30, TimeUnit.MINUTES))
//                .withWaitStrategy(WaitStrategies.exponentialWait(15000, 30, TimeUnit.MINUTES))
                // 设置延时实现的阻塞策略
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                // 设置每次重试超时时间
                .withAttemptTimeLimiter(AttemptTimeLimiters.<Boolean>fixedTimeLimit(3, TimeUnit.SECONDS))
                // 设置重试结束策略
//                .withStopStrategy(StopStrategies.neverStop())
                .withStopStrategy(StopStrategies.stopAfterAttempt(5))
                // 设置每次重试结果的监听
                .withRetryListener(new DefaultRetryListener<>(tag))
                .buildRetryer();
    }

    public AsyncRetryer<Boolean> getAsyncRetryer(String tag) {
        return RetryerBuilder.<Boolean>newBuilder()
                // 抛出runtime异常、checked异常时都会重试，但是抛出error不会重试
                .retryIfException()
                // 结果返回false也需要重试
                .retryIfResult(Predicates.equalTo(false))
                // 设置重试之间等待延时策略
//                .withWaitStrategy(WaitStrategies.noWait())
                .withWaitStrategy(WaitStrategies.fixedWait(3, TimeUnit.SECONDS))
//                .withWaitStrategy(WaitStrategies.randomWait(5, TimeUnit.SECONDS))
//                .withWaitStrategy(WaitStrategies.randomWait(2, TimeUnit.SECONDS, 5, TimeUnit.SECONDS))
//                .withWaitStrategy(WaitStrategies.incrementingWait(3, TimeUnit.SECONDS, 2, TimeUnit.SECONDS))
//                // 30*1000*(斐波拉契数列：1, 1, 2, 3, 5, 8, 13, 21, 34, 55...),最大30分钟
//                .withWaitStrategy(WaitStrategies.fibonacciWait(30000, 30, TimeUnit.MINUTES))
//                .withWaitStrategy(WaitStrategies.exponentialWait(15000, 30, TimeUnit.MINUTES))
                // 设置延时实现的阻塞策略
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                // 设置重试结束策略
//                .withStopStrategy(StopStrategies.neverStop())
                .withStopStrategy(StopStrategies.stopAfterAttempt(5))
                // 设置每次重试结果的监听
                .withRetryListener(new DefaultRetryListener<>(tag))
                .buildAsyncRetryer();
    }

    class DefaultRetryListener<Boolean> implements RetryListener {

        private String tag;

        public DefaultRetryListener(String tag) {
            this.tag = tag;
        }

        @Override
        public <Boolean> void onRetry(Attempt<Boolean> attempt) {
            StringBuilder sb = new StringBuilder(tag);
            // 第几次重试，第一次重试其实是第一次调用
            long num = attempt.getAttemptNumber();
            if (num == 1) {
                sb.append("[First invoke]");
            } else {
                sb.append("[Retry ");
                sb.append(num - 1);
                sb.append("]");
                // 距离第一次调用的延迟
                sb.append("DelaySinceFirst=");
                sb.append(attempt.getDelaySinceFirstAttempt() / 1000f);
                sb.append("s;");
            }

            // 重试结果: 是异常终止, 还是正常返回
            if (attempt.hasException()) {
                // 是什么原因导致异常
                sb.append("causeBy=");
                sb.append(getErroInfoFromException(attempt.getExceptionCause()));
            } else {
                // 正常返回时的结果
                sb.append("result=");
                sb.append(attempt.getResult());
            }

            Log.d(TAG, sb.toString());
        }

    }

    private String getErroInfoFromException(Throwable e) {
        if (e == null) {
            return "";
        }

        try {
            Writer stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);
            printWriter.close();
            return stringWriter.toString();
        } catch (Exception e2) {
            e2.printStackTrace();
        }

        return e.toString();
    }

}
