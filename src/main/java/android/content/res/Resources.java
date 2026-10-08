package android.content.res;

public class Resources {
    public Configuration getConfiguration() {
        return new Configuration();
    }

    public static class NotFoundException extends RuntimeException {
    }
}
