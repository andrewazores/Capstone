package ca.mcmaster.capstone;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import com.google.gson.Gson;

import java.security.PublicKey;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LocationActivity extends Activity {

    protected TextView timeTextView, ipTextView, latitudeTextView, longitudeTextView, altitudeTextView, jsonTextView;
    protected TableLayout tableLayout;
    protected LocationManager locationManager;
    protected WifiManager wifiManager;
    protected LocationListener locationListener;
    protected Button refreshButton;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        final TextView timeLabel = new TextView(this);
        timeLabel.setText("Last updated");
        timeTextView = new TextView(this);
        final TextView ipLabel = new TextView(this);
        ipLabel.setText("Device IP");
        ipTextView = new TextView(this);
        final TextView latitudeLabel = new TextView(this);
        latitudeLabel.setText("Latitude");
        latitudeTextView = new TextView(this);
        final TextView longitudeLabel = new TextView(this);
        longitudeLabel.setText("Longitude");
        longitudeTextView = new TextView(this);
        final TextView altitudeLabel = new TextView(this);
        altitudeLabel.setText("Altitude");
        altitudeTextView = new TextView(this);

        tableLayout = (TableLayout) findViewById(R.id.tableLayout);
        jsonTextView = (TextView) findViewById(R.id.jsonTextView);

        addNewRow(timeLabel, timeTextView);
        addNewRow(ipLabel, ipTextView);
        addNewRow(latitudeLabel, latitudeTextView);
        addNewRow(longitudeLabel, longitudeTextView);
        addNewRow(altitudeLabel, altitudeTextView);

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);

        locationListener = new TextViewLocationUpdateListener();
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);

        refreshButton = (Button) findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(new RefreshButtonClickListener());

        updateUi();
    }

    private void addNewRow(final View ... items) {
        final TableRow newRow = new TableRow(this);
        for (final View view : items) {
            newRow.addView(view);
        }
        tableLayout.addView(newRow);
    }

    private void updateUi() {
        updateUi(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
    }

    private void updateUi(final Location location) {
        final double latitude, longitude, altitude, accuracy;
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        altitude = location.getAltitude();

        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        timeTextView.setText(simpleDateFormat.format(new Date()));

        final String ip = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
        ipTextView.setText(ip);

        latitudeTextView.setText(Double.toString(latitude));
        longitudeTextView.setText(Double.toString(longitude));
        altitudeTextView.setText(Double.toString(altitude));

        final Gson gson = new Gson();
        final DeviceInfo info = new DeviceInfo(ip, new DeviceLocation(location));
        jsonTextView.setText(gson.toJson(info));
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

    protected class TextViewLocationUpdateListener implements LocationListener {
        @Override
        public void onLocationChanged(final Location newLocation) {
            // Called when a new location is found by the network location provider.
            updateUi(newLocation);
        }

        @Override
        public void onStatusChanged(final String provider, final int status, final Bundle extras) {}

        @Override
        public void onProviderEnabled(final String provider) {}

        @Override
        public void onProviderDisabled(final String provider) {}
    }

    protected class RefreshButtonClickListener implements View.OnClickListener {
        @Override
        public void onClick(final View v) {
            updateUi();
        }
    }
}
