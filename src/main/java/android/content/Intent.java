package android.content;

public class Intent {
    private final String action;
    private String _package;

    public Intent(String action) {
        this.action = action;
    }

    public Intent setPackage(String p) {
        this._package = p;
        return this;
    }

    public String getAction() {
        return action;
    }
}
