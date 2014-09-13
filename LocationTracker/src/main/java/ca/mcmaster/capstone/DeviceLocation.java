package ca.mcmaster.capstone;

import android.location.Location;

/**
 * Created by andrew on 9/13/14.
 */
public class DeviceLocation {

    private final double latitude, longitude, gpsAltitude, barometerPressure, bearing, accuracy, speed;

    public DeviceLocation(final double latitude, final double longitude, final double gpsAltitude, final double barometerPressure,
                          final double bearing, final double accuracy, final double speed) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.gpsAltitude = gpsAltitude;
        this.barometerPressure = barometerPressure;
        this.bearing = bearing;
        this.accuracy = accuracy;
        this.speed = speed;
    }

    public DeviceLocation(final Location location, final double barometerPressure) {
        this(location.getLatitude(), location.getLongitude(), location.getAltitude(), barometerPressure,
                    location.getBearing(), location.getAccuracy(), location.getSpeed());
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getGpsAltitude() {
        return gpsAltitude;
    }

    public double getBarometerPressure() {
        return barometerPressure;
    }

    public double getBearing() {
        return bearing;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public double getSpeed() {
        return speed;
    }
}
