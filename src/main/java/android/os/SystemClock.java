package android.os;

import java.lang.management.ManagementFactory;

public final class SystemClock {
    public static long elapsedRealtime() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }
    public static long uptimeMillis() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }
}
