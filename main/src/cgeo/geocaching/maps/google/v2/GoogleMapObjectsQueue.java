package cgeo.geocaching.maps.google.v2;

import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cgeo.geocaching.utils.Log;

public class GoogleMapObjectsQueue {


    private final GoogleMap googleMap;

    private boolean repaintRequested = false;

    private final ConcurrentLinkedQueue<MapObjectOptions> requestedToAdd = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<MapObjectOptions> requestedToRemove = new ConcurrentLinkedQueue<>();

    private final RepaintRunner repaintRunner = new RepaintRunner();

    private final Lock lock = new ReentrantLock();


    public GoogleMapObjectsQueue(GoogleMap googleMap) {
        this.googleMap = googleMap;
    }

    public void requestAdd(Collection<? extends MapObjectOptions> toAdd) {
        requestedToAdd.addAll(toAdd);
        requestRepaint();
    }

    public void requestAdd(MapObjectOptions toAdd) {
        requestedToAdd.add(toAdd);
        requestRepaint();
    }

    public void requestRemove(Collection<? extends MapObjectOptions> toRemove) {
        requestedToRemove.addAll(toRemove);
        requestRepaint();
    }

    void requestRepaint() {
        lock.lock();
        boolean isMainThread = false; // Looper.getMainLooper().getThread() == Thread.currentThread();
        if (isMainThread) {
            repaintRunner.run();
        }
        if (!repaintRequested)  {
            repaintRequested = true;
            if (!isMainThread) {
                runOnUIThread(repaintRunner);
            }
        }
        lock.unlock();
    }

    public MapObjectOptions getDrawnBy(Object drawn) {
        return repaintRunner.getDrawnBy(drawn);
    }


    public void runOnUIThread(Runnable runnable) {
        // inspired by http://stackoverflow.com/questions/12850143/android-basics-running-code-in-the-ui-thread/25250494#25250494
        // modifications of google map must be run on main (UI) thread
        new Handler(Looper.getMainLooper()).post(runnable);
    }


    private static void removeDrawnObject(Object obj) {
        if (obj == null) return; // failsafe
        if (obj instanceof Marker) {
            ((Marker) obj).remove();
        } else
        if (obj instanceof Circle) {
            ((Circle) obj).remove();
        } else
        if (obj instanceof Polyline) {
            ((Polyline) obj).remove();
        } else {
            throw new IllegalStateException();
        }
    }

    private class RepaintRunner implements Runnable {

        /**
         * magic number of milliseconds. maximum allowed time of adding or removing items to googlemap
         */
        protected static final long TIME_MAX = 40;

        private final Map<MapObjectOptions, Object> drawObjects = new HashMap<>();
        private final Map<Object, MapObjectOptions> drawnBy = new HashMap<>();

        private boolean removeRequested() {
            long time = System.currentTimeMillis();
            MapObjectOptions options;
            while ((options = requestedToRemove.poll()) != null) {
                Object obj = drawObjects.get(options);
                if (obj != null) {
                    removeDrawnObject(obj);
                    drawnBy.remove(obj);
                } else {
                    // could not remove, is it enqueued to be draw?
                    if (requestedToAdd.contains(options)) {
                        // if yes, it is not anymore
                        requestedToAdd.remove(options);
                    }
                }
                if (System.currentTimeMillis() - time >= TIME_MAX) {
                    // removing and adding markers are time costly operations and we don't want to block UI thread
                    runOnUIThread(this);
                    return false;
                }
            }
            return true;
        }

        @Override
        public void run() {
            lock.lock();
            if (repaintRequested) {
                if (removeRequested() && addRequested()) {
                    // repaint successful, set flag to false
                    repaintRequested = false;
                }
            }
            lock.unlock();
        }

        private boolean addRequested() {
            long time = System.currentTimeMillis();
            MapObjectOptions options;
            while ((options = requestedToAdd.poll()) != null) {
                Object drawn = options.addToGoogleMap(googleMap);
                drawnBy.put(drawn, options);
                drawObjects.put(options, drawn);
                if (System.currentTimeMillis() - time >= TIME_MAX) {
                    // removing and adding markers are time costly operations and we dont want to block UI thread
                    runOnUIThread(this);
                    return false;
                }
            }
            return true;
        }

        public MapObjectOptions getDrawnBy(Object drawn) {
            return drawnBy.get(drawn);
        }
    }
}
