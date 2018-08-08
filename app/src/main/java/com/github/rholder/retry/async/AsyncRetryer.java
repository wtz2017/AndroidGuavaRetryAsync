/*
 * Copyright 2012-2015 Ray Holder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.rholder.retry.async;


import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.BlockStrategies;
import com.github.rholder.retry.BlockStrategy;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategy;
import com.github.rholder.retry.WaitStrategy;
import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * A retryer, which executes a call, and retries it until it succeeds, or
 * a stop strategy decides to stop retrying. A wait strategy is used to sleep
 * between attempts. The strategy to decide if the call succeeds or not is
 * also configurable.
 * <p></p>
 * A retryer can also wrap the callable into a RetryerCallable, which can be submitted to an executor.
 * <p></p>
 * Retryer instances are better constructed with a {@link RetryerBuilder}. A retryer
 * is thread-safe, provided the arguments passed to its constructor are thread-safe.
 *
 * @param <V> the type of the call return value
 * @author JB
 * @author Jason Dunkelberger (dirkraft)
 */
public final class AsyncRetryer<V> {
    private final StopStrategy stopStrategy;
    private final WaitStrategy waitStrategy;
    private final BlockStrategy blockStrategy;
    private final Predicate<Attempt<V>> rejectionPredicate;
    private final Collection<RetryListener> listeners;
    private int attemptNumber;
    private long startTime;

    /**
     * Constructor
     *
     * @param stopStrategy       the strategy used to decide when the retryer must stop retrying
     * @param waitStrategy       the strategy used to decide how much time to sleep between attempts
     * @param rejectionPredicate the predicate used to decide if the attempt must be rejected
     *                           or not. If an attempt is rejected, the retryer will retry the call, unless the stop
     *                           strategy indicates otherwise or the thread is interrupted.
     */
    public AsyncRetryer(@Nonnull StopStrategy stopStrategy,
                        @Nonnull WaitStrategy waitStrategy,
                        @Nonnull Predicate<Attempt<V>> rejectionPredicate) {

        this(stopStrategy, waitStrategy, BlockStrategies.threadSleepStrategy(), rejectionPredicate);
    }

    /**
     * Constructor
     *
     * @param stopStrategy       the strategy used to decide when the retryer must stop retrying
     * @param waitStrategy       the strategy used to decide how much time to sleep between attempts
     * @param blockStrategy      the strategy used to decide how to block between retry attempts; eg, Thread#sleep(), latches, etc.
     * @param rejectionPredicate the predicate used to decide if the attempt must be rejected
     *                           or not. If an attempt is rejected, the retryer will retry the call, unless the stop
     *                           strategy indicates otherwise or the thread is interrupted.
     */
    public AsyncRetryer(@Nonnull StopStrategy stopStrategy,
                        @Nonnull WaitStrategy waitStrategy,
                        @Nonnull BlockStrategy blockStrategy,
                        @Nonnull Predicate<Attempt<V>> rejectionPredicate) {
        this(stopStrategy, waitStrategy, blockStrategy, rejectionPredicate, new ArrayList<RetryListener>());
    }

    /**
     * Constructor
     *
     * @param stopStrategy       the strategy used to decide when the retryer must stop retrying
     * @param waitStrategy       the strategy used to decide how much time to sleep between attempts
     * @param blockStrategy      the strategy used to decide how to block between retry attempts; eg, Thread#sleep(), latches, etc.
     * @param rejectionPredicate the predicate used to decide if the attempt must be rejected
     *                           or not. If an attempt is rejected, the retryer will retry the call, unless the stop
     *                           strategy indicates otherwise or the thread is interrupted.
     * @param listeners          collection of retry listeners
     */
    @Beta
    public AsyncRetryer(@Nonnull StopStrategy stopStrategy,
                        @Nonnull WaitStrategy waitStrategy,
                        @Nonnull BlockStrategy blockStrategy,
                        @Nonnull Predicate<Attempt<V>> rejectionPredicate,
                        @Nonnull Collection<RetryListener> listeners) {
        Preconditions.checkNotNull(stopStrategy, "stopStrategy may not be null");
        Preconditions.checkNotNull(waitStrategy, "waitStrategy may not be null");
        Preconditions.checkNotNull(blockStrategy, "blockStrategy may not be null");
        Preconditions.checkNotNull(rejectionPredicate, "rejectionPredicate may not be null");
        Preconditions.checkNotNull(listeners, "listeners may not null");

        this.stopStrategy = stopStrategy;
        this.waitStrategy = waitStrategy;
        this.blockStrategy = blockStrategy;
        this.rejectionPredicate = rejectionPredicate;
        this.listeners = listeners;
    }

    /**
     * Executes the given callable. If the rejection predicate
     * accepts the attempt, the stop strategy is used to decide if a new attempt
     * must be made. Then the wait strategy is used to decide how much time to sleep
     * and a new attempt is made.
     *
     * @param callable the asynchronous callable task to be executed
     * @param result the final retry result returned by the asynchronous callback
     *
     */
    public void call(AsyncCallable<V> callable, final AsyncCallResult<V> result) {
        startTime = System.nanoTime();
        callInternal(callable, result);
    }

