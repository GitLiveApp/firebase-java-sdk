/*
 * Shim for android.text.format.DateUtils with the elapsed-time formatting Remote Config
 * uses in its throttling messages: "MM:SS", or "H:MM:SS" when hours are present.
 */
package android.text.format;

public class DateUtils {

    public static String formatElapsedTime(long elapsedSeconds) {
        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
