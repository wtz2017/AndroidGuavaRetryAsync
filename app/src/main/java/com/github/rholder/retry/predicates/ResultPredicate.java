package com.github.rholder.retry.predicates;

import com.github.rholder.retry.Attempt;
import com.google.common.base.Predicate;


public class ResultPredicate<V> implements Predicate<Attempt<V>> {
    private Predicate<V> delegate;

    public ResultPredicate(Predicate<V> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean apply(Attempt<V> attempt) {
        if (!attempt.hasResult()) {
            return false;
        }
        V result = attempt.getResult();
        return delegate.apply(result);
    }

}
