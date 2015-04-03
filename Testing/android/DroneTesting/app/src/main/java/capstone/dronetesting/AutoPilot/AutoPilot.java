package capstone.dronetesting.AutoPilot;

import android.content.Context;

import com.o3dr.android.client.Drone;
import com.o3dr.services.android.lib.coordinate.LatLong;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

import capstone.dronetesting.AutoPilot.proxy.mission.MissionProxy;

/**
 * Created by eholtrop on 3/19/15.
 */
public class AutoPilot {

    private Drone mDrone;
    private Queue<LatLong> mDestinations = new ArrayDeque<>();
    private Context context;

    public AutoPilot(Context c, Drone drone) {
        this.mDrone = drone;
        this.context = c;
    }

    public void loadMissionFromFile(String path) {
        final File file = new File(path);

        try (final BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.ready()) {
                int lat = 0, lng = 0;

                mDestinations.add(new LatLong(lat, lng));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void goToNextDest() {
        final MissionProxy proxy = new MissionProxy(context, mDrone);
        final LatLong nextDest = mDestinations.poll();

        proxy.addWaypoint(new LatLong(nextDest));

        proxy.notifyMissionUpdate();
    }
}
