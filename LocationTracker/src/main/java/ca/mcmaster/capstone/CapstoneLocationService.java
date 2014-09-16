package ca.mcmaster.capstone;

import android.app.DownloadManager;
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
import android.view.View;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URL;

public final  class CapstoneLocationService extends Service {

    private LocationManager locationManager;
    private WifiManager wifiManager;
    private SensorManager sensorManager;
    private Sensor barometer;
    private LocationProvider gpsProvider;
    private double barometerPressure;

    private Location lastLocation;
    private final CapstoneLocationServiceBinder serviceBinder = new CapstoneLocationServiceBinder();
    private CapstoneSensorEventListener sensorEventListener;
    private CapstoneLocationListener locationListener;

    private final Gson gson = new Gson();
    private RequestQueue volleyRequestQueue;
    private CapstoneLocationServer locationServer;

    @Override
    public void onCreate() {
        log("Created");
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

        volleyRequestQueue = Volley.newRequestQueue(this);
        locationServer = new CapstoneLocationServer(this);
        try {
            locationServer.start();
        } catch (final IOException ioe) {
            Log.e("CapstoneService", "Error starting NanoHTTPD server", ioe);
        }

        Toast.makeText(this, "Capstone Location Service starting", Toast.LENGTH_LONG).show();
        log("Capstone Location Service starting");
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
        log("Capstone Location Service stopping");
    }

    public DeviceInfo getStatus() {
        final Location location;
        if (lastLocation != null) {
            location = lastLocation;
        } else {
            location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }
        final DeviceLocation deviceLocation = new DeviceLocation(location, barometerPressure);
        return new DeviceInfo(Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress()),
                                     locationServer.getListeningPort(), deviceLocation);
    }

    public String getStatusAsJson() {
        final DeviceInfo deviceInfo = getStatus();
        return gson.toJson(deviceInfo);
    }

    public void requestUpdate(final CapstoneLocationActivity capstoneLocationActivity, final String url) {
        final StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
            new Response.Listener<String>() {
                @Override
                public void onResponse(final String s) {
                    try {
                        final DeviceInfo deviceInfo = gson.fromJson(s, DeviceInfo.class);
                        capstoneLocationActivity.update(deviceInfo);
                    } catch (final JsonSyntaxException jse) {
                        Toast.makeText(CapstoneLocationService.this, "Error updating peer info", Toast.LENGTH_LONG).show();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(final VolleyError error) {
                    Toast.makeText(CapstoneLocationService.this, "Error updating peer info", Toast.LENGTH_LONG).show();
                }
            });
        volleyRequestQueue.add(stringRequest);
    }

    public class CapstoneLocationServiceBinder extends Binder {
        public CapstoneLocationService getService() {
            return CapstoneLocationService.this;
        }
    }

    private static void log(final String message) {
        Log.v("CapstoneService", message);
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
