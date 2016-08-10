package cgeo.geocaching.maps.google.v2;


import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.Collection;

import cgeo.geocaching.utils.Log;


/**
 * class to wrap GoogleMapObjectsQueue, able to draw individually map objects and to remove all previously
 * drawn
 */
public class GoogleMapObjects {

    private final GoogleMapObjectsQueue queue;
    /**
     * list of object options to be drawn to google map
     */
    private final Collection<MapObjectOptions> objects = new ArrayList<>();

    public GoogleMapObjects(GoogleMap googleMap) {
        queue = new GoogleMapObjectsQueue(googleMap);
    }

    protected void addOptions(final Object options) {
        synchronized (objects) {
            Log.i("Adding options: " + this.hashCode() + " " + options.hashCode());
            MapObjectOptions opts = MapObjectOptions.from(options);
            objects.add(opts);
            queue.requestAdd(opts);
        }
    }

    public void addMarker(MarkerOptions opts) {
        addOptions(opts);
    }

    public void addCircle(CircleOptions opts) {
        addOptions(opts);
    }

    public void addPolyline(PolylineOptions opts) {
        addOptions(opts);
    }


    public void removeAll() {
        synchronized (objects) {
            queue.requestRemove(objects);
            objects.clear();
        }
    }
}