    public void callInternal(final AsyncCallable<V> callable, final AsyncCallResult<V> ret) {
        attemptNumber++;

        AsyncCallResult<V> acr = new AsyncCallResult<V>() {

            @Override
            public void onResult(V result) {
                Attempt<V> attempt = new ResultAttempt<V>(result, attemptNumber, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
                handle(attempt);
            }

            @Override
            public void onException(Throwable t) {
                Attempt<V> attempt = new ExceptionAttempt<V>(t, attemptNumber, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
                handle(attempt);
            }

            private void handle(final Attempt<V> attempt) {
                Observable.create(new ObservableOnSubscribe<Boolean>() {
                    @Override
                    public void subscribe(ObservableEmitter<Boolean> emitter) throws Exception {
                        for (RetryListener listener : listeners) {
                            listener.onRetry(attempt);
                        }

                        if (!rejectionPredicate.<Boolean>apply(attempt)) {
                            // Do not meet retrial conditions
                            if (ret != null) {
                                try {
                                    ret.onResult(attempt.get());
                                } catch (ExecutionException e) {
                                    ret.onException(e);
                                }
                            }
                            emitter.onNext(false);
                            emitter.onComplete();
                            return;
                        }

                        if (stopStrategy.shouldStop(attempt)) {
                            // meet stop strategy
                            if (ret != null) {
                                ret.onException(new RetryException(attemptNumber, attempt));
                            }
                            emitter.onNext(false);
                            emitter.onComplete();
                            return;
                        }

                        // need retry
                        emitter.onNext(true);
                        emitter.onComplete();
                    }
                })
                .subscribeOn(AndroidSchedulers.mainThread()) // callback on main thread
                .observeOn(Schedulers.io())// block on io thread
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                    }

                    @Override
                    public void onNext(Boolean needRetry) {
                        if (!needRetry) {
                            return;
                        }

                        long sleepTime = waitStrategy.computeSleepTime(attempt);
                        try {
                            blockStrategy.block(sleepTime);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            if (ret != null) {
                                final Throwable t = new RetryException(attemptNumber, attempt);
                                Observable.create(new ObservableOnSubscribe<Void>() {
                                    @Override
                                    public void subscribe(ObservableEmitter<Void> e) throws Exception {
                                        ret.onException(t);
                                    }
                                })
                                .subscribeOn(AndroidSchedulers.mainThread()) // callback on main thread
                                .subscribe();
                            }
                            return;
                        }

                        callInternal(callable, ret);
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                    }
                });
            }

        };

        try {
            callable.call(acr);
        } catch (Throwable t) {
            acr.onException(t);
        }

    }

    @Immutable
    static final class ResultAttempt<R> implements Attempt<R> {
        private final R result;
        private final long attemptNumber;
        private final long delaySinceFirstAttempt;

        public ResultAttempt(R result, long attemptNumber, long delaySinceFirstAttempt) {
            this.result = result;
            this.attemptNumber = attemptNumber;
            this.delaySinceFirstAttempt = delaySinceFirstAttempt;
        }

        @Override
        public R get() throws ExecutionException {
            return result;
        }

        @Override
        public boolean hasResult() {
            return true;
        }

        @Override
        public boolean hasException() {
            return false;
        }

        @Override
        public R getResult() throws IllegalStateException {
            return result;
        }

        @Override
        public Throwable getExceptionCause() throws IllegalStateException {
            throw new IllegalStateException("The attempt resulted in a result, not in an exception");
        }

        @Override
        public long getAttemptNumber() {
            return attemptNumber;
        }

        @Override
        public long getDelaySinceFirstAttempt() {
            return delaySinceFirstAttempt;
        }
    }

    @Immutable
    static final class ExceptionAttempt<R> implements Attempt<R> {
        private final ExecutionException e;
        private final long attemptNumber;
        private final long delaySinceFirstAttempt;

        public ExceptionAttempt(Throwable cause, long attemptNumber, long delaySinceFirstAttempt) {
            this.e = new ExecutionException(cause);
            this.attemptNumber = attemptNumber;
            this.delaySinceFirstAttempt = delaySinceFirstAttempt;
        }

        @Override
        public R get() throws ExecutionException {
            throw e;
        }

        @Override
        public boolean hasResult() {
            return false;
        }

        @Override
        public boolean hasException() {
            return true;
        }

        @Override
        public R getResult() throws IllegalStateException {
            throw new IllegalStateException("The attempt resulted in an exception, not in a result");
        }

        @Override
        public Throwable getExceptionCause() throws IllegalStateException {
            return e.getCause();
        }

        @Override
        public long getAttemptNumber() {
            return attemptNumber;
        }

        @Override
        public long getDelaySinceFirstAttempt() {
            return delaySinceFirstAttempt;
        }
    }

}
