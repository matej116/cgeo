package cgeo.geocaching.maps.google.v2;

import android.graphics.drawable.Drawable;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.models.IWaypoint;
import cgeo.geocaching.maps.interfaces.CachesOverlayItemImpl;


public class GoogleCacheOverlayItem implements CachesOverlayItemImpl, MapObjectOptionsFactory {
    private final IWaypoint coord;
    private final boolean applyDistanceRule;
    private BitmapDescriptorCache bitmapDescriptorCache;
    private Drawable marker;

    public GoogleCacheOverlayItem(final IWaypoint coordinate, final boolean applyDistanceRule) {
        this.coord = coordinate;
        this.applyDistanceRule = applyDistanceRule;
    }

    @Override
    public IWaypoint getCoord() {
        return coord;
    }

    @Override
    public boolean applyDistanceRule() {
        return applyDistanceRule;
    }

    @Override
    public String getTitle() {
        return this.coord.getName();
    }

    @Override
    public Drawable getMarker(int index) {
        return marker;
    }

    @Override
    public void setMarker(Drawable markerIn) {
        this.marker = markerIn;
    }

    private static LatLng toLatLng(IWaypoint w) {
        return new LatLng(w.getCoords().getLatitude(), w.getCoords().getLongitude());
    }

    public void setBitmapDescriptorCache(BitmapDescriptorCache bitmapDescriptorCache) {
        this.bitmapDescriptorCache = bitmapDescriptorCache;
    }

    @Override
    public MapObjectOptions[] getMapObjectOptions(boolean showCircles) {
        MarkerOptions marker = new MarkerOptions()
                .icon(toBitmapDescriptor(this.marker))
                .position(toLatLng(coord))
                .anchor(0.5f, 1)
                .zIndex((coord instanceof Geocache) ? GoogleCachesList.ZINDEX_GEOCACHE : GoogleCachesList.ZINDEX_WAYPOINT);

        if (showCircles && applyDistanceRule) {
            CircleOptions circle = new CircleOptions()
                    .center(toLatLng(coord))
                    .strokeColor(0x44BB0000)
                    .strokeWidth(2)
                    .fillColor(0x66BB0000)
                    .radius(GoogleCachesList.CIRCLE_RADIUS)
                    .zIndex(GoogleCachesList.ZINDEX_CIRCLE);

            return new MapObjectOptions[]{MapObjectOptions.from(marker), MapObjectOptions.from(circle)};
        } else {
            return new MapObjectOptions[]{MapObjectOptions.from(marker)};
        }
    }

    private BitmapDescriptor toBitmapDescriptor(Drawable d) {
        if (bitmapDescriptorCache != null) {
            return bitmapDescriptorCache.fromDrawable(d);
        } else {
            return BitmapDescriptorCache.toBitmapDescriptor(d);
        }
    }
}
