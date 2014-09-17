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
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;
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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final  class CapstoneLocationService extends Service {

    private static final String NSD_LOCATION_SERVICE_NAME = "CapstoneLocationNSD";
    private static final String NSD_LOCATION_SERVICE_TYPE = "_http._tcp.";

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

    private NsdServiceInfo nsdServiceInfo;
    private NsdManager.RegistrationListener nsdRegistrationListener;
    private NsdManager.DiscoveryListener nsdDiscoveryListener;
    private NsdManager.ResolveListener nsdResolveListener;
    private String assignedNsdServiceName;
    private NsdManager nsdManager;
    private Set<NsdServiceInfo> nsdPeers = new HashSet<>();

    private final Set<LocalUpdateCallbackReceiver> locationUpdateCallbackReceivers = new HashSet<>();
    private final Set<PeerUpdateCallbackReceiver> peerUpdateCallbackReceivers = new HashSet<>();
    private final Set<NsdUpdateCallbackReceiver> nsdUpdateCallbackReceivers = new HashSet<>();
    private volatile boolean nsdBound;

    @Override
    public void onCreate() {
        logv("Created");

        createPersistentNotification();
        setupLocationServices();
        setupBarometerService();
        setupLocalHttpClient();
        setupLocalHttpServer();

        nsdManager = (NsdManager) getSystemService(NSD_SERVICE);
        setupNsdRegistration();
        setupNsdDiscovery();
        setupNsdResolution();

        Toast.makeText(this, "Capstone Location Service starting", Toast.LENGTH_LONG).show();
        logv("Capstone Location Service starting");
    }

    private void setupNsdRegistration() {
        logv("Setting up NSD registration...");
        nsdServiceInfo = new NsdServiceInfo();
        nsdServiceInfo.setServiceName(getNsdServiceName() + "-" + Build.SERIAL);
        nsdServiceInfo.setServiceType(getNsdServiceType());
        nsdServiceInfo.setPort(locationServer.getListeningPort());
        logv("Attempting to register NSD service: " + nsdServiceInfo);

        nsdRegistrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(final NsdServiceInfo nsdServiceInfo, final int errorCode) {
                logd("Failed to register service " + nsdServiceInfo + ". Error: " + errorCode);
            }

            @Override
            public void onUnregistrationFailed(final NsdServiceInfo nsdServiceInfo, final int errorCode) {
                logd("Failed to unregister service " + nsdServiceInfo + ". Error: " + errorCode);
            }

            @Override
            public void onServiceRegistered(final NsdServiceInfo nsdServiceInfo) {
                logv("NSD service registered: " + nsdServiceInfo);
                assignedNsdServiceName = nsdServiceInfo.getServiceName();
            }

            @Override
            public void onServiceUnregistered(final NsdServiceInfo nsdServiceInfo) {
                logv("Local NSD service unregistered: " + nsdServiceInfo);
                assignedNsdServiceName = null;
            }
        };

        nsdRegister();
        logv("Done");
    }

    private void nsdRegister() {
        nsdManager.registerService(nsdServiceInfo, NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener);
    }

    private void setupNsdDiscovery() {
        logv("Setting up NSD discovery...");
        nsdDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(final String regType, final int errorCode) {
                logd("NSD start discovery failed, error: " + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(final String regType, final int errorCode) {
                logd("NSD stop discovery failed, error: " + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onDiscoveryStarted(final String regType) {
                logv("Discovery started for: " + regType);
            }

            @Override
            public void onDiscoveryStopped(final String regType) {
                logv("Discovery stopped for: " + regType);
            }

            @Override
            public void onServiceFound(final NsdServiceInfo nsdServiceInfo) {
                logv("NSD discovery found: " + nsdServiceInfo);

                if (!nsdServiceInfo.getServiceType().equals(getNsdServiceType())) {
                    logv("Unknown Service Type: " + nsdServiceInfo);
                } else if (nsdServiceInfo.getServiceName().equals(assignedNsdServiceName)) {
                    logv("Same machine: " + nsdServiceInfo);
                } else if (nsdServiceInfo.getServiceName().contains(getNsdServiceName())){
                    nsdManager.resolveService(nsdServiceInfo, nsdResolveListener);
                } else {
                    logv("Could not register NSD service: " + nsdServiceInfo);
                }
            }

            @Override
            public void onServiceLost(final NsdServiceInfo nsdServiceInfo) {
                logv("NSD Service lost: " + nsdServiceInfo);
                nsdPeers.remove(nsdServiceInfo);
                for (final NsdUpdateCallbackReceiver nsdUpdateCallbackReceiver : nsdUpdateCallbackReceivers) {
                    nsdUpdateCallbackReceiver.nsdUpdate(nsdPeers);
                }
            }
        };
        nsdDiscover();
        logv("Done");
    }

    private void nsdDiscover() {
        nsdManager.discoverServices(getNsdServiceType(), NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener);
    }

    private void setupNsdResolution() {
        logv("Setting up NSD resolver...");
        nsdResolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(final NsdServiceInfo nsdServiceInfo, final int errorCode) {
                logd("NSD resolve failed for: " + nsdServiceInfo + ". Error: " + errorCode);
            }

            @Override
            public void onServiceResolved(final NsdServiceInfo nsdServiceInfo) {
                logd("NSD resolve succeeded for: " + nsdServiceInfo);

                InetAddress localhost;
                try {
                    localhost = InetAddress.getLocalHost();
                } catch (final UnknownHostException uhe) {
                    Log.e("CapstoneService", "Unknown Host Exception while attempting to resolve localhost", uhe);
                    return;
                }

                if (nsdServiceInfo.getHost().getHostAddress().equals(localhost.getHostAddress())
                        || nsdServiceInfo.getServiceName().equals(assignedNsdServiceName)) {
                    logv("NSD resolve found localhost");
                    return;
                }

                logv("Adding NSD peer: " + nsdServiceInfo);
                boolean knownPeer = false;
                for (final NsdServiceInfo nsdPeer : nsdPeers) {
                    if (nsdPeer.getServiceName().equals(nsdServiceInfo.getServiceName())) {
                        knownPeer = true;
                    }
                }
                if (!knownPeer) {
                    identifySelfToPeer(nsdServiceInfo);
                    nsdPeers.add(nsdServiceInfo);
                    for (final NsdUpdateCallbackReceiver nsdUpdateCallbackReceiver : nsdUpdateCallbackReceivers) {
                        nsdUpdateCallbackReceiver.nsdUpdate(nsdPeers);
                    }
                }
            }
        };
        logv("Done");
    }

    private void nsdRestart() {
        if (nsdBound) {
            return;
        }
        nsdRegister();
        nsdDiscover();
        nsdBound = true;
    }

    private void setupLocalHttpClient() {
        logv("Setting up local HTTP client...");
        volleyRequestQueue = Volley.newRequestQueue(this);
        logv("Done");
    }

    private void setupLocalHttpServer() {
        logv("Setting up local HTTP server...");
        locationServer = new CapstoneLocationServer(this);
        try {
            locationServer.start();
        } catch (final IOException ioe) {
            Log.e("CapstoneService", "Error starting NanoHTTPD server", ioe);
        }
        logv("Done");
    }

    private void setupBarometerService() {
        logv("Setting up barometer service...");
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sensorEventListener = new CapstoneSensorEventListener();
        sensorManager.registerListener(sensorEventListener, barometer, SensorManager.SENSOR_DELAY_NORMAL);
        logv("Done");
    }

    private void setupLocationServices() {
        logv("Setting up location services...");
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        gpsProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);

        locationListener = new CapstoneLocationListener();
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        logv("Done");
    }

    private void createPersistentNotification() {
        logv("Creating persistent notification...");
        final Notification notification = new Notification(0, "Capstone Location",
                                                                  System.currentTimeMillis());
        final Intent notificationIntent = new Intent(this, CapstoneLocationActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(this, "Capstone Location Service",
                                               "GPS Tracking", pendingIntent);
        startForeground(100, notification);
        logv("Done");
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(final Intent intent) {
        if (serviceBinder.getClients() == 0) {
            nsdRestart();
        }
        serviceBinder.increment();
        return serviceBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        serviceBinder.decrement();
        if (serviceBinder.getClients() == 0) {
            nsdTeardown();
        }
        return false;
    }

    private void nsdTeardown() {
        if (!nsdBound) {
            return;
        }
        nsdManager.unregisterService(nsdRegistrationListener);
        nsdManager.stopServiceDiscovery(nsdDiscoveryListener);
        nsdBound = false;
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "Capstone Location Service stopping", Toast.LENGTH_LONG).show();
        locationManager.removeUpdates(locationListener);
        sensorManager.unregisterListener(sensorEventListener);
        nsdTeardown();
        locationServer.stop();
        logv("Capstone Location Service stopping");
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

    public Collection<NsdServiceInfo> getKnownPeers() {
        return new HashSet<>(nsdPeers);
    }

    public void registerLocationUpdateCallback(final CapstoneLocationActivity capstoneLocationActivity) {
        this.locationUpdateCallbackReceivers.add(capstoneLocationActivity);
    }

    public void unregisterLocationUpdateCallback(final CapstoneLocationActivity capstoneLocationActivity) {
        this.locationUpdateCallbackReceivers.remove(capstoneLocationActivity);
    }

    public void registerNsdUpdateCallback(final CapstoneLocationActivity capstoneLocationActivity) {
        this.nsdUpdateCallbackReceivers.add(capstoneLocationActivity);
    }

    public void unregisterNsdUpdateCallback(final CapstoneLocationActivity capstoneLocationActivity) {
        this.nsdUpdateCallbackReceivers.remove(capstoneLocationActivity);
    }

    public void identifySelfToPeer(final NsdServiceInfo nsdPeer) {
        final Map<String, String> headers = new HashMap<>();
        headers.put("method", "identify");
        headers.put("nsd_service_type", nsdServiceInfo.getServiceType());
        headers.put("nsd_service_name", assignedNsdServiceName);
        headers.put("nsd_service_port", Integer.toString(nsdServiceInfo.getPort()));
        headers.put("nsd_service_host", gson.toJson(nsdServiceInfo.getHost()));
        final String peerUrl = "http://" + nsdPeer.getHost().getHostAddress() + ":" + nsdPeer.getPort();
        final GsonRequest<DeviceInfo> request = new GsonRequest<>(Request.Method.POST, peerUrl, DeviceInfo.class, headers,
            new Response.Listener<DeviceInfo>() {
                @Override
                public void onResponse(final DeviceInfo deviceInfo) {
                }
            }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(final VolleyError volleyError) {
                Toast.makeText(CapstoneLocationService.this,
                      "Could not identify self to peer " + nsdPeer + ", Error " + volleyError,
                          Toast.LENGTH_LONG).show();
                nsdDiscoveryListener.onServiceLost(nsdPeer);
            }
        });
        volleyRequestQueue.add(request);
    }

    void addSelfIdentifiedPeer(final NsdServiceInfo peerNsdServiceInfo) {
        if (nsdServiceInfo.getServiceName().equals(peerNsdServiceInfo.getServiceName())) {
            return;
        }
        nsdDiscoveryListener.onServiceFound(peerNsdServiceInfo);
    }

    public void requestUpdateFromPeer(final CapstoneLocationActivity capstoneLocationActivity, final String url) {
        logv("Requesting nsdUpdate from " + url);
        final Map<String, String> headers = new HashMap<>();
        headers.put("method", "request");
        final GsonRequest<DeviceInfo> request = new GsonRequest<>(Request.Method.GET, url, DeviceInfo.class, headers,
            new Response.Listener<DeviceInfo>() {
                @Override
                public void onResponse(final DeviceInfo deviceInfo) {
                    try {
                        capstoneLocationActivity.peerUpdate(deviceInfo);
                    } catch (final JsonSyntaxException jse) {
                        Toast.makeText(CapstoneLocationService.this, "Bad JSON syntax in peer response", Toast.LENGTH_LONG).show();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(final VolleyError error) {
                    Toast.makeText(CapstoneLocationService.this,
                          "Error " + error.networkResponse.statusCode + " while updating peer info",
                              Toast.LENGTH_LONG).show();
                }
            });
        volleyRequestQueue.add(request);
    }

    public String getNsdServiceType() {
        return NSD_LOCATION_SERVICE_TYPE;
    }

    public String getNsdServiceName() {
        return NSD_LOCATION_SERVICE_NAME;
    }

    public class CapstoneLocationServiceBinder extends Binder {
        private int clients;

        public CapstoneLocationService getService() {
            return CapstoneLocationService.this;
        }

        public void increment() {
            ++clients;
        }

        public void decrement() {
            --clients;
        }

        public int getClients() {
            return clients;
        }
    }

    private static void logv(final String message) {
        Log.v("CapstoneService", message);
    }

    private static void logd(final String message) {
        Log.d("CapstoneService", message);
    }

    private class CapstoneLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(final Location location) {
            lastLocation = location;
            for (final LocalUpdateCallbackReceiver localUpdateCallbackReceiver : locationUpdateCallbackReceivers) {
                localUpdateCallbackReceiver.update(getStatus());
            }
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
