package ca.mcmaster.capstone.networking;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Set;

import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Valuation;
import ca.mcmaster.capstone.monitoralgorithm.VectorClock;
import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;

public class CubeActivity extends Activity {

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private SensorEventListener mSensorEventListener;
    private int flatnessCounter = 0;
    private boolean isFlat = true;
    private double flat = 1.0;
    private HashableNsdServiceInfo NSD;

    private final float[] gravity = new float[3];
    private final float[] linearAcceleration = new float[3];
    private OpenGLRenderer renderer;
    private final LocationServiceConnection serviceConnection = new LocationServiceConnection();
    private Intent serviceIntent = null;
    private boolean back = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        serviceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        setupGravitySensorService();
        // Go fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        GLSurfaceView view = new GLSurfaceView(this);
        renderer = new OpenGLRenderer();
        view.setRenderer(renderer);
        setContentView(view);

        AsyncTask networkGet = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] params) {
                while(!back) {
                    try {
                        getEvent();
                    } catch (final InterruptedException ie) {
                        return null;
                    }
                }
                return null;
            }
        };
        networkGet.execute();
    }

    HashMap<HashableNsdServiceInfo, Double> deviceMap = new HashMap<>();
    public void getEvent() throws InterruptedException {
        if (serviceConnection.getService() == null) {
            return;
        }
        Event e = serviceConnection.getService().receiveEvent();

        deviceMap.put(e.getPid(), (Double)e.getVal().getValue("isFlat"));

        int i = 0;
        StringBuilder sb = new StringBuilder();
        for(Double d : deviceMap.values()){
            sb.append(" Device ").append(i).append(": ");
            sb.append(d);
            ++i;
        }

        runOnUiThread(() -> Toast.makeText(CubeActivity.this, sb.toString(), Toast.LENGTH_SHORT).show());
    }

    private void setupGravitySensorService() {
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mSensorEventListener = new GravitySensorEventListener();
        mSensorManager.registerListener(mSensorEventListener, mSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        back = true;
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
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

            final float[] z_axis = new float[] {0, 0, 1};

            float[] target_dir = normalise( gravity );
            float rot_angle = (float) Math.acos( dot_product(target_dir,z_axis) );

            if( Math.abs(rot_angle) > Double.MIN_VALUE ) {
                float[] rot_axis = normalise(cross_product(target_dir, z_axis));

                renderer.axis = rot_axis;
                renderer.angle = rot_angle;
            }

            boolean previouslyFlat = isFlat;

            isFlat = checkCondition(gravity);

            if (isFlat) {
                renderer.setColor(new float[] {
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f
                });
            } else {
                renderer.setColor(new float[] {
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f
                });
            }
            if (previouslyFlat != isFlat) {
                ++flatnessCounter;
                if (flat == 0.0) {
                    flat = 1.0;
                } else {
                    flat = 0.0;
                }
                sendEvent(flat);
            }
        }

        public void sendEvent(double value){
            if (serviceConnection.getService() == null) {
                return;
            }
            final Valuation<?> valuation = new Valuation<Double>(new HashMap() {{
                put("isFlat", value);
            }});
            Event e = new Event(flatnessCounter, NSD, Event.EventType.INTERNAL, valuation,
                    new VectorClock(new HashMap<HashableNsdServiceInfo, Integer>() {{
                        put(HashableNsdServiceInfo.get(serviceConnection.getService().getLocalNsdServiceInfo()), flatnessCounter);
                        for (HashableNsdServiceInfo peer : serviceConnection.getService().getNsdPeers()) {
                            put(peer, 0);
                        }
                    }}));
            Toast.makeText(CubeActivity.this, "Event has left the building", Toast.LENGTH_SHORT).show();
            serviceConnection.getService().sendEventToMonitor(e);
        }

        public boolean checkCondition(float[] gravity){
            float dot = dot_product(gravity, new float[] {0, 0, 1});

            return dot < .5;
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        }
    }

    private float[] normalise(float[] gravity) {
        float sum = 0;
        for (float f : gravity) {
            sum += f*f;
        }
        float magnitude = (float) Math.sqrt(sum);
        float[] result = new float[gravity.length];
        for (int i = 0; i < result.length; ++i) {
            result[i] = gravity[i] / magnitude;
        }
        return result;
    }

    public float[] cross_product(float[] v1, float[] v2){
        float[] cross =  new float[3];

        cross[0] = (v1[1] * v2[2]) - (v1[2] * v2[1]);
        cross[1] = (v1[0] * v2[2]) - (v1[2] * v2[0]);
        cross[2] = (v1[0] * v2[1]) - (v1[1] * v2[0]);

        return cross;
    }

    public float dot_product(float[] v1, float[] v2){
        float dot = 0;

        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vector dimensionality mismatch");
        }

        for (int i = 0; i < v1.length; ++i) {
            dot += v1[i] * v2[i];
        }

        return dot;
    }


    public class LocationServiceConnection implements ServiceConnection {

        public CapstoneService service;
        private CapstoneService.CapstoneNetworkServiceBinder binder;

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CubeActivity.this, "Service connected", Toast.LENGTH_LONG).show();
//            log("Service connected");

            this.binder = (CapstoneService.CapstoneNetworkServiceBinder) service;
            this.service = ((CapstoneService.CapstoneNetworkServiceBinder) service).getService();
//            this.service.registerSensorUpdateCallback(CubeActivity.this);
//            this.service.registerNsdUpdateCallback(CubeActivity.this);
            CubeActivity.this.NSD = HashableNsdServiceInfo.get(this.service.getLocalNsdServiceInfo());
//            updateSelfInfo();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(CubeActivity.this, "Service disconnected", Toast.LENGTH_LONG).show();
//            log("Service disconnected");
            this.service = null;
        }

        public boolean isBound() {
            return this.service != null && binder.getClients() > 0;
        }

        public CapstoneService getService() {
            return service;
        }
    }
}
