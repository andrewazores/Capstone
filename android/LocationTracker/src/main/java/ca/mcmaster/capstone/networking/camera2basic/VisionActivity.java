/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ca.mcmaster.capstone.networking.camera2basic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

import ca.mcmaster.capstone.R;
import ca.mcmaster.capstone.initializer.Initializer;
import ca.mcmaster.capstone.initializer.InitializerBinder;
import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Valuation;
import ca.mcmaster.capstone.monitoralgorithm.VectorClock;
import ca.mcmaster.capstone.networking.CapstoneService;
import ca.mcmaster.capstone.networking.CubeActivity;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import ca.mcmaster.capstone.networking.util.MonitorSatisfactionStateListener;
import lombok.NonNull;

public class VisionActivity extends Activity implements VisionStatusListener, MonitorSatisfactionStateListener {

    private static final String LOG_TAG = "VisionActivity";

    private final NetworkServiceConnection networkServiceConnection = new NetworkServiceConnection();
    private final InitializerServiceConnection initializerServiceConnection = new InitializerServiceConnection();
    private NetworkPeerIdentifier localPeerIdentifier;
    private String variableName;
    private int eventCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision);
        if (null == savedInstanceState) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, Camera2BasicFragment.newInstance(this))
                    .commit();
        }

        final Intent serviceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(serviceIntent, networkServiceConnection, BIND_AUTO_CREATE);

        final Intent initializerServiceIntent = new Intent(this, Initializer.class);
        getApplicationContext().bindService(initializerServiceIntent, initializerServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getApplicationContext().unbindService(networkServiceConnection);
        getApplicationContext().unbindService(initializerServiceConnection);
    }

    @Override
    public void onCircleFound(@NonNull final Circle circle) {
        log("Found a circle");
        showToast("Found a circle at " + circle.getCentre() + " with radius " + circle.getRadius());
        sendEvent(1);
    }

    @Override
    public void onCircleLost(@NonNull final Circle circle) {
        log("Lost a circle");
        showToast("Lost a circle at " + circle.getCentre() + " with radius " + circle.getRadius());
        sendEvent(0);
    }

    private void sendEvent(final double value) {
        waitForNetworkLayer();
        final Valuation valuation = new Valuation(new HashMap<String, Double>() {{
            put(VisionActivity.this.variableName, value);
        }});
        ++eventCounter;
        final Event e = new Event(eventCounter, localPeerIdentifier, Event.EventType.INTERNAL, valuation,
                new VectorClock(new HashMap<NetworkPeerIdentifier, Integer>() {{
                    put(networkServiceConnection.getService().getLocalNetworkPeerIdentifier(), eventCounter);
                    for (final NetworkPeerIdentifier peer : networkServiceConnection.getService().getKnownPeers()) {
                        put(peer, 0);
                    }
                }}));
        if (eventCounter != 0) {
            showToast("Event has left the building");
            networkServiceConnection.getService().sendEventToMonitor(e);
        }
    }

    private static void log(@NonNull final String message) {
        Log.v(LOG_TAG, message);
    }

    private void showToast(@NonNull final String message) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onMonitorSatisfied() {
        log("Monitor is satisfied");
        showToast("Monitor is satisfied");
    }

    @Override
    public void onMonitorViolated() {
        log("Monitor is violated");
        showToast("Monitor is violated");
    }

    private void waitForNetworkLayer() {
        while (networkServiceConnection.getService() == null) {
            log("waitForNetworkLayer");
            try {
                log("waiting for network layer to appear...");
                networkServiceConnection.waitForService();
            } catch (final InterruptedException e) {
                log("NetworkLayer connection is not established: " + e.getLocalizedMessage());
            }
        }
    }

    public class NetworkServiceConnection implements ServiceConnection {

        private CapstoneService service;
        private final Object latch = new Object();

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            showToast("Service connected");

            this.service = ((CapstoneService.CapstoneNetworkServiceBinder) service).getService();
            this.service.registerMonitorStateListener(VisionActivity.this);
            latch.notifyAll();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            showToast("Service disconnected");
            this.service = null;
        }

        public CapstoneService getService() {
            return service;
        }

        public void waitForService() throws InterruptedException {
            if (service == null) {
                latch.wait();
            }
        }
    }

    public class InitializerServiceConnection implements ServiceConnection {
        private Initializer initializer;

        @Override
        public void onServiceConnected(final ComponentName componentName, final IBinder iBinder) {
            this.initializer = ((InitializerBinder) iBinder).getInitializer();

            VisionActivity.this.localPeerIdentifier = initializer.getLocalPID();
            //FIXME: this is for testing out simple test case. More work is needed for more complex variableGlobalText arrangements
            for (final Map.Entry<String, NetworkPeerIdentifier> virtualID : initializer.getVirtualIdentifiers().entrySet()) {
                if (virtualID.getValue().equals(localPeerIdentifier)) {
                    VisionActivity.this.variableName = virtualID.getKey();
                    break;
                }
            }
            log("I am: " + VisionActivity.this.variableName);
        }

        @Override
        public void onServiceDisconnected(final ComponentName componentName) {
            this.initializer = null;
        }

        public Initializer getInitializer() {
            return this.initializer;
        }
    }
}
