package cgeo.geocaching.maps.google.v2;

import android.support.annotation.NonNull;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cgeo.geocaching.location.IConversion;
import cgeo.geocaching.maps.interfaces.CachesOverlayItemImpl;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.Log;

public class GoogleCachesList {

    protected static final double CIRCLE_RADIUS = 528.0 * IConversion.FEET_TO_KILOMETER * 1000.0;
    public static float ZINDEX_GEOCACHE = 4;
    public static float ZINDEX_WAYPOINT = 3;
    public static float ZINDEX_CIRCLE   = 2;

    private Collection<MapObjectOptions> options;

    private final Map<MapObjectOptions, MapObjectOptionsFactory> createdBy = new HashMap<>();
    private final GoogleMapObjectsQueue mapObjects;

    public GoogleCachesList(GoogleMap googleMap) {
        mapObjects = new GoogleMapObjectsQueue(googleMap);
    }


    private static Set<MapObjectOptions> diff(Collection<MapObjectOptions> one, Collection<MapObjectOptions> two)
    {
        Set<MapObjectOptions> set = new HashSet<>(one);
        set.removeAll(two); // rely on MapObjectOptions.equals() implementation
        return set;
    }


    public void redraw(@NonNull Collection<? extends MapObjectOptionsFactory> itemsPre, boolean showCircles) {
        final Collection<MapObjectOptions> options = updateMapObjectOptions(itemsPre, showCircles);
        updateMapObjects(options);
    }

    private void updateMapObjects(@NonNull Collection<MapObjectOptions> options) {
        if (this.options == options) {
            return; // rare, can happen, be prepared if happens
        }
        if (this.options == null) {
            this.options = options;
            mapObjects.requestAdd(this.options);
        } else {
            final Collection<MapObjectOptions> toRemove = diff(this.options, options);
            final Collection<MapObjectOptions> toAdd = toRemove.size() == this.options.size() ? options : diff(options, this.options);
            Log.i("From original "+ this.options.size()  +" items will be " + toAdd.size() + " added and " + toRemove.size() + " removed to match new count " + options.size());
            this.options = options;

            mapObjects.requestRemove(toRemove);
            mapObjects.requestAdd(toAdd);
        }
    }

    private Collection<MapObjectOptions> updateMapObjectOptions(Collection<? extends MapObjectOptionsFactory> items, boolean showCircles) {
        Collection<MapObjectOptions> options = new ArrayList<>(items.size());
        synchronized (createdBy) {
            createdBy.clear();
            for (MapObjectOptionsFactory factory : items) {
                for (MapObjectOptions opts : factory.getMapObjectOptions(showCircles)) {
                    options.add(opts);
                    createdBy.put(opts, factory);
                }
            }
        }
        return options;
    }


    public CachesOverlayItemImpl getDrawnItem(Marker marker) {

        return (GoogleCacheOverlayItem) createdBy.get(mapObjects.getDrawnBy(marker));
    }
}
