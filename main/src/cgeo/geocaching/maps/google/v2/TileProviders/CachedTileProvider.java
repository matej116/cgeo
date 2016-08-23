package cgeo.geocaching.maps.google.v2.TileProviders;

import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileProvider;
import com.jakewharton.disklrucache.DiskLruCache;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import cgeo.geocaching.utils.Log;

public class CachedTileProvider implements TileProvider {

    protected final TileProvider provider;

    protected final DiskLruCache cache;

    public CachedTileProvider(TileProvider provider, DiskLruCache cache) {
        this.provider = provider;
        this.cache = cache;
    }

    public static String getKey(int x, int y, int zoom) {
        return x + "_" + y + "_" + zoom;
    }



    protected Tile getFromCache(String key) {
        InputStream in = null;
        try {
            DiskLruCache.Snapshot snapshot = cache.get(key);
            if (snapshot == null) {
                return null;
            }
            try {
                in = snapshot.getInputStream(0);
                return loadTile(in);
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        } catch (IOException e) {
            Log.e("CachedTileProvider getting key " + key, e);
            return null;
        }
    }


    protected void setToCache(String key, Tile tile) {
        DiskLruCache.Editor editor = null;
        OutputStream out = null;
        try {
            try {
                editor = cache.edit(key);
                out = editor.newOutputStream(0);
                writeTile(out, tile);
            } finally {
                if (out != null) {
                    out.close();
                }
                if (editor != null) {
                    editor.commit();
                }
            }
        } catch (IOException e) {
            Log.e("CachedTileProvider setting key " + key, e);
        }

    }

    protected static void writeTile(OutputStream out, Tile tile) throws IOException {
        DataOutputStream dout = new DataOutputStream(out);
        dout.writeInt(tile.width);
        dout.writeInt(tile.height);
        dout.writeInt(tile.data.length);
        dout.write(tile.data);
    }

    protected static Tile loadTile(InputStream in) throws IOException {
        DataInputStream din = new DataInputStream(in);
        int width = din.readInt();
        int height = din.readInt();
        int size = din.readInt();
        byte[] data = new byte[size];
        din.readFully(data);
        return new Tile(width, height, data);

    }

    @Override
    public Tile getTile(int x, int y, int zoom) {
        String key = getKey(x, y, zoom);
        Tile cached = getFromCache(key);
        if (cached != null) {
            return cached;
        } else {
            Tile tile = provider.getTile(x, y, zoom);
            if (tile == null) return null;
            setToCache(key, tile);
            return tile;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (!cache.isClosed()){
            cache.flush();
            cache.close();
        }
    }
}
