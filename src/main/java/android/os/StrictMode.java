package android.os;

public class StrictMode {

    public static void setThreadPolicy(ThreadPolicy policy) {

    }

    public static final class ThreadPolicy {
        public static final class Builder {
            public Builder detectAll() {
                return this;
            }
            public Builder detectNetwork() {
                return this;
            }
            public Builder detectResourceMismatches() {
                return this;
            }
            public Builder detectUnbufferedIo() {
                return this;
            }
            public Builder penaltyLog() {
                return this;
            }
            public ThreadPolicy build() {
                return new ThreadPolicy();
            }
        }
    }
}
