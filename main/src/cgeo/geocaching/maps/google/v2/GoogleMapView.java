package cgeo.geocaching.maps.google.v2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.VisibleRegion;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.Viewport;
import cgeo.geocaching.maps.ScaleDrawer;
import cgeo.geocaching.maps.interfaces.CachesOverlayItemImpl;
import cgeo.geocaching.maps.interfaces.GeneralOverlay;
import cgeo.geocaching.maps.interfaces.GeoPointImpl;
import cgeo.geocaching.maps.interfaces.MapControllerImpl;
import cgeo.geocaching.maps.interfaces.MapProjectionImpl;
import cgeo.geocaching.maps.interfaces.MapReadyCallback;
import cgeo.geocaching.maps.interfaces.MapViewImpl;
import cgeo.geocaching.maps.interfaces.OnCacheTapListener;
import cgeo.geocaching.maps.interfaces.OnMapDragListener;
import cgeo.geocaching.maps.interfaces.PositionAndHistory;
import cgeo.geocaching.models.ICoordinates;
import cgeo.geocaching.models.IWaypoint;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.Log;

public class GoogleMapView extends MapView implements MapViewImpl<GoogleCacheOverlayItem>, OnMapReadyCallback {

    private OnMapDragListener onDragListener;
    private final GoogleMapController mapController = new GoogleMapController();
    private GoogleMap googleMap;
    private MapReadyCallback mapReadyCallback;

    private LatLng viewCenter;
    private float zoomLevel;
    private float bearing;
    private VisibleRegion visibleRegion;

    private GoogleCachesList cachesList;
    private GestureDetector gestureDetector;
    private Collection<GoogleCacheOverlayItem> cacheItems;

    private OnCacheTapListener onCacheTapListener;
    private boolean showCircles = false;

    private Lock lock = new ReentrantLock();

    private final ScaleDrawer scaleDrawer = new ScaleDrawer();

