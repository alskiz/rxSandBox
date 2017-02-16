package com.example.alsk.myapplication;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.observables.SyncOnSubscribe;
import rx.subjects.PublishSubject;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final String TAGS = "Socket";
    public static final String TAG2 = "SECOND";
    public static final String TAG1 = "FIRST";
    public static final String INJECTOR = "INJECTOR";

    private Integer socketState = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        test1();
    }

    private void test1() {

        final PublishSubject<Object> injector = PublishSubject.create();


        Observable<Integer> socketConnection =
                Observable.create(SyncOnSubscribe.<Integer>createStateless(
                        observer -> {
                            observer.onNext(socketState);
                            observer.onCompleted();
                            socketState++;
                        }))
                        .doOnUnsubscribe(() -> Log.e(TAGS, "Unsubscribed"))
                        .doOnSubscribe(() -> Log.e(TAGS, "Subscribed"))
                        .doOnCompleted(() -> Log.e(TAGS, "Complete"))
                        .doOnError(error -> Log.e(TAGS, "Error " + error.getMessage()))
                        .doOnNext(string -> Log.e(TAGS, "Next " + string))
                        .repeatWhen(notifications -> notifications.flatMap(notification -> {

                            if (injector.hasObservers()) {
                                Log.e(TAG, "returned injector EMPTY");
                                return Observable.empty();
                            } else {
                                Log.e(TAG, "returned injector = [" + injector + "]");
                                return injector
                                        .doOnNext(o -> Log.e(INJECTOR, "doOnNext" + " o = [" + o + "]"))
                                        .doOnError(e -> Log.e(INJECTOR, "doOnError" + " e = [" + e + "]"))
                                        .doOnUnsubscribe(() -> Log.e(INJECTOR, "Unsubscribed"))
                                        .doOnSubscribe(() -> Log.e(INJECTOR, "Subscribed"));
                            }
                        }))
                        .replay(1)
                        .refCount();

        Log.e(TAG2, "--- vvv ---");
        socketConnection.
                flatMap(socket -> Observable.range(1, 3).map(state -> " state = [" + state + "]" + " socket = [" + socket + "]"))
                .doOnUnsubscribe(() -> Log.e(TAG2, "Unsubscribed"))
                .doOnSubscribe(() -> Log.e(TAG2, "Subscribed"))
                .subscribe(new DebugSubscriber<>(TAG2));

        Log.e(TAG2, "--- ^^^ ---");

        Log.e(TAG1, "--- vvv ---");
        final int[] errorCounter = {0};
        socketConnection.
                flatMap(socket -> Observable.range(1, 3).flatMap(state -> {

                    String value = " state = [" + state + "]" + " socket = [" + socket + "]";

                    if (state == 3) {
                        Log.e(TAG1, "Generate error " + value);
                        return Observable.error(new RuntimeException("socket broken"));
                    }

                    return Observable.just(value);
                }))
                .doOnUnsubscribe(() -> Log.e(TAG1, "Unsubscribed"))
                .doOnSubscribe(() -> Log.e(TAG1, "Subscribed"))
                .doOnError(error -> Log.e(TAG1, "Error going through [" + error + "]"))
                .retryWhen(errors -> errors.flatMap(error -> {

                    errorCounter[0]++;
                    Log.e(TAG1, "error = [" + error + "]" + " errorCounter[0] = [" + errorCounter[0] + "]");
                    if (errorCounter[0] == 4) {
                        return Observable.<Long>error(new RuntimeException("Unrecoverable error"));
                    }

                    return Observable.just(errorCounter[0])
                            .delay(3000, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnNext(o -> {
                                Log.e(TAG1, "--------- Injected -----------" + " o = [" + o + "]");
                                injector.onNext(o);
                            });
                }))
                .subscribe(new DebugSubscriber<>(TAG1));
        Log.e(TAG1, "--- ^^^ ---");

    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static class DebugSubscriber<T> extends Subscriber<T> {

        final String TAG;

        public DebugSubscriber(String tag) {
            this.TAG = tag;
        }

        @Override
        public void onCompleted() {
            Log.e(TAG, "onCompleted() called");
        }

        @Override
        public void onError(Throwable e) {
            Log.e(TAG, "onError() [" + e + "]");
        }

        @Override
        public void onNext(T t) {
            Log.e(TAG, "onNext() [" + t + "]");
        }
    }
}
