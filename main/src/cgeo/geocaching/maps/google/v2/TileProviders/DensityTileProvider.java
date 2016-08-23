package cgeo.geocaching.maps.google.v2.TileProviders;

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
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import cgeo.geocaching.utils.ImageUtils;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.RxUtils;
import okhttp3.Call;
import rx.Observable;
import rx.functions.Func4;

public class DensityTileProvider implements TileProvider {


    private TileProvider provider;
    private final int factor;

    ThreadGroup threads;

    public DensityTileProvider(TileProvider provider, int factor) {

        this.provider = provider;
        this.factor = factor;
    }

    public DensityTileProvider(TileProvider provider, DisplayMetrics metrics) {
        this(provider, factorOfDisplay(metrics)); // factorOfDisplay());
    }



    protected Observable<Tile> getTileFromProviderAsync(final int x, final int y, final int zoom) {
        return Observable.fromCallable(new Callable<Tile>() {
            @Override
            public Tile call() throws Exception {
                return provider.getTile(x, y, zoom);
            }
        });
    }



    protected Observable<Tile> mergeTiles(final int x, final int y, final int zoom, int factor) {
        if (factor <= 1) {
            return getTileFromProviderAsync(x, y, zoom);
        }
        Observable<Tile> lt = getTileFromProviderAsync(x * 2, y * 2, zoom + 1);
        Observable<Tile> rt = getTileFromProviderAsync(x * 2 + 1, y * 2, zoom + 1);
        Observable<Tile> lb = getTileFromProviderAsync(x * 2, y * 2 + 1, zoom + 1);
        Observable<Tile> rb = getTileFromProviderAsync(x * 2 + 1, y * 2 + 1, zoom + 1);


        return Observable.zip(lt, rt, lb, rb, new Func4<Tile, Tile, Tile, Tile, Tile>() {
            @Override
            public Tile call(Tile lt, Tile rt, Tile lb, Tile rb) {
                if (lt == null || rt == null || lb == null || rb == null) {
                    // one of the tiles is not available, return null to signalize whole tile is not available
                    return null;
                }
                if (lt == NO_TILE || rt == NO_TILE || lb == NO_TILE || rb == NO_TILE) {
                    // return lower resolution as callback
                    return provider.getTile(x, y, zoom);
                }


//                if (lt.width != lb.width || lt.width != rt.width || lt.width != rb.width) {
//                    Log.w("Tiles cannot be merged, not same width");
//                    return provider.getTile(x, y, zoom);
//                }
//                if (lt.height != lb.height|| lt.height!= rt.height|| lt.height != rb.height) {
//                    Log.w("Tiles cannot be merged, not same height");
//                    return provider.getTile(x, y, zoom);
//                }

                int w = lt.width + rt.width;
                int h = lt.height + lb.height;

                Bitmap wrap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

                Canvas c = new Canvas(wrap);
                c.drawBitmap(BitmapFactory.decodeByteArray(lt.data, 0, lt.data.length), 0, 0, null);
                c.drawBitmap(BitmapFactory.decodeByteArray(rt.data, 0, rt.data.length), lt.width, 0, null);
                c.drawBitmap(BitmapFactory.decodeByteArray(lb.data, 0, lb.data.length), 0, lt.height, null);
                c.drawBitmap(BitmapFactory.decodeByteArray(rb.data, 0, rb.data.length), lb.width, rt.height, null);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                wrap.compress(Bitmap.CompressFormat.PNG, 0, baos);

                return new Tile(w, h, baos.toByteArray());
            }
        });
    }


    protected static int factorOfDisplay(DisplayMetrics metrics) {
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
        return mergeTiles(x, y, zoom, factor).toBlocking().first();
    }


}
