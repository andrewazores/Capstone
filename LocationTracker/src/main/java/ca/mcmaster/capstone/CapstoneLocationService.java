package ca.mcmaster.capstone;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.Gson;

public class CapstoneLocationService extends Service {

    protected LocationManager locationManager;
    protected WifiManager wifiManager;
    protected SensorManager sensorManager;
    protected Sensor barometer;
    protected LocationProvider gpsProvider;
    protected double barometerPressure;

    protected Location lastLocation;
    private final CapstoneLocationServiceBinder serviceBinder = new CapstoneLocationServiceBinder();
    private CapstoneSensorEventListener sensorEventListener;
    private CapstoneLocationListener locationListener;

    @Override
    public void onCreate() {
        Log.v("CapstoneService", "Created");
        final Notification notification = new Notification(0, "Capstone Location",
                                                            System.currentTimeMillis());
        final Intent notificationIntent = new Intent(this, CapstoneLocationActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(this, "Capstone Location Service",
                                               "GPS Tracking", pendingIntent);
        startForeground(100, notification);

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        gpsProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);

        locationListener = new CapstoneLocationListener();
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sensorEventListener = new CapstoneSensorEventListener();
        sensorManager.registerListener(sensorEventListener, barometer, SensorManager.SENSOR_DELAY_NORMAL);

        Toast.makeText(this, "Capstone Location Service starting", Toast.LENGTH_LONG).show();
        Log.v("CapstoneService", "Capstone Location Service starting");
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return serviceBinder;
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "Capstone Location Service stopping", Toast.LENGTH_LONG).show();
        locationManager.removeUpdates(locationListener);
        sensorManager.unregisterListener(sensorEventListener);
        Log.v("CapstoneService", "Capstone Location Service stopping");
    }

    public DeviceInfo getStatus() {
        final Location location;
        if (lastLocation != null) {
            location = lastLocation;
        } else {
            location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }
        final DeviceLocation deviceLocation = new DeviceLocation(location, barometerPressure);
        return new DeviceInfo(Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress()), deviceLocation);
    }

    public String getStatusAsJson() {
        final DeviceInfo deviceInfo = getStatus();
        final Gson gson = new Gson();
        return gson.toJson(deviceInfo);
    }

    public class CapstoneLocationServiceBinder extends Binder {
        public CapstoneLocationService getService() {
            return CapstoneLocationService.this;
        }
    }

    private class CapstoneLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(final Location location) {
            lastLocation = location;
        }

        @Override
        public void onStatusChanged(final String provider, final int status, final Bundle extras) {

        }

        @Override
        public void onProviderEnabled(final String provider) {

        }

        @Override
        public void onProviderDisabled(final String provider) {

        }
    }

    private class CapstoneSensorEventListener implements SensorEventListener {
        @Override
        public void onSensorChanged(final SensorEvent event) {
            barometerPressure = event.values[0];
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {

        }
    }
}
