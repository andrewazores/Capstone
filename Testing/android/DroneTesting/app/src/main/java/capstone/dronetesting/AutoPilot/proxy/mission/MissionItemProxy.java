package capstone.dronetesting.AutoPilot.proxy.mission;

import com.o3dr.android.client.Drone;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.mission.item.MissionItem;
import com.o3dr.services.android.lib.drone.mission.item.complex.StructureScanner;
import com.o3dr.services.android.lib.drone.mission.item.complex.Survey;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Circle;
import com.o3dr.services.android.lib.util.MathUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is responsible for providing logic to access and interpret the
 * {@link com.o3dr.services.android.lib.drone.mission.item.MissionItem} class on the Android layer,
 * as well as providing methods for rendering it on the Android UI.
 */
public class MissionItemProxy {

    private final Drone.OnMissionItemsBuiltCallback missionItemBuiltListener = new Drone.OnMissionItemsBuiltCallback() {
        @Override
        public void onMissionItemsBuilt(MissionItem.ComplexItem[] complexItems) {
            mMission.notifyMissionUpdate(false);
        }
    };


    /**
	 * This is the mission item object this class is built around.
	 */
	private final MissionItem mMissionItem;

	/**
	 * This is the mission render to which this item belongs.
	 */
	private final MissionProxy mMission;

    /**
     * Used by the mission item list adapter to provide drag and drop support.
     */
    private final long stableId;

	public MissionItemProxy(MissionProxy mission, MissionItem missionItem) {
        this.stableId = System.nanoTime();

		mMission = mission;
		mMissionItem = missionItem;

        if(mMissionItem instanceof Survey){
            mMission.getDrone().buildMissionItemsAsync(missionItemBuiltListener, (Survey) mMissionItem);
        }
        else if(mMissionItem instanceof StructureScanner){
            mMission.getDrone().buildMissionItemsAsync(missionItemBuiltListener, (StructureScanner) mMissionItem);
        }
	}

	/**
	 * Provides access to the owning mission render instance.
	 * 
	 * @return
	 */
	public MissionProxy getMissionProxy() {
		return mMission;
	}

    public MissionProxy getMission(){return mMission;}

	/**
	 * Provides access to the mission item instance.
	 * 
	 * @return {@link com.o3dr.services.android.lib.drone.mission.item.MissionItem} object
	 */
	public MissionItem getMissionItem() {
		return mMissionItem;
	}

	/**
	 * @param previousPoint
	 *            Previous point on the path, null if there wasn't a previous
	 *            point
	 * @return the set of points/coords making up this mission item.
	 */
	public List<LatLong> getPath(LatLong previousPoint) {
		List<LatLong> pathPoints = new ArrayList<LatLong>();
		switch (mMissionItem.getType()) {
		case LAND:
		case WAYPOINT:
		case SPLINE_WAYPOINT:
			pathPoints.add(((MissionItem.SpatialItem) mMissionItem).getCoordinate());
			break;

		case CIRCLE:
			for (int i = 0; i <= 360; i += 10) {
				Circle circle = (Circle) mMissionItem;
				double startHeading = 0;
				if (previousPoint != null) {
					startHeading = MathUtils.getHeadingFromCoordinates(circle.getCoordinate(),
                            previousPoint);
				}
				pathPoints.add(MathUtils.newCoordFromBearingAndDistance(circle.getCoordinate(),
                        startHeading + i, circle.getRadius()));
			}
			break;

		case SURVEY:
            List<LatLong> gridPoints = ((Survey)mMissionItem).getGridPoints();
			if (gridPoints != null && !gridPoints.isEmpty()) {
				pathPoints.addAll(gridPoints);
			}
			break;

		case STRUCTURE_SCANNER:
			StructureScanner survey = (StructureScanner)mMissionItem;
			pathPoints.addAll(survey.getPath());
			break;

		default:
			break;
		}

		return pathPoints;
	}

    /**
     * @return stable id used by the recycler view adapter to provide drag and drop support.
     */
    public long getStableId(){
        return stableId;
    }
}
