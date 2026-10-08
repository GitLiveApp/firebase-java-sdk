/*
 * No-op shim for android.net.TrafficStats. Firebase Installations tags its network thread
 * for Android's per-app traffic accounting, which has no JVM equivalent.
 */
package android.net;

public class TrafficStats {

    public static void setThreadStatsTag(int tag) {
    }

    public static void clearThreadStatsTag() {
    }
}
