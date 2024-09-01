package android.os;

import javax.swing.*;

public class Looper {
    private static final Looper main = new Looper();

    public Thread getThread() {
        return Thread.currentThread();
    }

    public static Looper getMainLooper() {
        return main;
    }

    public static Looper myLooper() {
        return SwingUtilities.isEventDispatchThread() ? main : new Looper();
    }

    static void prepare() {

    }

    static void loop() {

    }
}
