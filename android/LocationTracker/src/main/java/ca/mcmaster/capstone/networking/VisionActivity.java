package ca.mcmaster.capstone.networking;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;

import lombok.NonNull;

import static java.util.Arrays.asList;

public class VisionActivity extends Activity {

    public static final String TAG = "VisionActivity";
    private CameraDevice cameraDevice;
    private CVSurface cvSurface;

    @Override
    public void onCreate(final Bundle bundle) {
        cvSurface = new CVSurface(getApplicationContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        final CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            final String[] cameraIDs = cameraManager.getCameraIdList();
            cameraManager.openCamera(cameraIDs[0], new CameraCallback(), null);
        } catch (final CameraAccessException cae) {
            Log.e(TAG, "Could not connect to camera!");
            Toast.makeText(getApplicationContext(), "Could not connect to camera!", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraDevice != null) {
            cameraDevice.close();
        }
    }

    private class CameraCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull final CameraDevice cameraDevice) {
            VisionActivity.this.cameraDevice = cameraDevice;
            try {
                cameraDevice.createCaptureSession(asList(cvSurface.getHolder().getSurface()), new CameraPreviewCallback(), null);
            } catch (final CameraAccessException cae) {
                Log.e(TAG, "Could not connect to camera!");
            }
        }

        @Override
        public void onDisconnected(@NonNull final CameraDevice cameraDevice) {
            VisionActivity.this.cameraDevice = null;
        }

        @Override
        public void onError(final CameraDevice cameraDevice, final int i) {

        }
    }

    private class CameraPreviewCallback extends CameraCaptureSession.StateCallback {
        @Override
        public void onConfigured(@NonNull final CameraCaptureSession cameraCaptureSession) {
            try {
                final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                builder.addTarget(cvSurface.getHolder().getSurface());
                final CaptureRequest request = builder.build();
                cameraCaptureSession.setRepeatingRequest(request, new CameraCaptureCallback(), null);
            } catch (final CameraAccessException cae) {
                Log.e(TAG, "Could not connect to camera!");
            }
        }

        @Override
        public void onConfigureFailed(@NonNull final CameraCaptureSession cameraCaptureSession) {

        }
    }

    private class CVSurface extends SurfaceView {
        public CVSurface(@NonNull final Context context) {
            super(context);
        }
    }

    private class CameraCaptureCallback extends CameraCaptureSession.CaptureCallback {
        @Override
        public void onCaptureCompleted(final CameraCaptureSession session, final CaptureRequest request, final TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    }
}
