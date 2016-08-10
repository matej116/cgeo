package cgeo.geocaching.maps.google.v2;

import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.maps.interfaces.MapViewImpl;
import cgeo.geocaching.maps.interfaces.OverlayImpl;

import com.google.android.gms.maps.GoogleMap;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GoogleOverlay implements OverlayImpl {

    private GooglePositionAndHistory overlayBase = null;
    private final Lock lock = new ReentrantLock();

    public GoogleOverlay(final GoogleMap googleMap, final Geopoint coords) {
        overlayBase = new GooglePositionAndHistory(googleMap, coords);
    }


    public GooglePositionAndHistory getBase() {
        return overlayBase;
    }

    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public void unlock() {
        lock.unlock();
    }

    @Override
    public MapViewImpl getMapViewImpl() {
        throw new UnsupportedOperationException();
    }

}
