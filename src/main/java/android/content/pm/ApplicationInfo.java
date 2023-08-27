package android.content.pm;

import java.util.Collections;
import java.util.Map;

public class ApplicationInfo extends ServiceInfo {
    public ApplicationInfo() { super(Collections.emptyMap()); }
    public ApplicationInfo(Map<String, Object> data) {
        super(data);
    }

    public int minSdkVersion;
    public int targetSdkVersion;
}
