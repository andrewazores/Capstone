package ca.mcmaster.capstone;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.Gson;

public class CapstoneLocationActivity extends Activity implements UpdateCallbackReceiver<DeviceInfo> {

    protected TextView jsonTextView, peerTextView;
    protected EditText hostnameTextField;
    private CapstoneLocationService capstoneLocationService;
    private final Gson gson = new Gson();
    private final LocationServiceConnection serviceConnection = new LocationServiceConnection();
    public static final Intent SERVICE_INTENT = new Intent("ca.mcmaster.capstone.CapstoneLocationService");
    private DeviceInfo peerInfo;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log("Starting");
        setContentView(R.layout.activity_location);

        jsonTextView = (TextView) findViewById(R.id.jsonTextView);
        peerTextView = (TextView) findViewById(R.id.peerInfo);
        hostnameTextField = (EditText) findViewById(R.id.hostnameTextField);

        final Button refreshButton = (Button) findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                reconnect();
                updateSelfInfo();
            }
        });

        final Button pingButton = (Button) findViewById(R.id.pingButton);
        pingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (capstoneLocationService == null) {
                    Toast.makeText(CapstoneLocationActivity.this, "Service connection not established", Toast.LENGTH_SHORT).show();
                    return;
                }
                capstoneLocationService.requestUpdate(CapstoneLocationActivity.this, hostnameTextField.getText().toString());
                updateSelfInfo();
            }
        });

        final Button stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                stopLocationService();
            }
        });

        reconnect();
    }

    @Override
    public void update(final DeviceInfo deviceInfo) {
        this.peerInfo = deviceInfo;
        updatePeerInfo();
    }

    private void reconnect() {
        if (capstoneLocationService != null) {
            return;
        }
        final Intent startSticky = new Intent(this, CapstoneLocationService.class);
        startService(startSticky);
        bindService(SERVICE_INTENT, serviceConnection, BIND_AUTO_CREATE);
    }

    private void stopLocationService() {
        if (capstoneLocationService == null) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        stopService(new Intent(this, CapstoneLocationService.class));
        unbindService(serviceConnection);
        capstoneLocationService = null;
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        reconnect();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (capstoneLocationService != null) {
            unbindService(serviceConnection);
            capstoneLocationService = null;
        }
    }

    private void updateSelfInfo() {
        if (capstoneLocationService == null) {
            Toast.makeText(this, "Service connection not established", Toast.LENGTH_LONG).show();
            log("Service connection not established");
            return;
        }
        jsonTextView.setText(capstoneLocationService.getStatusAsJson());
        Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show();
    }

    private void updatePeerInfo() {
        if (capstoneLocationService == null) {
            Toast.makeText(this, "Service connection not established", Toast.LENGTH_LONG).show();
            log("Service connection not established");
            return;
        }
        peerTextView.setText(gson.toJson(peerInfo));
        Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show();
    }

    private static void log(final String message) {
        Log.v("CapstoneActivity", message);
    }

    private class LocationServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CapstoneLocationActivity.this, "Service connected", Toast.LENGTH_LONG).show();
            log("Service connected");

            if (capstoneLocationService == null) {
                capstoneLocationService = ((CapstoneLocationService.CapstoneLocationServiceBinder) service).getService();
                updateSelfInfo();
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(CapstoneLocationActivity.this, "Service disconnected", Toast.LENGTH_LONG).show();
            log("Service disconnected");

            capstoneLocationService = null;
        }
    }
}
