package android.os;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A mapping from String keys to values, with the Android class's semantics for the primitive, String and Bundle values
 * the ported Firebase libraries use: a getter returns its default when the key is missing or the value has another
 * type, {@link #containsKey} answers rather than throws, and keys may be null.
 */
public class Bundle {

    private final Map<String, Object> data;

    public Bundle() {
        this.data = new LinkedHashMap<>();
    }

    /** A bundle holding a copy of {@code data}, so that it stays mutable whatever map it is created from. */
    public Bundle(Map<String, Object> data) {
        this.data = new LinkedHashMap<>(data);
    }

    public Bundle(Bundle bundle) {
        this.data = new LinkedHashMap<>(bundle.data);
    }

    public int size() {
        return data.size();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public void clear() {
        data.clear();
    }

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public void remove(String key) {
        data.remove(key);
    }

    public void putAll(Bundle bundle) {
        data.putAll(bundle.data);
    }

    public Set<String> keySet() {
        return data.keySet();
    }

    public void putBoolean(String key, boolean value) {
        data.put(key, value);
    }

    public void putInt(String key, int value) {
        data.put(key, value);
    }

    public void putLong(String key, long value) {
        data.put(key, value);
    }

    public void putFloat(String key, float value) {
        data.put(key, value);
    }

    public void putDouble(String key, double value) {
        data.put(key, value);
    }

    public void putString(String key, String value) {
        data.put(key, value);
    }

    public void putBundle(String key, Bundle value) {
        data.put(key, value);
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return get(key, Boolean.class, defaultValue);
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int defaultValue) {
        return get(key, Integer.class, defaultValue);
    }

    public long getLong(String key) {
        return getLong(key, 0L);
    }

    public long getLong(String key, long defaultValue) {
        return get(key, Long.class, defaultValue);
    }

    public float getFloat(String key) {
        return getFloat(key, 0.0f);
    }

    public float getFloat(String key, float defaultValue) {
        return get(key, Float.class, defaultValue);
    }

    public double getDouble(String key) {
        return getDouble(key, 0.0);
    }

    public double getDouble(String key, double defaultValue) {
        return get(key, Double.class, defaultValue);
    }

    public String getString(String key) {
        return get(key, String.class, null);
    }

    /** Returns {@code defaultValue} when the key is missing, the value is null or it is not a String. */
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value == null ? defaultValue : value;
    }

    public Bundle getBundle(String key) {
        return get(key, Bundle.class, null);
    }

    private <T> T get(String key, Class<T> type, T defaultValue) {
        Object value = data.get(key);
        return type.isInstance(value) ? type.cast(value) : defaultValue;
    }

    @Override
    public String toString() {
        return "Bundle[" + data + "]";
    }
}
