package com.github.rholder.retry.predicates;

import com.github.rholder.retry.Attempt;
import com.google.common.base.Predicate;

public class ExceptionClassPredicate<V> implements Predicate<Attempt<V>> {
    private Class<? extends Throwable> exceptionClass;

    public ExceptionClassPredicate(Class<? extends Throwable> exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    @Override
    public boolean apply(Attempt<V> attempt) {
        if (!attempt.hasException()) {
            return false;
        }
        return exceptionClass.isAssignableFrom(attempt.getExceptionCause().getClass());
    }

}
