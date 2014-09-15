package ca.mcmaster.capstone;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CapstoneLocationActivity extends Activity {

    protected TextView jsonTextView;
    private CapstoneLocationService capstoneLocationService;
    private final LocationServiceConnection serviceConnection = new LocationServiceConnection();
    public static final Intent SERVICE_INTENT = new Intent("ca.mcmaster.capstone.CapstoneLocationService");

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v("CapstoneActivity", "Starting");
        setContentView(R.layout.activity_location);

        jsonTextView = (TextView) findViewById(R.id.jsonTextView);

        final Button refreshButton = (Button) findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                reconnect();
                updateUi();
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

    private void updateUi() {
        if (capstoneLocationService == null) {
            Toast.makeText(this, "Service connection not established", Toast.LENGTH_LONG).show();
            Log.v("CapstoneActivity", "Service connection not established");
            return;
        }
        jsonTextView.setText(capstoneLocationService.getStatusAsJson());
        Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show();
    }

    private class LocationServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CapstoneLocationActivity.this, "Service connected", Toast.LENGTH_LONG).show();
            Log.v("CapstoneActivity", "Service connected");

            if (capstoneLocationService == null) {
                capstoneLocationService = ((CapstoneLocationService.CapstoneLocationServiceBinder) service).getService();
                updateUi();
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(CapstoneLocationActivity.this, "Service disconnected", Toast.LENGTH_LONG).show();
            Log.v("CapstoneActivity", "Service disconnected");

            capstoneLocationService = null;
        }
    }
}
