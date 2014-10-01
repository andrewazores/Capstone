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
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

    private volatile NsdManager.RegistrationListener nsdRegistrationListener;
    private volatile NsdManager.DiscoveryListener nsdDiscoveryListener;
    private volatile NsdManager.ResolveListener nsdResolveListener;
    private volatile NsdManager nsdManager;

    private final Set<HashableNsdServiceInfo> nsdPeers =
            Collections.synchronizedSet(new HashSet<>());

    private final Set<LocalUpdateCallbackReceiver<DeviceInfo>> locationUpdateCallbackReceivers =
            Collections.synchronizedSet(new HashSet<>());
    private final Set<PeerUpdateCallbackReceiver<NsdServiceInfo>> peerUpdateCallbackReceivers =
            Collections.synchronizedSet(new HashSet<>());
    private final Set<NsdUpdateCallbackReceiver> nsdUpdateCallbackReceivers =
            Collections.synchronizedSet(new HashSet<>());
    private volatile boolean nsdBound;

    @Override
    public void onCreate() {
        logv("Created");
        System.setProperty("http.keepAlive", "false");

        createPersistentNotification();
        setupLocationServices();
        setupBarometerService();
        setupLocalHttpClient();
        setupLocalHttpServer();
        setupNsdRegistration();
        setupNsdResolution();

        // Sometimes getting the system NSD_SERVICE blocks for a few minutes or indefinitely... start these components
        // async so we can eventually get updates when ready while allowing the UI to populate/appear in the meantime,
        // even if blank
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... voids) {
                logv("Async launching NSD services");
                nsdManager = (NsdManager) getSystemService(NSD_SERVICE);
                setupNsdDiscovery();
                nsdRestart();
                logv("Done NSD async start");
                return null;
            }
        }.execute();


        Toast.makeText(getApplicationContext(), "Capstone Location Service starting", Toast.LENGTH_LONG).show();
        logv("Capstone Location Service starting");
    }

    private void setupNsdRegistration() {
        logv("Setting up NSD registration...");
        logv("Attempting to register NSD service: " + getLocalNsdServiceInfo());

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
//                assignedNsdServiceName = localNsdServiceInfo.getServiceName();
            }

            @Override
            public void onServiceUnregistered(final NsdServiceInfo nsdServiceInfo) {
                logv("Local NSD service unregistered: " + nsdServiceInfo);
//                assignedNsdServiceName = null;
            }
        };
        logv("Done");
    }

    private void nsdRegister() {
        nsdManager.registerService(getLocalNsdServiceInfo(), NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener);
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
            public void onServiceFound(final NsdServiceInfo nsdPeerInfo) {
                logv("NSD discovery found: " + nsdPeerInfo);

                if (nsdPeerInfo == null
                        || nsdPeerInfo.getServiceType() == null
                        || nsdPeerInfo.getServiceName() == null) {
                    logv("Found invalid nsdPeerInfo: " + nsdPeerInfo);
                    return;
                }

                if (!nsdPeerInfo.getServiceType().equals(getNsdServiceType())) {
                    logv("Unknown Service Type: " + nsdPeerInfo);
                } else if (nsdPeerInfo.getServiceName().contains(getLocalNsdServiceName())) {
                    logv("Same machine: " + nsdPeerInfo);
                } else if (nsdPeerInfo.getServiceName().contains(getNsdServiceName())){
                    logv("Attempting to resolve: " + nsdPeerInfo);
                    nsdManager.resolveService(nsdPeerInfo, nsdResolveListener);
                } else {
                    logv("Could not register NSD service: " + nsdPeerInfo);
                }
            }

            @Override
            public void onServiceLost(final NsdServiceInfo nsdServiceInfo) {
                logv("NSD Service lost: " + nsdServiceInfo);
                if (nsdServiceInfo.getHost() == null) {
                    return; // useless data, we don't store these anyway
                }
                nsdPeers.remove(HashableNsdServiceInfo.get(nsdServiceInfo));
                updateNsdCallbackListeners();
            }
        };
        logv("Done");
    }

    public NsdServiceInfo getLocalNsdServiceInfo() {
        final NsdServiceInfo nsdServiceInfo = new NsdServiceInfo();
        nsdServiceInfo.setServiceName(getLocalNsdServiceName());
        nsdServiceInfo.setServiceType(getNsdServiceType());
        nsdServiceInfo.setPort(locationServer.getListeningPort());
        nsdServiceInfo.setHost(getIpAddress());
        return nsdServiceInfo;
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

                if (nsdServiceInfo.getHost().getHostAddress().equals(getIpAddress().getHostAddress())
                        || nsdServiceInfo.getServiceName().contains(getLocalNsdServiceName())) {
                    logv("NSD resolve found localhost");
                    return;
                }

                logv("Validating NSD peer: " + nsdServiceInfo);
                final HashableNsdServiceInfo hashableNsdServiceInfo = HashableNsdServiceInfo.get(nsdServiceInfo);
                boolean newPeer = nsdPeers.add(hashableNsdServiceInfo);
                if (newPeer) {
                    identifySelfToPeer(hashableNsdServiceInfo);
                }
                updateNsdCallbackListeners();
            }
        };
        logv("Done");
    }

    private void updateNsdCallbackListeners() {
        logv("Updating NSD receivers with new peer list: " + nsdPeers);
        for (final NsdUpdateCallbackReceiver nsdUpdateCallbackReceiver : nsdUpdateCallbackReceivers) {
            nsdUpdateCallbackReceiver.nsdUpdate(nsdPeers);
        }
    }

    private void nsdRestart() {
        if (nsdBound) {
            return;
        }
        new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(final Void... voids) {
                while (nsdManager == null) {
                    try {
                        Thread.sleep(100);
                    } catch (final InterruptedException ie) {
                        // ignore
                    }
                }
                nsdRegister();
                nsdDiscover();
                nsdBound = true;
                return null;
            }
        }.execute();
    }

    private void setupLocalHttpClient() {
        logv("Setting up local HTTP client...");
        volleyRequestQueue = Volley.newRequestQueue(this);
        logv("Done");
    }

    private void setupLocalHttpServer() {
        logv("Setting up local HTTP server...");
        if (locationServer != null && locationServer.wasStarted()) {
            locationServer.stop();
        }
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
    public void onRebind(final Intent intent) {
        for (final NsdUpdateCallbackReceiver nsdUpdateCallbackReceiver : nsdUpdateCallbackReceivers) {
            nsdUpdateCallbackReceiver.nsdUpdate(nsdPeers);
        }
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        serviceBinder.decrement();
//        if (serviceBinder.getClients() == 0) {
//            nsdTeardown();
//        }
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
        Toast.makeText(getApplicationContext(), "Capstone Location Service stopping", Toast.LENGTH_LONG).show();
        locationManager.removeUpdates(locationListener);
        sensorManager.unregisterListener(sensorEventListener);
        nsdTeardown();
        locationServer.stop();
        logv("Capstone Location Service stopping");
    }

    public DeviceInfo getStatus() {
        final DeviceLocation deviceLocation = new DeviceLocation(lastLocation, barometerPressure);
        return new DeviceInfo(getLocalNsdServiceInfo().getHost().getHostAddress(),
                                     locationServer.getListeningPort(), deviceLocation);
    }

    public String getStatusAsJson() {
        final DeviceInfo deviceInfo = getStatus();
        return gson.toJson(deviceInfo);
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

    public void identifySelfToPeer(final HashableNsdServiceInfo nsdPeer) {
        if (nsdPeer == null || nsdPeer.getHost() == null) {
            return;
        }

        final String contentBody = gson.toJson(HashableNsdServiceInfo.get(getLocalNsdServiceInfo()));
        final String peerUrl = "http://" + nsdPeer.getHost().getHostAddress() + ":" + nsdPeer.getPort();
        try {
            final JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, peerUrl, new JSONObject(contentBody),
                   jsonObject -> {
                       final NsdServiceInfo nsdServiceInfo = gson.fromJson(jsonObject.toString(), NsdServiceInfo.class);
                       nsdDiscoveryListener.onServiceFound(nsdServiceInfo);
                   },
                   volleyError -> nsdDiscoveryListener.onServiceLost(nsdPeer.getNsdServiceInfo())) {
                @Override
                public Map<String, String> getHeaders() {
                    final Map<String, String> headers = new HashMap<>();
                    headers.put(CapstoneLocationServer.KEY_REQUEST_METHOD, CapstoneLocationServer.REQUEST_METHOD_IDENTIFY);
                    return headers;
                }
            };
            volleyRequestQueue.add(request);
        } catch (final JSONException jse) {
            Log.e("CapstoneLocationService", "Could not POST request", jse);
        }
    }

    void addSelfIdentifiedPeer(final HashableNsdServiceInfo peerNsdServiceInfo) {
        logv("Learned about new self-identified peer: " + peerNsdServiceInfo);
        if (peerNsdServiceInfo == null
                || peerNsdServiceInfo.getHost() == null
                || peerNsdServiceInfo.getPort() == 0
                || peerNsdServiceInfo.getServiceName() == null
                || peerNsdServiceInfo.getServiceType() == null) {
            logv("Invalid service info: " + peerNsdServiceInfo);
            return;
        }
        nsdPeers.add(peerNsdServiceInfo);
        updateNsdCallbackListeners();
    }

    public void requestUpdateFromPeer(final CapstoneLocationActivity capstoneLocationActivity, final HashableNsdServiceInfo nsdServiceInfo) {
        if (capstoneLocationActivity == null || nsdServiceInfo == null || nsdServiceInfo.getHost() == null) {
            logd("Requested peer update from invalid NsdServiceInfo: " + nsdServiceInfo);
            return;
        }
        final String url = "http://" + nsdServiceInfo.getHost().getHostAddress() + ":" + nsdServiceInfo.getPort();
        final Request request = new JsonObjectRequest(Request.Method.GET, url, null,
             jsonObject -> {
                 try {
                     final DeviceInfo deviceInfo = gson.fromJson(jsonObject.toString(), DeviceInfo.class);
                     capstoneLocationActivity.peerUpdate(deviceInfo);
                 } catch (final JsonSyntaxException jse) {
                     logv("Bad JSON syntax in peer response, got: " + jsonObject);
                 }
             },
             volleyError -> logv("Volley GET update error: " + volleyError)) {
            @Override
            public Map<String, String> getHeaders() {
                final Map<String, String> headers = new HashMap<>();
                headers.put(CapstoneLocationServer.KEY_REQUEST_METHOD, CapstoneLocationServer.REQUEST_METHOD_UPDATE);
                return headers;
            }
        };
        volleyRequestQueue.add(request);
    }

    public String getNsdServiceType() {
        return NSD_LOCATION_SERVICE_TYPE;
    }

    public String getNsdServiceName() {
        return NSD_LOCATION_SERVICE_NAME;
    }

    private String getLocalNsdServiceName() {
        return getNsdServiceName() + "-" + Build.SERIAL;
    }

    private static InetAddress getIpAddress() {
        try {
            InetAddress myAddr = null;

            for (final NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {

                for (final InetAddress ipAddress : Collections.list(networkInterface.getInetAddresses())) {
                    if (!ipAddress.isLoopbackAddress()) {
                        myAddr = ipAddress;
                        logv("Identified local network interface " + networkInterface + " with IP address " + ipAddress);
                    }
                }
            }
            return myAddr;
        } catch (final SocketException se) {
            Log.e("CapstoneLocationService", "Error when attempting to determine local IP", se);
        }
        return null;
    }

    private static void logv(final String message) {
        Log.v("CapstoneService", message);
    }

    private static void logd(final String message) {
        Log.d("CapstoneService", message);
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

    private class CapstoneLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(final Location location) {
            lastLocation = location;
            for (final LocalUpdateCallbackReceiver<DeviceInfo> localUpdateCallbackReceiver : locationUpdateCallbackReceivers) {
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
