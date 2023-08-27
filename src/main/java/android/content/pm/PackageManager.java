package android.content.pm;

import android.content.ComponentName;
import android.content.Intent;
import android.util.AndroidException;

import java.util.HashMap;
import java.util.Map;

public class PackageManager {

    public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
        Map<String, Object> data = new HashMap<>();
        data.put("com.google.app.id", "app.teamhub.TeamHub");
        data.put("packageName", "app.teamhub.TeamHub");
        data.put("com.google.android.gms.version", 12451000);
        data.put("firebase_data_collection_default_enabled", false);
        data.put("firebase_messaging_auto_init_enabled", false);
        return new ApplicationInfo(data);
    }

    public static class NameNotFoundException extends AndroidException {
        public NameNotFoundException() {
        }

        public NameNotFoundException(String name) {
            super(name);
        }
    }

    public ServiceInfo getServiceInfo(ComponentName component, int flags) throws NameNotFoundException {
        switch(component.cls) {
            case "com.google.firebase.components.ComponentDiscoveryService":
                Map<String, Object> data = new HashMap<>();
                data.put("com.google.firebase.components.ComponentRegistrar", Boolean.TRUE);
                data.put("com.google.firebase.components:com.google.firebase.database.DatabaseRegistrar", "com.google.firebase.components.ComponentRegistrar");
                data.put("com.google.firebase.components:com.google.firebase.firestore.FirestoreRegistrar", "com.google.firebase.components.ComponentRegistrar");
                data.put("com.google.firebase.components:com.google.firebase.auth.FirebaseAuthRegistrar", "com.google.firebase.components.ComponentRegistrar");
                data.put("com.google.firebase.components:com.google.firebase.functions.FunctionsRegistrar", "com.google.firebase.components.ComponentRegistrar");
                data.put("com.google.firebase.components:com.google.firebase.iid.Registrar", "com.google.firebase.components.ComponentRegistrar");
                return new ServiceInfo(data);
        }
        throw new IllegalArgumentException(component.cls);
    }

    public ResolveInfo resolveService(Intent intent, int flags) {
        switch (intent.getAction()) {
            case "com.google.firebase.MESSAGING_EVENT":
                return null; //new ResolveInfo();

        }
        throw new IllegalArgumentException(intent.getAction());
    }

    public PackageInfo getPackageInfo(String name, int flags) throws NameNotFoundException {
        throw new NameNotFoundException();
    }

    public String getInstallerPackageName(String var1) throws NameNotFoundException {
        return (String) getApplicationInfo("", 0).metaData.get("packageName");
    }

    public boolean hasSystemFeature(String name) {
        switch(name) {
            case "android.hardware.type.watch":
            case "android.hardware.type.iot":
            case "android.hardware.type.embedded":
            case "android.hardware.type.television":
            case "android.hardware.type.automotive":
                return false;
        }
        throw new IllegalArgumentException(name);

    }

}
