package com.github.rholder.retry.async;

public interface AsyncCallResult<V> {

    void onResult(V result);

    void onException(Throwable t);

}
