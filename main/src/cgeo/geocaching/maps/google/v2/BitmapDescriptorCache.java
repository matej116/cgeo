package cgeo.geocaching.maps.google.v2;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import java.util.Map;
import java.util.WeakHashMap;

import cgeo.geocaching.utils.Log;

public class BitmapDescriptorCache {

    /**
     * cache must be WeakMap - cached Drawables are cleared and recreated after every MapView.onPause()
     */
    protected final Map<Drawable, BitmapDescriptor> cache = new WeakHashMap<>();

    public BitmapDescriptor fromDrawable(Drawable d)
    {
        BitmapDescriptor bd = cache.get(d);
        if (bd == null) {
            bd = toBitmapDescriptor(d);
            cache.put(d, bd);
        } else {
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
