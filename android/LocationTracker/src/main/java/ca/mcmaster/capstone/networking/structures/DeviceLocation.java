package ca.mcmaster.capstone.networking.structures;

import android.location.Location;

public class DeviceLocation {

    private final double latitude, longitude, altitude, barometerPressure, speed;
    private final float bearing, accuracy;

    public DeviceLocation(final Location location, final double barometerPressure) {
        final double latitude, longitude, altitude, speed;
        final float bearing, accuracy;
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

    public float getBearing() {
        return bearing;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public double getSpeed() {
        return speed;
    }
}
