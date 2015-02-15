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
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import ca.mcmaster.capstone.R;
import lombok.NonNull;

public class VisionActivity extends Activity implements VisionStatusListener {

    private static final String LOG_TAG = "VisionActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision);
        if (null == savedInstanceState) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, Camera2BasicFragment.newInstance(this))
                    .commit();
        }
    }

    @Override
    public void onCircleFound(@NonNull final Circle circle) {
        log("Found a circle");
        showToast("Found a circle at " + circle.getCentre() + " with radius " + circle.getRadius());
    }

    @Override
    public void onCircleLost(@NonNull final Circle circle) {
        log("Lost a circle");
        showToast("Lost a circle at " + circle.getCentre() + " with radius " + circle.getRadius());
    }

    private static void log(@NonNull final String message) {
        Log.v(LOG_TAG, message);
    }

    private void showToast(@NonNull final String message) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }
}
