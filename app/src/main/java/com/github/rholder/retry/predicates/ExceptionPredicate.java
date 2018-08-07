package com.github.rholder.retry.predicates;

import com.github.rholder.retry.Attempt;
import com.google.common.base.Predicate;


public class ExceptionPredicate<V> implements Predicate<Attempt<V>> {
    private Predicate<Throwable> delegate;

    public ExceptionPredicate(Predicate<Throwable> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean apply(Attempt<V> attempt) {
        if (!attempt.hasException()) {
            return false;
        }
        return delegate.apply(attempt.getExceptionCause());
    }

}
