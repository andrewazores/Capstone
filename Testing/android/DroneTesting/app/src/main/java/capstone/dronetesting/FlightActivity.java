package capstone.dronetesting;

import android.location.Location;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

public class FlightActivity extends FragmentActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static int NUM_STEPS = 5;


    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private GoogleApiClient mGoogleApiClient;
    private List<Marker> mEndPoints = new ArrayList<>();
    private List<Marker> mDestinations = new ArrayList<>();
    private Polyline mFlightLine;

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        buildGoogleApiClient();
        setUpMapIfNeeded();

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick (LatLng latLng) {
                MarkerOptions mo = new MarkerOptions().position(latLng);

                mEndPoints.add(mMap.addMarker(mo));

                drawLines();
            }
        });

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick (Marker marker) {
                marker.remove();
                mEndPoints.remove(marker);
                drawLines();

                return false;
            }
        });
    }

    private void drawLines(){
        //clear map
        if (mFlightLine != null) {
            mFlightLine.remove();
        }

        //add new line
        if(mEndPoints.size() > 1) {
            PolylineOptions plOptions = new PolylineOptions();
            for(Marker marker : mEndPoints){
                plOptions.add(marker.getPosition());
            }
            mFlightLine = mMap.addPolyline(plOptions);
        }

        drawMiniMarkers();
    }

    private void drawMiniMarkers() {
        for (Marker m : mDestinations) {
            m.remove();
        }

        mDestinations.clear();

        Marker lastMarker = null;
        for(Marker marker: mEndPoints) {
            if (lastMarker == null){
               lastMarker = marker;
               continue;
            }

            LatLng origin = lastMarker.getPosition();
            LatLng destination = marker.getPosition();

            double[] path = new double[2];
            path[0] = (origin.latitude - destination.latitude);
            path[1] = (origin.longitude - destination.longitude);

            path[0] /=  (NUM_STEPS - 1);
            path[1] /=  (NUM_STEPS - 1);

            mDestinations.add(mMap.addMarker(new MarkerOptions().position(origin)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.mini_marker))));
            for(int i = 1; i < NUM_STEPS - 1; i++) {
                LatLng subDest = new LatLng(origin.latitude - i*path[0], origin.longitude - i*path[1]);
                mDestinations.add(mMap.addMarker(new MarkerOptions().position(subDest)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.mini_marker))));
            }
            mDestinations.add(mMap.addMarker(new MarkerOptions().position(destination)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.mini_marker))));

            lastMarker = marker;
        }
    }

    @Override
    protected void onResume () {
        super.onResume();
        setUpMapIfNeeded();
    }

    private void setUpMapIfNeeded () {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private void setUpMap () {
        mMap.setMyLocationEnabled(true);
    }

    @Override
    public void onConnected (Bundle bundle) {
        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        if (mLastLocation != null) {
            LatLng ll = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, 16));
        }
    }

    @Override
    public void onConnectionSuspended (int i) {
    }

    @Override
    public void onConnectionFailed (ConnectionResult connectionResult) {
        Toast.makeText(this, "failed", Toast.LENGTH_SHORT).show();
    }
}
