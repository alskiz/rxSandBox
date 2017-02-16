package com.example.alsk.myapplication;

import android.os.Bundle;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.subjects.PublishSubject;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final String TAGS = "Socket";
    public static final String TAG2 = "SECOND";
    public static final String TAG1 = "FIRST";

    private Integer socketState = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        test1();
    }

    private void test2() {

        final int[] counter = {0};
        Observable.create(s -> {
            System.out.println("subscribing");
            counter[0]++;
            s.onError(new RuntimeException("always fails " + counter[0]));
        }).retryWhen(attempts -> {
            return attempts
                    .zipWith(Observable.range(1, 3), Pair::new)
                    .flatMap(pair -> {
                        Log.e(TAG, "flatMap " + pair.first + " " + pair.second);

                        int i = pair.second;
                        System.out.println("delay retry by " + i + " second(s)");
                        return Observable.timer(i, TimeUnit.SECONDS);
                    });
        }).toBlocking().forEach(System.out::println);
    }

    private void test1() {

        PublishSubject<Object> injector = PublishSubject.create();

        Observable<Integer> socketConnection =
                Observable.defer(() -> Observable.just(socketState))
                        .doOnUnsubscribe(() -> Log.e(TAGS, "Unsubscribed"))
                        .doOnSubscribe(() -> Log.e(TAGS, "Subscribed"))
                        .doOnCompleted(() -> Log.e(TAGS, "Complete"))
                        .doOnError(error -> Log.e(TAGS, "Error " + error.getMessage()))
                        .doOnNext(string -> Log.e(TAGS, "Next " + string))
                        .repeatWhen(notifications -> injector)
                        .replay(1)
                        .refCount();

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
                .retryWhen(errors -> {
                    return errors.flatMap(error -> {
                        Log.e(TAG1, "error = [" + error + "]");

                        errorCounter[0]++;
                        if (errorCounter[0] > 3) {
                            return Observable.<Long>error(new RuntimeException("Uncorrectable error"));
                        }

                        return Observable.just(0)
                                //.delay(3000, TimeUnit.MILLISECONDS)
                                .doOnNext(o -> {
                                    socketState++;
                                    Log.e(TAG1, "Injected");
                                    injector.onNext(o);
                                });
                    });
                })
                .subscribe(new DebugSubscriber<>(TAG1));

        socketConnection.
                flatMap(socket -> Observable.range(1, 3).map(state -> " state = [" + state + "]" + " socket = [" + socket + "]"))
                .doOnUnsubscribe(() -> Log.e(TAG2, "Unsubscribed"))
                .doOnSubscribe(() -> Log.e(TAG2, "Subscribed"))
                .subscribe(new DebugSubscriber<>(TAG2));
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
            Log.e(TAG, "onError() called with: e = [" + e + "]");
        }

        @Override
        public void onNext(T t) {
            Log.e(TAG, "onNext() called with: t = [" + t + "]");
        }
    }
}
