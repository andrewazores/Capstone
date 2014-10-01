package ca.mcmaster.capstone;

import android.os.Build;

public class DeviceInfo {

    private final String serial = Build.SERIAL;
    private final String ip;
    private final int port;
    private final DeviceLocation location;

    public DeviceInfo(final String ip, final int port, final DeviceLocation location) {
        this.ip = ip;
        this.port = port;
        this.location = location;
    }

    public int getPort() {
        return port;
    }

    public DeviceLocation getLocation() {
        return location;
    }

    public String getIp() {
        return ip;
    }

    public String getSerial() {
        return serial;
    }

}
