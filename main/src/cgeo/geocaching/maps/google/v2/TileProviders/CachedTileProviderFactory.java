package cgeo.geocaching.maps.google.v2.TileProviders;


import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import com.google.android.gms.maps.model.TileProvider;
import com.jakewharton.disklrucache.DiskLruCache;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

import cgeo.geocaching.utils.Log;



public class CachedTileProviderFactory {



    protected final File path;
    protected static final int APP_VERSION = 100;

    // keep opened DiskLruCaches to avoid creating more of one folder
    private final Map<String, DiskLruCache> cache = new WeakHashMap<>();

    public CachedTileProviderFactory(File path) throws IOException {
        if (!path.exists()) {
            if (!path.mkdirs()) {
                throw new IOException("Could not create directory: " + path);
            }
        } else {
            if (!path.isDirectory()) {
                throw new IOException("File is not directory: " + path);
            }
        }
        this.path = path;
    }


    public static TileProvider createCacheTileProviderOnSdCard(TileProvider of, String key)
    {
        if (HOLDER.instance != null) {
            return HOLDER.instance.createCachedTileProvider(of, key);
        } else {
            return of;
        }
    }

    public TileProvider createCachedTileProvider(TileProvider of, String key)
    {
        try {
            return new CachedTileProvider(of, getDiskLruCache(key));
        } catch (IOException e) {
            Log.e("Could not create CachedTileProvider with DiskLruCache", e);
            return of;
        }
    }

    private DiskLruCache getDiskLruCache(String key) throws IOException {
        DiskLruCache c = cache.get(key);
        if (c == null || c.isClosed()) {
            c = DiskLruCache.open(new File(path, key), APP_VERSION, 1, getSpaceForCache());
            cache.put(key, c);
        }
        return c;
    }

    private long getSpaceForCache() {
        if (Build.VERSION.SDK_INT >= 18) {
            try {
                StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
                long bytesAvailable = stat.getBlockSizeLong() * stat.getBlockCountLong();

                // don't be too hungry
                return (long) Math.min(300 * 1e6, bytesAvailable / 5);
            } catch (Exception e) {
                Log.e("Cannot get free space ", e);
            }
        }
        // fallback
        return (long) (300 * 1e6); // 300 MB should be enough
    }

    private static class HOLDER {

        private static CachedTileProviderFactory create() {
            try {
                return new CachedTileProviderFactory(new File(Environment.getExternalStorageDirectory(), ".cgeo/tilecache"));
            } catch (IOException e) {
                Log.e("Could not create CachedTileProviderFactory ", e);
                return null;
            }
        }

        static CachedTileProviderFactory instance = create();
    }


}
