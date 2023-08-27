package android.content;

public class ComponentName {

    public final String pkg;
    public final String cls;

    public ComponentName(String pkg, String cls) {
        this.pkg = pkg;
        this.cls = cls;
    }

    public ComponentName(Context context, Class cls) {
        this.pkg = null;
        this.cls = cls.getName();
    }
}
