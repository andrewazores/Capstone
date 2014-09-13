package ca.mcmaster.capstone;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

/**
* Created by andrew on 9/13/14.
*/
class LocationUpdateListener implements LocationListener {
    private LocationActivity locationActivity;

    public LocationUpdateListener(final LocationActivity locationActivity) {
        this.locationActivity = locationActivity;
    }

    @Override
    public void onLocationChanged(final Location newLocation) {
        // Called when a new location is found by the network location provider.
        locationActivity.updateUi(newLocation);
    }

    @Override
    public void onStatusChanged(final String provider, final int status, final Bundle extras) {}

    @Override
    public void onProviderEnabled(final String provider) {}

    @Override
    public void onProviderDisabled(final String provider) {}
}
