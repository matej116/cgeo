package cgeo.geocaching.maps.google.v2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.ui.IconGenerator;

import java.util.ArrayList;
import java.util.List;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Units;
import cgeo.geocaching.maps.PositionHistory;
import cgeo.geocaching.maps.interfaces.PositionAndHistory;
import cgeo.geocaching.settings.Settings;


public class GooglePositionAndHistory implements PositionAndHistory {

    public static float ZINDEX_DISTANCE_LABEL = 6;
    public static float ZINDEX_DIRECTION_LINE = 5;
    public static float ZINDEX_POSITION = 10;
    public static float ZINDEX_POSITION_ACCURACY_CIRCLE = 3;
    public static float ZINDEX_HISTORY = 2;
    public static float ZINDEX_HISTORY_SHADOW = 1;


    private LatLng intentCoords;
    private Location coordinates;
    private float heading;
    private PositionHistory history = new PositionHistory();

    private final int maxHistoryPoints = 230; // TODO add alpha, make changeable in constructor?


    private GoogleMapObjects positionObjs;
    private GoogleMapObjects historyObjs;


    public GooglePositionAndHistory(GoogleMap googleMap, Geopoint coords) {
        this.intentCoords = coords == null ? null : new LatLng(coords.getLatitude(), coords.getLongitude());
        positionObjs = new GoogleMapObjects(googleMap);
        historyObjs = new GoogleMapObjects(googleMap);
    }

    @Override
    public void setCoordinates(Location coord) {
        boolean coordChanged = coord == null ? coordinates != null : !coord.equals(coordinates);
        coordinates = coord;
        if (coordChanged) {
            history.rememberTrailPosition(coordinates);
        }
    }

    @Override
    public Location getCoordinates() {
        return coordinates;
    }

    @Override
    public void setHeading(float heading) {
        if (this.heading != heading) {
            this.heading = heading;
        }
    }

    @Override
    public float getHeading() {
        return heading;
    }

    @Override
    public ArrayList<Location> getHistory() {
        return history.getHistory();
    }

    @Override
    public void setHistory(ArrayList<Location> history) {
        if (history != this.history.getHistory()) {
            this.history.setHistory(history);
        }
    }

    @Override
    public void repaintRequired() {
        drawPosition();
        if (Settings.isMapTrail()) {
            drawHistory();
        }
    }


    private static Bitmap locationIcon;

    private static Bitmap getLocationIcon() {
        if (locationIcon == null) {
            locationIcon = BitmapFactory.decodeResource(CgeoApplication.getInstance().getResources(), R.drawable.my_location_chevron);
        }
        return locationIcon;
    }

    private synchronized void drawPosition() {
        positionObjs.removeAll();
        if (this.coordinates == null) return;

        LatLng latLng = new LatLng(coordinates.getLatitude(), coordinates.getLongitude());

        // accuracy circle
        positionObjs.addCircle(new CircleOptions()
                .center(latLng)
                .strokeColor(0x66000000)
                .strokeWidth(3)
                .fillColor(0x08000000)
                .radius(coordinates.getAccuracy())
                .zIndex(ZINDEX_POSITION_ACCURACY_CIRCLE)
        );

        positionObjs.addMarker(new MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(getLocationIcon()))
                .position(latLng)
                .rotation(heading)
                .anchor(0.5f, 0.5f)
                .zIndex(ZINDEX_POSITION)
        );

        if (intentCoords != null) {
            // draw direction line

            positionObjs.addPolyline(new PolylineOptions()
                    .width(4)
                    .color(0x80EB391E)
                    .add(latLng, intentCoords)
                    .zIndex(ZINDEX_DIRECTION_LINE)
            );


            // draw distance label, TODO show distance as fixed position on viewport, not as marker at coords

            BitmapDescriptor bd = BitmapDescriptorFactory.fromBitmap(
                    new IconGenerator(CgeoApplication.getInstance().getApplicationContext())
                            .makeIcon(Units.getDistanceFromMeters(getDistance(coordinates, intentCoords))));

            positionObjs.addMarker(new MarkerOptions()
                    .icon(bd)
                    .position(latLng)
                    .zIndex(ZINDEX_DISTANCE_LABEL)
            );
        }
    }

    /**
     * helper function to compute distance between Location and LatLng
     */
    private static float getDistance(Location l1, LatLng l2) {
        final float results[] = new float[1];
        Location.distanceBetween(l1.getLatitude(), l1.getLongitude(), l2.latitude, l2.longitude, results);
        return results[0];
    }


    private synchronized void drawHistory() {
        historyObjs.removeAll();
        List<Location> history = getHistory();
        if (history.isEmpty()) return;

        final int size = history.size();

        List<LatLng> points = new ArrayList<>(maxHistoryPoints);

        for (int i = 1; i < size; i++) {
            Location loc = history.get(i);
            LatLng ll = new LatLng(loc.getLatitude(), loc.getLongitude());
            points.add(ll);
        }

        if (coordinates != null) {
            LatLng ll = new LatLng(coordinates.getLatitude(), coordinates.getLongitude());
            points.add(ll);
        }

        final float alpha = 1; // TODO

        // history line
        historyObjs.addPolyline(new PolylineOptions()
                .addAll(points)
                .color(0xFFFFFF | ((int) (alpha * 0xff) << 24))
                .width(3)
                .zIndex(ZINDEX_HISTORY)
        );

        // history line shadow
        historyObjs.addPolyline(new PolylineOptions()
                .addAll(points)
                .color(0x000000 | ((int) (alpha * 0x66) << 24))
                .width(7)
                .zIndex(ZINDEX_HISTORY_SHADOW)
        );

    }
}
