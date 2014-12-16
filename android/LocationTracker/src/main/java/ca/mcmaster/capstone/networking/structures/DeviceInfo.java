package ca.mcmaster.capstone.networking.structures;

import android.os.Build;

import lombok.Getter;
import lombok.ToString;
import lombok.Value;

@Value @ToString(includeFieldNames = true)
public class DeviceInfo {
    @Getter private static final String serial = Build.SERIAL;
    String ip;
    int port;
    DeviceLocation location;
}
