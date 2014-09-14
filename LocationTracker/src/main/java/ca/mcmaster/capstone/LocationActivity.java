package ca.mcmaster.capstone;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LocationActivity extends Activity {

    protected TextView timeTextView, ipTextView, latitudeTextView, longitudeTextView, altitudeTextView, jsonTextView;
    protected TableLayout tableLayout;
    protected LocationManager locationManager;
    protected WifiManager wifiManager;
    protected SensorManager sensorManager;
    protected LocationListener locationListener;
    protected Sensor barometer;
    protected double barometerPressure;
    protected BarometerListener barometerListener;
    protected String locationProviderName = LocationManager.GPS_PROVIDER;
    protected LocationProvider gpsProvider;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        timeTextView = new TextView(this);
        ipTextView = new TextView(this);
        latitudeTextView = new TextView(this);
        longitudeTextView = new TextView(this);
        altitudeTextView = new TextView(this);

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        gpsProvider = locationManager.getProvider(locationProviderName);

        locationListener = new LocationUpdateListener(this);
        locationManager.requestLocationUpdates(locationProviderName, 0, 0, locationListener);

        barometerListener = new BarometerListener(this);
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        tableLayout = (TableLayout) findViewById(R.id.tableLayout);
        jsonTextView = (TextView) findViewById(R.id.jsonTextView);

        addNewRowWithLabel("Last Updated", timeTextView);
        addNewRowWithLabel("Device IP", ipTextView);
        addNewRowWithLabel("Latitude", latitudeTextView);
        addNewRowWithLabel("Longitude", longitudeTextView);
        addNewRowWithLabel("Altitude", altitudeTextView);

        addStaticRow(gpsProvider.getName() +" Altitude-enabled?", Boolean.toString(gpsProvider.supportsAltitude()));
        addStaticRow("Barometer detected?", Boolean.toString(barometer != null));

        if (barometer != null) {
            sensorManager.registerListener(barometerListener, barometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void addStaticRow(final String ... strings) {
        final List<View> views = new ArrayList<>();
        for (final String string : strings) {
            final TextView textView = new TextView(this);
            textView.setText(string);
            views.add(textView);
        }
        addNewRow(views.toArray(new View[views.size()]));
    }

    private void addNewRowWithLabel(final String label, final View ... views) {
        final TableRow row = new TableRow(this);
        final TextView labelText = new TextView(this);
        labelText.setText(label);
        row.addView(labelText);
        for (final View view : views) {
            row.addView(view);
        }
        tableLayout.addView(row);
    }

    private void addNewRow(final View ... items) {
        final TableRow newRow = new TableRow(this);
        for (final View view : items) {
            newRow.addView(view);
        }
        tableLayout.addView(newRow);
    }

    @Override
    public void onPause() {
        super.onPause();
        locationManager.removeUpdates(locationListener);
        if (barometer != null) {
            sensorManager.unregisterListener(barometerListener);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        locationManager.requestLocationUpdates(locationProviderName, 0, 0, locationListener);
        if (barometer != null) {
            sensorManager.registerListener(barometerListener, barometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    void updateUi(final Location location) {
        if (location == null) {
            return;
        }
        final double latitude, longitude, altitude;
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
        final DeviceInfo info = new DeviceInfo(ip, new DeviceLocation(location, barometerPressure));
        jsonTextView.setText(gson.toJson(info));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }

}