    public GoogleMapView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public GoogleMapView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        initialize(context);
    }


    public void onMapReady(final GoogleMap googleMap) {
        if (this.googleMap != null) {
            if (this.googleMap == googleMap) {
                return;
            } else {
                throw new IllegalStateException("Could not set new google map - already set");
            }
        }
        this.googleMap = googleMap;
        mapController.setGoogleMap(googleMap);
        cachesList = new GoogleCachesList(googleMap);
        googleMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                // update all variable, which getters are available only in main thread
                viewCenter = cameraPosition.target;
                zoomLevel = cameraPosition.zoom;
                bearing = cameraPosition.bearing;
                VisibleRegion newVisibleRegion = googleMap.getProjection().getVisibleRegion();
                if (newVisibleRegion != null) {
                    visibleRegion = newVisibleRegion;
                }
                invalidate(); // force redraw to draw scale
            }
        });
        googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                // onCacheTapListener will fire on onSingleTapUp event, not here, because this event
                // is fired 300 ms after map tap, which is too slow for UI

                // suppress default behaviour (yeah, true == suppress)
                // ("The default behavior is for the camera to move to the marker and an info window to appear.")
                return true;
            }
        });
        if (mapReadyCallback != null) {
            mapReadyCallback.mapReady();
            mapReadyCallback = null;
        }
        redraw();
    }


    private void initialize(final Context context) {
        if (isInEditMode()) {
            return;
        }
        getMapAsync(this);
        gestureDetector = new GestureDetector(context, new GestureListener());
    }


    @Override
    public void setBuiltInZoomControls(boolean b) {
        if (googleMap == null) return;
        googleMap.getUiSettings().setZoomControlsEnabled(b);
    }

    @Override
    public void displayZoomControls(final boolean takeFocus) {
        // nothing to do here, TODO merge design with mapsforge zoom controls?
    }

    @Override
    public MapControllerImpl getMapController() {
        return mapController;
    }

    @Override
    public GeoPointImpl getMapViewCenter() {
        if (viewCenter == null) return null;
        return new GoogleGeoPoint(viewCenter);
    }

    @Override
    public int getLatitudeSpan() {
        if (visibleRegion == null) return -1;
        return (int) (Math.abs(visibleRegion.latLngBounds.northeast.latitude - visibleRegion.latLngBounds.southwest.latitude) * 1e6);
    }

    @Override
    public int getLongitudeSpan() {
        if (visibleRegion == null) return -1;
        return (int) (Math.abs(visibleRegion.latLngBounds.northeast.longitude - visibleRegion.latLngBounds.southwest.longitude) * 1e6);
    }

    @Override
    public Viewport getViewport() {
        if (visibleRegion == null) return null;
        return new Viewport(new GoogleGeoPoint(visibleRegion.farLeft), new GoogleGeoPoint(visibleRegion.nearRight));
    }

    @Override
    public void clearOverlays() {
        // do nothing, there are no overlays to be cleared
    }

    @Override
    public MapProjectionImpl getMapProjection() {
        if (googleMap == null) return null;
        return new GoogleMapProjection(googleMap.getProjection());
    }

    @Override
    public PositionAndHistory createAddPositionAndScaleOverlay(final Geopoint coords) {
        if (googleMap == null)
            throw new IllegalStateException("Google map not initialized yet"); // TODO check
        final GoogleOverlay ovl = new GoogleOverlay(googleMap, coords);
        return ovl.getBase();
    }

    @Override
    public int getMapZoomLevel() {
        return googleMap != null ? (int) zoomLevel : -1;
    }

    @Override
    public void setMapSource() {
        if (googleMap == null) return;
        boolean satellite = GoogleMapProvider.isSatelliteSource(Settings.getMapSource());
        googleMap.setMapType(satellite ? GoogleMap.MAP_TYPE_SATELLITE : GoogleMap.MAP_TYPE_NORMAL);
    }

    @Override
    public void repaintRequired(final GeneralOverlay overlay) {
        // FIXME add recheck/readd markers and overlay
    }

    @Override
    public void setOnDragListener(final OnMapDragListener onDragListener) {
        this.onDragListener = onDragListener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // onTouchEvent is not working for Google's MapView
        gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    public float getBearing() {
        return bearing;
    }

    private class GestureListener extends SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(final MotionEvent e) {
            // no need to move to new location, google maps will do it for us
            if (onDragListener != null) {
                onDragListener.onDrag();
            }
            return false;
        }

        /** can be made static if nonstatic inner clases could have static methods */
        private boolean insideCachePointDrawable(Point p, Point drawP, Drawable d)
        {
            int width = d.getIntrinsicWidth();
            int height = d.getIntrinsicHeight();
            int diffX = p.x - drawP.x;
            int diffY = p.y - drawP.y;
            // assume drawable is drawn above drawP
            return
                    Math.abs(diffX) < width / 2 &&
                    diffY > -height && diffY < 0;

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            Point p = new Point((int)e.getX(), (int)e.getY());
            LatLng latLng = googleMap.getProjection().fromScreenLocation(p);
            if (latLng != null && onCacheTapListener != null) {
                GoogleCacheOverlayItem closest = closest(new Geopoint(latLng.latitude, latLng.longitude));
                if (closest != null) {
                    Point waypointPoint = googleMap.getProjection().toScreenLocation(new LatLng(closest.getCoord().getCoords().getLatitude(), closest.getCoord().getCoords().getLongitude()));
                    if (insideCachePointDrawable(p, waypointPoint, closest.getMarker(0).getDrawable())) {
                        onCacheTapListener.onCacheTap(closest.getCoord());
                    }
                }
            }
            return false;
        }

        @Override
        public boolean onScroll(final MotionEvent e1, final MotionEvent e2,
                                final float distanceX, final float distanceY) {
            if (onDragListener != null) {
                onDragListener.onDrag();
            }
            return false;
        }
    }

    @Override
    public boolean needsInvertedColors() {
        return false;
    }

    @Override
    public boolean hasMapThemes() {
        // Not supported
        return false;
    }

    @Override
    public void setMapTheme() {
        // Not supported
    }

    @Override
    public void onMapReady(final MapReadyCallback callback) {
        if (callback == null) return;
        if (googleMap == null) {
            if (mapReadyCallback != null) {
                Log.e("Can not register more than one mapReadyCallback, overriding the previous one");
            }
            mapReadyCallback = callback;
        } else {
            callback.mapReady();
        }
    }

    @Override
    public void updateItems(Collection<GoogleCacheOverlayItem> itemsPre) {
        try {
            lock.lock();
            if (itemsPre != null) {
                this.cacheItems = itemsPre;
            }
            redraw();
        } finally {
            lock.unlock();
        }
    }

    public GoogleCacheOverlayItem closest(Geopoint geopoint)
    {
        final int size = cacheItems.size();
        if (size == 0) return null;
        Iterator<GoogleCacheOverlayItem> it = cacheItems.iterator();
        GoogleCacheOverlayItem closest = it.next();
        float closestDist = closest.getCoord().getCoords().distanceTo(geopoint);
        while (it.hasNext()) {
            GoogleCacheOverlayItem next = it.next();
            float dist = next.getCoord().getCoords().distanceTo(geopoint);
            if (dist < closestDist) {
                closest = next;
                closestDist = dist;
            }
        }
        return closest;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        super.dispatchDraw(canvas);
        canvas.restore();
        // cannot be in draw(), would not work
        scaleDrawer.drawScale(canvas, this);
    }

    public void redraw() {
        if (cachesList == null || cacheItems == null) return;
        cachesList.redraw(cacheItems, showCircles);
    }


    @Override
    public boolean getCircles() {
        return showCircles;
    }

    @Override
    public void switchCircles() {
        showCircles = !showCircles;
        redraw();
    }

    @Override
    public void setOnTapListener(OnCacheTapListener listener) {
        onCacheTapListener = listener;
    }

}
