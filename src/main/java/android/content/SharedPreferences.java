package android.content;

import java.util.Map;
import java.util.Set;

public interface SharedPreferences {

    boolean contains(String key);
    String getString(String key, String defaultValue);
    Map<String, ?> getAll();
    long getLong(String key, long defValue);
    int getInt(String key, int defValue);
    boolean getBoolean(String key, boolean defValue);
    Set<String> getStringSet(String key, Set<String> defValues);

    Editor edit();

    interface Editor {
        Editor putLong(String key, long value);
        Editor putString(String key, String value);
        Editor putInt(String key, int value);
        Editor putBoolean(String key, boolean value);
        Editor putStringSet(String key, Set<String> values);
        Editor remove(String key);
        Editor clear();
        boolean commit();
        void apply();
    }
}
