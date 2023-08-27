package android.os;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Bundle {

    private final Map<String, Object> data;

    public Bundle(Map<String, Object> data) {
        this.data = data;
    }

    public Bundle(Bundle bundle) {
        this.data = new HashMap<>(bundle.data);
    }

    public boolean containsKey(String key) {
        if(data.containsKey(key)) return true;
        throw new IllegalArgumentException(key);
    }

    public Set<String> keySet() {
        return data.keySet();
    }

    public boolean getBoolean(String key) {

        return (Boolean)data.get(key);
    }

    public Object get(String key) {
        containsKey(key);
        return data.get(key);
    }

    public String getString(String key) {
        containsKey(key);
        return (String)data.get(key);
    }

    public int getInt(String key) {
        return (Integer)data.get(key);
    }
}
