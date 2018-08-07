package com.example.wtz.androidguavaretryasync;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.github.rholder.retry.async.AsyncCallResult;

import java.text.SimpleDateFormat;
import java.util.Date;


import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends Activity {
    private final static String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button1 = findViewById(R.id.btn_1);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testGuavaSyncRetry();
            }
        });

        Button button2 = findViewById(R.id.btn_2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testGuavaAsyncRetry();
            }
        });
    }

    private void testGuavaSyncRetry() {
        final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss,SSS");
        Log.d(TAG, "Sync begin..." + df.format(new Date()) + ", invoke thread id:" + Thread.currentThread().getId());

        // 同步调用
        Observable.create(new ObservableOnSubscribe<Boolean>() {
            @Override
            public void subscribe(ObservableEmitter<Boolean> emitter) throws Exception {
                boolean ret;
                try {
                    ret = RetryStrategyManager.getInstance()
                            .getSyncRetryer("SyncTest").call(new RetryCallable());
                } catch (Exception e) {
                    ret = false;
                }
                emitter.onNext(ret);
                emitter.onComplete();
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Observer<Boolean>() {
            @Override
            public void onSubscribe(Disposable d) {
            }

            @Override
            public void onNext(Boolean ret) {
                Log.d(TAG, "Sync end..." + df.format(new Date()) + ", thread id:" + Thread.currentThread().getId() + ", ret=" + ret);
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onComplete() {
            }
        });
    }

    private void testGuavaAsyncRetry() {
        final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss,SSS");
        Log.d(TAG, "Async begin..." + df.format(new Date()) + ", invoke thread id:" + Thread.currentThread().getId());

        // 异步调用
        RetryStrategyManager.getInstance().getAsyncRetryer("AsyncTest").call(
            new RetryCallable(),
            new AsyncCallResult<Boolean>() {
                @Override
                public void onResult(Boolean result) {
                    Log.d(TAG, "Async end..." + df.format(new Date()) + ", thread id:" + Thread.currentThread().getId() + ", ret=" + result);
                }

                @Override
                public void onException(Throwable t) {
                    Log.d(TAG, "Async end..." + df.format(new Date()) + ", thread id:" + Thread.currentThread().getId() + ", exception=" + t.getMessage());
                }
            });
    }

}
