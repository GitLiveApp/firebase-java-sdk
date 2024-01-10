package android.os;

import java.io.File;
import java.io.IOException;

public class StatFs {

    File file;

    public StatFs(String path) {
        file = new File(path);
    }

    public int getBlockSize() {
        try {
            return (int)file.toPath().getFileSystem().getFileStores().iterator().next().getBlockSize();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
