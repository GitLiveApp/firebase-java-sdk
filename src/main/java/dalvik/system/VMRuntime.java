package dalvik.system;

import com.android.internal.util.ArrayUtils;

import java.util.Arrays;

public class VMRuntime {
    public static VMRuntime getRuntime() {
        return new VMRuntime();
    }

    public <T> Object newUnpaddedArray(Class<T> clazz, int minLen) {
        if(clazz == int.class) return new int[minLen];
        return new Object[minLen];
    }
}
