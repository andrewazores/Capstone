package ca.mcmaster.capstone;

/**
 * Created by andrew on 9/13/14.
 */
public class DeviceInfo {

    private final String ip;
    private final double latitude, longitude, altitude, accuracy;

    public DeviceInfo(final String ip, final double latitude, final double longitude, final double altitude, final double accuracy) {
        this.ip = ip;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.accuracy = accuracy;
    }
}
