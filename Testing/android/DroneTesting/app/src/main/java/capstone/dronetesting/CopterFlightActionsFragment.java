package capstone.dronetesting;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.o3dr.android.client.Drone;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeEventExtra;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.property.GuidedState;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.follow.FollowState;
import com.o3dr.services.android.lib.gcs.follow.FollowType;
import com.o3dr.services.android.lib.util.Utils;

/**
 * Provide functionality for flight action button specific to copters.
 */
public class CopterFlightActionsFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = CopterFlightActionsFragment.class.getSimpleName();


    public static final String ACTION_TOGGLE_DRONE_CONNECTION = Utils.PACKAGE_NAME
            + ".ACTION_TOGGLE_DRONE_CONNECTION";
    public static final String EXTRA_ESTABLISH_CONNECTION = "extra_establish_connection";


    private static final String ACTION_FLIGHT_ACTION_BUTTON = "Copter flight action button";
    private static final double TAKEOFF_ALTITUDE = 10.0;

    private static final IntentFilter eventFilter = new IntentFilter();
    static {
        eventFilter.addAction(AttributeEvent.STATE_ARMING);
        eventFilter.addAction(AttributeEvent.STATE_CONNECTED);
        eventFilter.addAction(AttributeEvent.STATE_DISCONNECTED);
        eventFilter.addAction(AttributeEvent.STATE_UPDATED);
        eventFilter.addAction(AttributeEvent.STATE_VEHICLE_MODE);
        eventFilter.addAction(AttributeEvent.FOLLOW_START);
        eventFilter.addAction(AttributeEvent.FOLLOW_STOP);
        eventFilter.addAction(AttributeEvent.FOLLOW_UPDATE);
        eventFilter.addAction(AttributeEvent.MISSION_DRONIE_CREATED);
    }

    private final BroadcastReceiver eventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case AttributeEvent.STATE_ARMING:
                case AttributeEvent.STATE_CONNECTED:
                case AttributeEvent.STATE_DISCONNECTED:
                case AttributeEvent.STATE_UPDATED:
                    setupButtonsByFlightState();
                    break;

                case AttributeEvent.STATE_VEHICLE_MODE:
                    updateFlightModeButtons();
                    break;
            }
        }
    };

    private View mDisconnectedButtons;
    private View mDisarmedButtons;
    private View mArmedButtons;
    private View mInFlightButtons;

    private Button homeBtn;
    private Button landBtn;
    private Button pauseBtn;
    private Button autoBtn;

    private Drone mDrone;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDrone = new Drone();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_copter_mission_control, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mDisconnectedButtons = view.findViewById(R.id.mc_disconnected_buttons);
        mDisarmedButtons = view.findViewById(R.id.mc_disarmed_buttons);
        mArmedButtons = view.findViewById(R.id.mc_armed_buttons);
        mInFlightButtons = view.findViewById(R.id.mc_in_flight_buttons);

        final Button connectBtn = (Button) view.findViewById(R.id.mc_connectBtn);
        connectBtn.setOnClickListener(this);

        homeBtn = (Button) view.findViewById(R.id.mc_homeBtn);
        homeBtn.setOnClickListener(this);

        final Button armBtn = (Button) view.findViewById(R.id.mc_armBtn);
        armBtn.setOnClickListener(this);

        final Button disarmBtn = (Button) view.findViewById(R.id.mc_disarmBtn);
        disarmBtn.setOnClickListener(this);

        landBtn = (Button) view.findViewById(R.id.mc_land);
        landBtn.setOnClickListener(this);

        final Button takeoffBtn = (Button) view.findViewById(R.id.mc_takeoff);
        takeoffBtn.setOnClickListener(this);

        pauseBtn = (Button) view.findViewById(R.id.mc_pause);
        pauseBtn.setOnClickListener(this);

        autoBtn = (Button) view.findViewById(R.id.mc_autoBtn);
        autoBtn.setOnClickListener(this);

        final Button takeoffInAuto = (Button) view.findViewById(R.id.mc_TakeoffInAutoBtn);
        takeoffInAuto.setOnClickListener(this);


        final Button dronieBtn = (Button) view.findViewById(R.id.mc_dronieBtn);
        dronieBtn.setOnClickListener(this);

        setupButtonsByFlightState();
        updateFlightModeButtons();
    }

