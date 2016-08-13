package cgeo.geocaching.maps.google.v2;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import java.util.Map;
import java.util.WeakHashMap;

import cgeo.geocaching.maps.CacheMarker;
import cgeo.geocaching.utils.Log;

public class BitmapDescriptorCache {

    /**
     * rely on unique hashcode of CacheMarker
     */
    protected final SparseArray<BitmapDescriptor> cache = new SparseArray<>();

    public BitmapDescriptor fromCacheMarker(CacheMarker d)
    {
        BitmapDescriptor bd = cache.get(d.hashCode());
        if (bd == null) {
            bd = toBitmapDescriptor(d.getDrawable());
            cache.put(d.hashCode(), bd);
        }
        return bd;
    }

    public static BitmapDescriptor toBitmapDescriptor(Drawable d) {
        Canvas canvas = new Canvas();
        int width = d.getIntrinsicWidth();
        int height = d.getIntrinsicHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap);
        d.setBounds(0, 0, width, height);
        d.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

}
