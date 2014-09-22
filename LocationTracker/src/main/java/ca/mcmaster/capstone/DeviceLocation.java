package ca.mcmaster.capstone;

import android.location.Location;

/**
 * Created by andrew on 9/13/14.
 */
public class DeviceLocation {

    private final double latitude, longitude, altitude, barometerPressure, bearing, accuracy, speed;

    public DeviceLocation(final Location location, final double barometerPressure) {
        final double latitude, longitude, altitude, bearing, accuracy, speed;
        if (location == null) {
            latitude = 0;
            longitude = 0;
            altitude = 0;
            bearing = 0;
            accuracy = 0;
            speed = 0;
        } else {
            latitude = location.getLatitude();
            longitude = location.getLongitude();
            altitude = location.getAltitude();
            bearing = location.getBearing();
            accuracy = location.getAccuracy();
            speed = location.getSpeed();
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.barometerPressure = barometerPressure;
        this.bearing = bearing;
        this.accuracy = accuracy;
        this.speed = speed;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAltitude() {
        return altitude;
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