//    @Override
//    public void onApiConnected() {
//        missionProxy = getMissionProxy();
//
//        setupButtonsByFlightState();
//        updateFlightModeButtons();
//
//        getBroadcastManager().registerReceiver(eventReceiver, eventFilter);
//    }
//
//    @Override
//    public void onApiDisconnected() {
//        getBroadcastManager().unregisterReceiver(eventReceiver);
//    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.mc_connectBtn:
                toggleDroneConnection();
                break;

            case R.id.mc_armBtn:
                getArmingConfirmation();
                break;

            case R.id.mc_disarmBtn:
                mDrone.arm(false);
                break;

            case R.id.mc_land:
                mDrone.changeVehicleMode(VehicleMode.COPTER_LAND);
                break;

            case R.id.mc_takeoff:
                mDrone.doGuidedTakeoff(TAKEOFF_ALTITUDE);
                break;

            case R.id.mc_homeBtn:
                mDrone.changeVehicleMode(VehicleMode.COPTER_RTL);
                break;

            case R.id.mc_pause: {
                final FollowState followState = mDrone.getAttribute(AttributeType.FOLLOW_STATE);
                if (followState.isEnabled()){
                    mDrone.disableFollowMe();
                }

                mDrone.pauseAtCurrentLocation();
                break;
            }

            case R.id.mc_autoBtn:
                mDrone.changeVehicleMode(VehicleMode.COPTER_AUTO);
                break;

            case R.id.mc_TakeoffInAutoBtn:
                getTakeOffInAutoConfirmation();
                break;

            case R.id.mc_follow:
                FollowState followState = mDrone.getAttribute(AttributeType.FOLLOW_STATE);
                if(followState != null) {
                    if (followState.isEnabled())
                        mDrone.disableFollowMe();
                    else
                        mDrone.enableFollowMe(FollowType.LEASH);
                }
                break;

            case R.id.mc_dronieBtn:
                break;
        }
    }

    private void getTakeOffInAutoConfirmation() {
        Drone drone = mDrone;
        drone.doGuidedTakeoff(TAKEOFF_ALTITUDE);
        drone.changeVehicleMode(VehicleMode.COPTER_AUTO);
    }

    private void getArmingConfirmation() {
        mDrone.arm(true);
    }

    private void updateFlightModeButtons() {
        resetFlightModeButtons();

        State droneState = mDrone.getAttribute(AttributeType.STATE);
        if(droneState == null)
            return;

        final VehicleMode flightMode = droneState.getVehicleMode();
        if(flightMode == null)
            return;

        switch (flightMode) {
            case COPTER_AUTO:
                autoBtn.setActivated(true);
                break;

            case COPTER_GUIDED:
                final Drone drone = mDrone;
                final GuidedState guidedState = drone.getAttribute(AttributeType.GUIDED_STATE);
                final FollowState followState = drone.getAttribute(AttributeType.FOLLOW_STATE);
                if (guidedState.isInitialized() && !followState.isEnabled()) {
                    pauseBtn.setActivated(true);
                }
                break;

            case COPTER_RTL:
                homeBtn.setActivated(true);
                break;

            case COPTER_LAND:
                landBtn.setActivated(true);
                break;
            default:
                break;
        }
    }

    private void resetFlightModeButtons() {
        homeBtn.setActivated(false);
        landBtn.setActivated(false);
        pauseBtn.setActivated(false);
        autoBtn.setActivated(false);
    }

    private void resetButtonsContainerVisibility() {
        mDisconnectedButtons.setVisibility(View.GONE);
        mDisarmedButtons.setVisibility(View.GONE);
        mArmedButtons.setVisibility(View.GONE);
        mInFlightButtons.setVisibility(View.GONE);
    }

    private void setupButtonsByFlightState() {
        final State droneState = mDrone.getAttribute(AttributeType.STATE);
        if (droneState != null && droneState.isConnected()) {
            if (droneState.isArmed()) {
                if (droneState.isFlying()) {
                    setupButtonsForFlying();
                } else {
                    setupButtonsForArmed();
                }
            } else {
                setupButtonsForDisarmed();
            }
        } else {
            setupButtonsForDisconnected();
        }
    }

    private void setupButtonsForDisconnected() {
        resetButtonsContainerVisibility();
        mDisconnectedButtons.setVisibility(View.VISIBLE);
    }

    private void setupButtonsForDisarmed() {
        resetButtonsContainerVisibility();
        mDisarmedButtons.setVisibility(View.VISIBLE);
    }

    private void setupButtonsForArmed() {
        resetButtonsContainerVisibility();
        mArmedButtons.setVisibility(View.VISIBLE);
    }

    private void setupButtonsForFlying() {
        resetButtonsContainerVisibility();
        mInFlightButtons.setVisibility(View.VISIBLE);
    }

    public void toggleDroneConnection() {
        final Drone drone = mDrone;
        if (drone != null && drone.isConnected())
            if (drone.isConnected())
                drone.disconnect();
        else
                getActivity().sendBroadcast(new Intent(ACTION_TOGGLE_DRONE_CONNECTION)
                        .putExtra(EXTRA_ESTABLISH_CONNECTION, true));
    }
}
