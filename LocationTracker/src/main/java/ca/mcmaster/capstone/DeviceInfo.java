package ca.mcmaster.capstone;

/**
 * Created by andrew on 9/13/14.
 */
public class DeviceInfo {

    private final String ip;
    private final int port;
    private final DeviceLocation location;

    public DeviceInfo(final String ip, final int port, final DeviceLocation location) {
        this.ip = ip;
        this.port = port;
        this.location = location;
    }

    public DeviceLocation getLocation() {
        return location;
    }

    public String getIp() {
        return ip;
    }

}
