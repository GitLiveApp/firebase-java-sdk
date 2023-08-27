package android.content.pm;

import android.os.Bundle;

import java.util.Map;

public class ServiceInfo {

    public ServiceInfo(Map<String, Object> data) {
        metaData = new Bundle(data);
    }

    public Bundle metaData;
}
