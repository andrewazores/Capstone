package ca.mcmaster.capstone;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class LocationActivity extends Activity {

    TextView latitude;
    TextView longitude;
    LocationManager manager;
    String locationProvider;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        latitude = (TextView) findViewById(R.id.latitude);
        longitude = (TextView) findViewById(R.id.longitude);

        manager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        final LocationListener locationListener = new TextViewLocationUpdateListener();
        manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        locationProvider = LocationManager.GPS_PROVIDER;

        final Location location = manager.getLastKnownLocation(locationProvider);
        locationListener.onLocationChanged(location);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }

    private class TextViewLocationUpdateListener implements LocationListener {
        @Override
        public void onLocationChanged(final Location newLocation) {
            // Called when a new location is found by the network location provider.

            latitude.setText(Double.toString(newLocation.getLatitude()));
            longitude.setText(Double.toString(newLocation.getLongitude()));
        }

        @Override
        public void onStatusChanged(final String provider, final int status, final Bundle extras) {}

        @Override
        public void onProviderEnabled(final String provider) {}

        @Override
        public void onProviderDisabled(final String provider) {}
    }
}
