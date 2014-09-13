package ca.mcmaster.capstone;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
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
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.Date;

public class LocationActivity extends Activity {

    protected TextView timeTextView, ipTextView, latitudeTextView, longitudeTextView, altitudeTextView, jsonTextView;
    protected TableLayout tableLayout;
    protected final LocationManager locationManager;
    protected final WifiManager wifiManager;
    protected final SensorManager sensorManager;
    protected final LocationListener locationListener;
    protected final Sensor barometer;
    protected Button refreshButton;
    protected double barometerPressure;
    protected final BarometerListener barometerListener;

    public LocationActivity() {
        timeTextView = new TextView(this);
        ipTextView = new TextView(this);
        latitudeTextView = new TextView(this);
        longitudeTextView = new TextView(this);
        altitudeTextView = new TextView(this);

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);

        locationListener = new LocationUpdateListener(this);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        barometerListener = new BarometerListener(this);
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        final TextView timeLabel = new TextView(this);
        timeLabel.setText("Last updated");
        final TextView ipLabel = new TextView(this);
        ipLabel.setText("Device IP");
        final TextView latitudeLabel = new TextView(this);
        latitudeLabel.setText("Latitude");
        final TextView longitudeLabel = new TextView(this);
        longitudeLabel.setText("Longitude");
        final TextView altitudeLabel = new TextView(this);
        altitudeLabel.setText("Altitude");

        tableLayout = (TableLayout) findViewById(R.id.tableLayout);
        jsonTextView = (TextView) findViewById(R.id.jsonTextView);

        addNewRow(timeLabel, timeTextView);
        addNewRow(ipLabel, ipTextView);
        addNewRow(latitudeLabel, latitudeTextView);
        addNewRow(longitudeLabel, longitudeTextView);
        addNewRow(altitudeLabel, altitudeTextView);

        sensorManager.registerListener(barometerListener, barometer, SensorManager.SENSOR_DELAY_NORMAL);

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

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(barometerListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        sensorManager.registerListener(barometerListener, barometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    void updateUi() {
        updateUi(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
    }

    void updateUi(final Location location) {
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

    protected class RefreshButtonClickListener implements View.OnClickListener {
        @Override
        public void onClick(final View v) {
            updateUi();
        }
    }

}
