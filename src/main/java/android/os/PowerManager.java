package android.os;

public class PowerManager {

    public WakeLock newWakeLock(int flags, String name) {
        return new WakeLock();
    }

    public class WakeLock {

        public void setReferenceCounted(boolean a) {}

        public void acquire() {}

        public void release() {}
    }


}
