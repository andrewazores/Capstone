package ca.mcmaster.capstone;

import android.location.Location;

/**
 * Created by andrew on 9/13/14.
 */
public class DeviceLocation {

    private final double latitude, longitude, altitude, bearing, accuracy, speed;

    public DeviceLocation(final double latitude, final double longitude, final double altitude,
                          final double bearing, final double accuracy, final double speed) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.bearing = bearing;
        this.accuracy = accuracy;
        this.speed = speed;
    }

    public DeviceLocation(final Location location) {
        this(location.getLatitude(), location.getLongitude(), location.getAltitude(),
                    location.getBearing(), location.getAccuracy(), location.getSpeed());
    }
}
