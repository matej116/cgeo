package cgeo.geocaching.maps.google.v2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.Image;
import android.media.ImageReader;
import android.provider.MediaStore;
import android.util.DisplayMetrics;

import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.gms.maps.model.UrlTileProvider;


import java.io.ByteArrayOutputStream;

import cgeo.geocaching.utils.ImageUtils;
import cgeo.geocaching.utils.Log;

public class DensityTileProvider implements TileProvider {


    private TileProvider provider;
    private int factor;

    public DensityTileProvider(TileProvider provider, int factor) {

        this.provider = provider;
        this.factor = factor;
    }

    public DensityTileProvider(TileProvider provider, DisplayMetrics metrics) {
        this(provider, factorOfDisplay(metrics)); // factorOfDisplay());
    }

    protected Tile mergeTiles(int x, int y, int zoom, int factor)
    {
        if (factor <= 1) {
            return provider.getTile(x, y, zoom);
        }
        Tile lt = provider.getTile(x * 2    , y * 2    , zoom + 1);
        if (lt == NO_TILE) {
            // fallback to lower resolution
            return provider.getTile(x, y, zoom);
        }
        Tile rt = provider.getTile(x * 2 + 1, y * 2    , zoom + 1);
        Tile lb = provider.getTile(x * 2    , y * 2 + 1, zoom + 1);
        Tile rb = provider.getTile(x * 2 + 1, y * 2 + 1, zoom + 1);

//        if (lt.width != lb.width || lt.width != rt.width || lt.width != rb.width) {
//            Log.w("Tiles cannot be merged, not same width");
//            return provider.getTile(x, y, zoom);
//        }
//        if (lt.height != lb.height|| lt.height!= rt.height|| lt.height != rb.height) {
//            Log.w("Tiles cannot be merged, not same height");
//            return provider.getTile(x, y, zoom);
//        }

        int w = lt.width + rt.width;
        int h = lt.height + lb.height;

        Bitmap wrap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

        android.graphics.Canvas c = new Canvas(wrap);
        c.drawBitmap(BitmapFactory.decodeByteArray(lt.data, 0, lt.data.length), 0, 0, null);
        c.drawBitmap(BitmapFactory.decodeByteArray(rt.data, 0, rt.data.length), lt.width, 0, null);
        c.drawBitmap(BitmapFactory.decodeByteArray(lb.data, 0, lb.data.length), 0, lt.height, null);
        c.drawBitmap(BitmapFactory.decodeByteArray(rb.data, 0, rb.data.length), lb.width, rt.height, null);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        wrap.compress(Bitmap.CompressFormat.PNG,0, baos);

        return new Tile(w, h, baos.toByteArray());
    }


    protected static int factorOfDisplay(DisplayMetrics metrics)
    {
        float density = metrics.density;
        // compute ceil(log base 2 of density)
        return 1 + (int) Math.ceil(Math.log(Math.ceil(density)) / Math.log(2));
        // for density = 1, this will return 1
        // for density = 2, this will return 2
        // for density = 3-4, this will return 3
        // for density = 5-7, this will return 4
        // .. and so on
    }

    @Override
    public Tile getTile(int x, int y, int zoom) {
        return mergeTiles(x, y, zoom, factor);
    }


}
