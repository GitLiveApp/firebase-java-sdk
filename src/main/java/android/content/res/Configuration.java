/*
 * Shim for android.content.res.Configuration. Remote Config reads the locale from it
 * and sends it with each fetch request.
 */
package android.content.res;

import java.util.Locale;

public class Configuration {
    public Locale locale = Locale.getDefault();
}
