package ca.mcmaster.capstone.initializer;

import android.app.Service;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import com.google.gson.Gson;

import junit.framework.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

import ca.mcmaster.capstone.monitoralgorithm.Automaton;
import ca.mcmaster.capstone.monitoralgorithm.NetworkServiceConnection;
import ca.mcmaster.capstone.networking.CapstoneService;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import ca.mcmaster.capstone.networking.util.JsonUtil;
import ca.mcmaster.capstone.networking.util.NpiUpdateCallbackReceiver;
import lombok.Getter;
import lombok.NonNull;

public class Initializer extends Service {
    private static class NetworkInitializer implements Runnable, NpiUpdateCallbackReceiver {
        private final NetworkServiceConnection serviceConnection;
        private final int numPeers;
        private NetworkPeerIdentifier localPID;
        private final Map<String, NetworkPeerIdentifier> virtualIdentifiers = new HashMap<>();
        private final CountDownLatch initializationLatch = new CountDownLatch(1);
        private final CountDownLatch peerCountLatch = new CountDownLatch(1);
        private volatile boolean cancelled = false;

        public NetworkInitializer(final int numPeers, final NetworkServiceConnection serviceConnection) {
            Log.v("networkInitializer", "created");
            this.numPeers = numPeers;
            this.serviceConnection = serviceConnection;
            cancelled = false;
        }

        @Override
        public void npiUpdate(final Collection<NetworkPeerIdentifier> npiPeers) {
            if (npiPeers.size() == numPeers - 1) { // npiPeers set does not include local PID
                Log.v("networkInitializer", "has enough npi peers - unlatching");
                peerCountLatch.countDown();
                serviceConnection.getNetworkLayer().stopNpiDiscovery();
            }
        }

        private void waitForNetworkLayer() {
            Log.v("networkInitializer", "waitForNetworkLayer");
            while (serviceConnection.getNetworkLayer() == null && !cancelled) {
                try {
                    Log.v("networkInitializer", "waiting 1 second for network layer to appear...");
                    Thread.sleep(1000);
                } catch (final InterruptedException e) {
                    Log.d("initializer", "NetworkLayer connection is not established: " + e.getLocalizedMessage());
                }
            }
        }

        @Override
        public void run() {
            Log.v("networkInitializer", "running");
            waitForNetworkLayer();
            Log.v("networkInitializer", "got network layer");
            serviceConnection.getNetworkLayer().registerNpiUpdateCallback(this);
            if (serviceConnection.getNetworkLayer().getAllNetworkDevices().size() == numPeers) {
                peerCountLatch.countDown();
            }

            localPID = serviceConnection.getNetworkLayer().getLocalNetworkPeerIdentifier();
            Log.v("networkInitializer", "got localPID");
            Log.v("networkInitializer", "getting virtual identifiers");
            this.virtualIdentifiers.putAll(generateVirtualIdentifiers());
            Log.v("networkInitializer", "got virtual identifiers");

            for (final Map.Entry<String, NetworkPeerIdentifier> entry : virtualIdentifiers.entrySet()) {
                Log.v("initializer", entry.getKey() + " - " + entry.getValue());
                if (entry.getValue().equals(localPID)) {
                    Log.v("initializer", "I am " + entry.getKey()  + "!");
                }
            }
            Log.v("networkInitializer", "unlatching initialization latch");
            initializationLatch.countDown();
            Log.v("networkInitializer", "finished");
        }

        private void waitForLatch(final CountDownLatch latch) {
            Log.v("networkInitializer", "waiting for latch: " + latch);
            while (latch.getCount() > 0 && !cancelled) {
                try {
                    latch.await(500, TimeUnit.MILLISECONDS);
                } catch (final InterruptedException ie) {
                    // don't really care, just need to try again
                }
            }
            Log.v("networkInitializer", "stopped waiting for latch: " + latch);
        }

        private Map<String, NetworkPeerIdentifier> generateVirtualIdentifiers() {
            waitForLatch(peerCountLatch);

            final Map<String, NetworkPeerIdentifier> virtualIdentifiers = new HashMap<>();
            final List<NetworkPeerIdentifier> sortedIdentifiers = new ArrayList<>(serviceConnection.getNetworkLayer().getAllNetworkDevices());
            Collections.sort(sortedIdentifiers, (f, s) -> Integer.compare(f.hashCode(), s.hashCode()));
            for (final NetworkPeerIdentifier networkPeerIdentifier : sortedIdentifiers) {
                final String virtualIdentifier = "x" + (sortedIdentifiers.indexOf(networkPeerIdentifier) + 1);
                virtualIdentifiers.put(virtualIdentifier, networkPeerIdentifier);
            }
            return virtualIdentifiers;
        }

        public NetworkPeerIdentifier getLocalPID() {
            waitForLatch(initializationLatch);
            return localPID;
        }

        public Map<String, NetworkPeerIdentifier> getVirtualIdentifiers() {
            waitForLatch(initializationLatch);
            return virtualIdentifiers;
        }

        public void cancel() {
            Log.v("networkInitializer", "cancelling");
            cancelled = true;
            peerCountLatch.countDown();
            initializationLatch.countDown();
            serviceConnection.getNetworkLayer().unregisterNpiUpdateCallback(this);
        }
    }

    private static class AutomatonInitializer implements Runnable {
        private static final File automatonFile = new File(Environment.getExternalStorageDirectory(), "automaton");
        private final CountDownLatch latch = new CountDownLatch(1);
        private AutomatonFile automaton = null;

        @Override
        public void run() {
            Log.d("automatonInitializer", "Started");
            try {
                this.automaton = JsonUtil.fromJson(FileUtils.readFileToString(automatonFile), AutomatonFile.class);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            latch.countDown();
        }

        public AutomatonFile getAutomatonFile() {
            waitForLatch(latch);
            return automaton;
        }

        private void waitForLatch(final CountDownLatch latch) {
            Log.v("networkInitializer", "waiting for latch: " + latch);
            while (latch.getCount() > 0) {
                try {
                    latch.await(500, TimeUnit.MILLISECONDS);
                } catch (final InterruptedException ie) {
                    // don't really care, just need to try again
                }
            }
            Log.v("networkInitializer", "stopped waiting for latch: " + latch);
        }
    }

    private final NetworkServiceConnection networkServiceConnection = new NetworkServiceConnection();
    private Intent networkServiceIntent;
    private Future<?> networkInitJob = null;

    // FIXME: the magic number will be read in from the input file, but for now is hard coded
    private final NetworkInitializer network = new NetworkInitializer(2, networkServiceConnection);
    private final AutomatonInitializer automatonInit = new AutomatonInitializer();

    @Override
    public IBinder onBind(Intent intent) {
        return new InitializerBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v("initializer", "creating initializer");
        networkServiceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(networkServiceIntent, networkServiceConnection, BIND_AUTO_CREATE);

        networkInitJob = Executors.newSingleThreadExecutor().submit(network);
        Executors.newSingleThreadExecutor().submit(automatonInit);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v("initializer", "destroying initializer");
        getApplicationContext().unbindService(networkServiceConnection);
        network.cancel();
        if (networkInitJob != null) {
            networkInitJob.cancel(true);
        }
    }

    public void processAutomaton() {
        final AutomatonFile automatonFile = automatonInit.getAutomatonFile();
        Automaton.processAutomatonFile(automatonFile);
    }

    public NetworkPeerIdentifier getLocalPID() {
        return network.getLocalPID();
    }

    public Map<String, NetworkPeerIdentifier> getVirtualIdentifiers() {
        return network.getVirtualIdentifiers();
    }
}
