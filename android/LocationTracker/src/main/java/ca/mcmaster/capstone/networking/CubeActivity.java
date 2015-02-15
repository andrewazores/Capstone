package ca.mcmaster.capstone.networking;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;

import ca.mcmaster.capstone.R;
import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.networking.structures.Message;
import lombok.NonNull;

public class CubeActivity extends MonitorableProcess {

    public static final String LOG_TAG = "CubeActivity";

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private SensorEventListener mSensorEventListener;
    private boolean isFlat = true;
    private double flat = 1.0;

    private final float[] gravity = new float[3];
    private OpenGLRenderer renderer;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cube);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        setupGravitySensorService();

        LinearLayout gl = (LinearLayout) findViewById(R.id.gl_layout);

        final GLSurfaceView view = new GLSurfaceView(this);
        renderer = new OpenGLRenderer();
        view.setRenderer(renderer);
        gl.addView(view);
    }

    public void setLabelText(@NonNull final String satisfactionState) {
        runOnUiThread(() -> {
            final TextView globalText = (TextView) findViewById(R.id.cube_global_info);

            final StringBuilder text = new StringBuilder();
            text.append("Virtual ID: ").append(variableName).append("\n");
            text.append("localID: ").append(localPeerIdentifier.toString()).append("\n");
            text.append("Satisfaction: ").append(satisfactionState);

            globalText.setText(text.toString());
        });
    }

    private void setupGravitySensorService() {
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mSensorEventListener = new GravitySensorEventListener();
        mSensorManager.registerListener(mSensorEventListener, mSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onMonitorSatisfied() {
        Log.d("MonitorState", "Monitor is satisfied !!!");
        setLabelText("satisfied");
    }

    @Override
    public void onMonitorViolated() {
        Log.d("MonitorState", "Monitor is violated !!!");
        setLabelText("violated");
    }

    @Override
    public void onNetworkServiceConnection() {
    }

    @Override
    public void onNetworkServiceDisconnection() {
    }

    @Override
    public void onInitializerServiceConnection() {
        setLabelText("indeterminate");
    }

    @Override
    public void onInitializerServiceDisconnection() {
    }

    @Override
    public void onReceiveMessage(@NonNull final Message message) {
        sendEvent(flat, Event.EventType.RECEIVE);
    }

    @Override
    public final void broadcastHeartbeat() {
        super.broadcastHeartbeat();
        sendEvent(flat, Event.EventType.SEND);
    }

    private class GravitySensorEventListener implements SensorEventListener {

        public static final float ALPHA = 0.7f;

        @Override
        public void onSensorChanged(final SensorEvent event) {
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];

            final float[] z_axis = new float[] {0, 0, 1};

            float[] target_dir = normalize(gravity);
            float rot_angle = (float) Math.acos( dotProduct(target_dir, z_axis) );

            if (Math.abs(rot_angle) > Double.MIN_VALUE) {
                renderer.axis = normalize(crossProduct(target_dir, z_axis));
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
                if (flat == 0.0) {
                    flat = 1.0;
                } else {
                    flat = 0.0;
                }
                sendEvent(flat, Event.EventType.INTERNAL);
            }
        }

        public boolean checkCondition(float[] gravity) {
            float dot = dotProduct(gravity, new float[]{0, 0, 1});

            return dot < 0.5;
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {}
    }

    private static float[] normalize(float[] gravity) {
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

    public static float[] crossProduct(float[] v1, float[] v2){
        float[] cross =  new float[3];

        cross[0] = (v1[1] * v2[2]) - (v1[2] * v2[1]);
        cross[1] = (v1[0] * v2[2]) - (v1[2] * v2[0]);
        cross[2] = (v1[0] * v2[1]) - (v1[1] * v2[0]);

        return cross;
    }

    public static float dotProduct(float[] v1, float[] v2){
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vector dimensionality mismatch");
        }

        float dot = 0;
        for (int i = 0; i < v1.length; ++i) {
            dot += v1[i] * v2[i];
        }
        return dot;
    }

    @Override
    public String getLogTag() {
        return LOG_TAG;
    }

}
