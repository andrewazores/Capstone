package ca.mcmaster.capstone.networking;

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

import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Token;
import ca.mcmaster.capstone.networking.structures.DeviceInfo;
import ca.mcmaster.capstone.networking.structures.DeviceLocation;
import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;
import ca.mcmaster.capstone.networking.util.SensorUpdateCallbackReceiver;
import ca.mcmaster.capstone.networking.util.NsdUpdateCallbackReceiver;
import ca.mcmaster.capstone.networking.util.PeerUpdateCallbackReceiver;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final  class CapstoneService extends Service {

    private static final String NSD_LOCATION_SERVICE_NAME = "CapstoneLocationNSD";
    private static final String NSD_LOCATION_SERVICE_TYPE = "_http._tcp.";

    private InetAddress ipAddress;

    private LocationManager locationManager;
    private WifiManager wifiManager;
    private SensorManager sensorManager;
    private Sensor barometer, gravitySensor;
    private LocationProvider gpsProvider;
    private double barometerPressure;

    private Location lastLocation;
    private final CapstoneLocationServiceBinder serviceBinder = new CapstoneLocationServiceBinder();
    private BarometerEventListener barometerEventListener;
    private GravitySensorEventListener gravitySensorEventListener;
    private CapstoneLocationListener locationListener;

    private final Gson gson = new Gson();
    private RequestQueue volleyRequestQueue;
    private CapstoneServer locationServer;

    private volatile NsdManager.RegistrationListener nsdRegistrationListener;
    private volatile NsdManager.DiscoveryListener nsdDiscoveryListener;
    private volatile NsdManager nsdManager;

    private final Set<HashableNsdServiceInfo> nsdPeers =
            Collections.synchronizedSet(new HashSet<>());

    private final Set<SensorUpdateCallbackReceiver<DeviceInfo>> sensorUpdateCallbackReceivers =
            Collections.synchronizedSet(new HashSet<>());
    private final Set<PeerUpdateCallbackReceiver<NsdServiceInfo>> peerUpdateCallbackReceivers =
            Collections.synchronizedSet(new HashSet<>());
    private final Set<NsdUpdateCallbackReceiver> nsdUpdateCallbackReceivers =
            Collections.synchronizedSet(new HashSet<>());
    private final BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Token> tokenQueue = new LinkedBlockingQueue<>();
    private volatile boolean nsdBound;

    private final float[] gravity = new float[3];
    private final float[] linearAcceleration = new float[3];

    @Override
    public void onCreate() {
        logv("Created");
        System.setProperty("http.keepAlive", "false");

        ipAddress = findIpAddress();

        createPersistentNotification();
        setupLocationServices();
        setupBarometerService();
        setupGravitySensorService();
        setupLocalHttpClient();
        setupLocalHttpServer();
        setupNsdRegistration();

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
        try {
            nsdManager.registerService(getLocalNsdServiceInfo(), NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener);
        } catch (final IllegalArgumentException iae) {
            logd(iae.getLocalizedMessage());
        }
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
                    nsdManager.resolveService(nsdPeerInfo, new NsdResolveListener());
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
        try {
            nsdManager.discoverServices(getNsdServiceType(), NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener);
        } catch (final IllegalArgumentException iae) {
            logd(iae.getLocalizedMessage());
        }
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
        locationServer = new CapstoneServer(this);
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
        barometerEventListener = new BarometerEventListener();
        sensorManager.registerListener(barometerEventListener, barometer, SensorManager.SENSOR_DELAY_NORMAL);
        logv("Done");
    }

    private void setupGravitySensorService() {
        logv("Setting up gravity sensor service...");
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        gravitySensorEventListener = new GravitySensorEventListener();
        sensorManager.registerListener(gravitySensorEventListener, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
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
        final Intent notificationIntent = new Intent(this, CapstoneActivity.class);
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

    public void stopLocationService() {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Toast.makeText(getApplicationContext(), "Capstone Location Service stopping", Toast.LENGTH_LONG).show();
        locationManager.removeUpdates(locationListener);
        sensorManager.unregisterListener(barometerEventListener);
        sensorManager.unregisterListener(gravitySensorEventListener);
        sensorUpdateCallbackReceivers.clear();
        peerUpdateCallbackReceivers.clear();
        nsdUpdateCallbackReceivers.clear();
        nsdTeardown();
        locationServer.stop();
        stopLocationService();
        logv("Capstone Location Service stopping");
    }

    DeviceInfo getStatus() {
        final DeviceLocation deviceLocation = new DeviceLocation(lastLocation, barometerPressure, gravity, linearAcceleration);
        return new DeviceInfo(getLocalNsdServiceInfo().getHost().getHostAddress(),
                                     locationServer.getListeningPort(), deviceLocation);
    }

    String getStatusAsJson() {
        final DeviceInfo deviceInfo = getStatus();
        return gson.toJson(deviceInfo);
    }

    void registerSensorUpdateCallback(final CapstoneActivity capstoneActivity) {
        this.sensorUpdateCallbackReceivers.add(capstoneActivity);
    }

    void unregisterSensorUpdateCallback(final CapstoneActivity capstoneActivity) {
        this.sensorUpdateCallbackReceivers.remove(capstoneActivity);
    }

    void registerNsdUpdateCallback(final CapstoneActivity capstoneActivity) {
        this.nsdUpdateCallbackReceivers.add(capstoneActivity);
    }

    void unregisterNsdUpdateCallback(final CapstoneActivity capstoneActivity) {
        this.nsdUpdateCallbackReceivers.remove(capstoneActivity);
    }

    void sendHandshakeToPeer(final HashableNsdServiceInfo nsdPeer) {
        final Set<HashableNsdServiceInfo> peersPlusSelf = new HashSet<>();
        peersPlusSelf.addAll(nsdPeers);
        peersPlusSelf.add(HashableNsdServiceInfo.get(getLocalNsdServiceInfo()));
        peersPlusSelf.remove(nsdPeer);

        for (final HashableNsdServiceInfo nsdServiceInfo : peersPlusSelf) {
            sendNsdInfoToPeer(nsdServiceInfo, nsdPeer);
        }
    }

    private void sendNsdInfoToPeer(final HashableNsdServiceInfo info, final HashableNsdServiceInfo destination) {
        if (destination == null || destination.getHost() == null) {
            return;
        }

        final String contentBody = gson.toJson(info);
        final String peerUrl = "http://" + destination.getHost().getHostAddress() + ":" + destination.getPort();
        try {
            final JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, peerUrl, new JSONObject(contentBody),
                   jsonObject -> {
                       final NsdServiceInfo nsdServiceInfo = gson.fromJson(jsonObject.toString(), NsdServiceInfo.class);
                       nsdDiscoveryListener.onServiceFound(nsdServiceInfo);
                   },
                   volleyError -> {
                       logv("Volley POST info error: " + volleyError);
                       nsdDiscoveryListener.onServiceLost(destination.getNsdServiceInfo());
                   }) {
                @Override
                public Map<String, String> getHeaders() {
                    final Map<String, String> headers = new HashMap<>();
                    headers.put(CapstoneServer.KEY_REQUEST_METHOD, CapstoneServer.RequestMethod.IDENTIFY.toString());
                    return headers;
                }
            };
            volleyRequestQueue.add(request);
        } catch (final JSONException jse) {
            Log.e("CapstoneService", "Could not POST request", jse);
        }
    }

    public void sendTokenToPeer(final Token token, final HashableNsdServiceInfo destination) {
        if (destination == null || destination.getHost() == null) {
            return;
        }

        final String contentBody = gson.toJson(token);
        final String peerUrl = "http://" + destination.getHost().getHostAddress() + ":" + destination.getPort();
        try {
            final JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, peerUrl, new JSONObject(contentBody),
                    jsonObject -> {},
                    volleyError -> {
                        logv("Volley POST info error: " + volleyError);
                    }) {
                @Override
                public Map<String, String> getHeaders() {
                    final Map<String, String> headers = new HashMap<>();
                    headers.put(CapstoneServer.KEY_REQUEST_METHOD, CapstoneServer.RequestMethod.SEND_TOKEN.toString());
                    return headers;
                }
            };
            volleyRequestQueue.add(request);
        } catch (final JSONException jse) {
            Log.e("CapstoneService", "Could not POST token send", jse);
        }
    }

    void receiveTokenInternal(final Token token) {
        this.tokenQueue.add(token);
    }

    public Token receiveToken() throws InterruptedException {
        return this.tokenQueue.take();
    }

    void receiveEventInternal(final Event event) {
        this.eventQueue.add(event);
    }
    
    public Event receiveEvent() throws InterruptedException {
        return this.eventQueue.take();
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
        new NsdResolveListener().onServiceResolved(peerNsdServiceInfo.getNsdServiceInfo());
        updateNsdCallbackListeners();
    }

    void requestUpdateFromPeer(final CapstoneActivity capstoneActivity, final HashableNsdServiceInfo nsdServiceInfo) {
        if (capstoneActivity == null || nsdServiceInfo == null || nsdServiceInfo.getHost() == null) {
            logd("Requested peer update from invalid NsdServiceInfo: " + nsdServiceInfo);
            return;
        }
        final String url = "http://" + nsdServiceInfo.getHost().getHostAddress() + ":" + nsdServiceInfo.getPort();
        final Request request = new JsonObjectRequest(Request.Method.GET, url, null,
             jsonObject -> {
                 try {
                     final DeviceInfo deviceInfo = gson.fromJson(jsonObject.toString(), DeviceInfo.class);
                     capstoneActivity.peerUpdate(deviceInfo);
                 } catch (final JsonSyntaxException jse) {
                     logv("Bad JSON syntax in peer response, got: " + jsonObject);
                 }
             },
             volleyError -> {
                 logv("Volley GET update error: " + volleyError);
                 nsdDiscoveryListener.onServiceLost(nsdServiceInfo.getNsdServiceInfo());
             }) {
            @Override
            public Map<String, String> getHeaders() {
                final Map<String, String> headers = new HashMap<>();
                headers.put(CapstoneServer.KEY_REQUEST_METHOD, CapstoneServer.RequestMethod.UPDATE.toString());
                return headers;
            }
        };
        volleyRequestQueue.add(request);
    }

    String getNsdServiceType() {
        return NSD_LOCATION_SERVICE_TYPE;
    }

    String getNsdServiceName() {
        return NSD_LOCATION_SERVICE_NAME;
    }

    private String getLocalNsdServiceName() {
        return getNsdServiceName() + "-" + Build.SERIAL;
    }

    private static InetAddress findIpAddress() {
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
            Log.e("CapstoneService", "Error when attempting to determine local IP", se);
        }
        return null;
    }

    private InetAddress getIpAddress() {
        if (ipAddress != null) {
            return ipAddress;
        }

        final InetAddress ipAddress = findIpAddress();
        this.ipAddress = ipAddress;
        return this.ipAddress;
    }

    private static void logv(final String message) {
        Log.v("CapstoneService", message);
    }

    private static void logd(final String message) {
        Log.d("CapstoneService", message);
    }

    public class CapstoneLocationServiceBinder extends Binder {
        private int clients;

        public CapstoneService getService() {
            return CapstoneService.this;
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
            for (final SensorUpdateCallbackReceiver<DeviceInfo> sensorUpdateCallbackReceiver : sensorUpdateCallbackReceivers) {
                sensorUpdateCallbackReceiver.update(getStatus());
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

    private class BarometerEventListener implements SensorEventListener {
        @Override
        public void onSensorChanged(final SensorEvent event) {
            barometerPressure = event.values[0];
            for (final SensorUpdateCallbackReceiver<DeviceInfo> sensorUpdateCallbackReceiver : sensorUpdateCallbackReceivers) {
                sensorUpdateCallbackReceiver.update(getStatus());
            }
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        }
    }

    private class GravitySensorEventListener implements SensorEventListener {

        public static final float ALPHA = 0.7f;

        @Override
        public void onSensorChanged(final SensorEvent event) {
            // Isolate the force of gravity with the low-pass filter.
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];

            // Remove the gravity contribution with the high-pass filter.
            linearAcceleration[0] = event.values[0] - gravity[0];
            linearAcceleration[1] = event.values[1] - gravity[1];
            linearAcceleration[2] = event.values[2] - gravity[2];
            for (final SensorUpdateCallbackReceiver<DeviceInfo> sensorUpdateCallbackReceiver : sensorUpdateCallbackReceivers) {
                sensorUpdateCallbackReceiver.update(getStatus());
            }
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        }
    }

    private class NsdResolveListener implements NsdManager.ResolveListener {
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
                sendHandshakeToPeer(hashableNsdServiceInfo);
            }
            updateNsdCallbackListeners();
        }
    }
}
