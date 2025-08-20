package android.os;


public final class SystemClock {

    private static final long startTime = System.currentTimeMillis();

    public static long elapsedRealtime() {
        return System.currentTimeMillis() - startTime;
    }
    public static long uptimeMillis() {
        return System.currentTimeMillis() - startTime;
    }
}
