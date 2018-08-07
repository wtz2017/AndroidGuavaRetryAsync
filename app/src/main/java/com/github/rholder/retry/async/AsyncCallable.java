package com.github.rholder.retry.async;

public interface AsyncCallable<V> {

    void call(AsyncCallResult<V> result) throws Exception;

}
