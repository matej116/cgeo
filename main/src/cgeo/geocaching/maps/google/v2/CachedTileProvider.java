package cgeo.geocaching.maps.google.v2;

import android.util.LruCache;

import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileProvider;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import cgeo.geocaching.utils.LeastRecentlyUsedMap;

public class CachedTileProvider implements TileProvider {

    protected TileProvider provider;

    protected LeastRecentlyUsedMap<int[], TileData> cache = new LeastRecentlyUsedMap.LruCache<>(500);


    public CachedTileProvider(TileProvider provider) {
        this.provider = provider;
    }

    protected static int[] getKey(int x, int y, int zoom)
    {
        return new int[] {x, y, zoom};
    }

    public void cache(int x, int y, int zoom)
    {
        int[] key = getKey(x, y, zoom);
        if (cache.containsKey(key)) return;
        Tile tile = provider.getTile(x, y, zoom);
        if (tile == null) return;
        cache.put(key, TileData.fromTile(tile));
    }

    @Override
    public Tile getTile(int x, int y, int zoom) {
        int[] key = getKey(x, y, zoom);
        TileData cached = cache.get(key);
        if (cached != null) {
            return cached.toTile();
        } else {
            Tile tile = provider.getTile(x, y, zoom);
            if (tile == null) return null;
            TileData data = TileData.fromTile(tile);
            cache.put(key, data);
            return tile;
        }
    }

    public void cacheArea(int fromX, int toX, int fromY, int toY, int zoomFrom, int zoomTo)
    {
        if (fromX > toX || fromY > toY || zoomFrom > zoomTo) {
            throw new IllegalArgumentException();
        }
        for (int x = fromX; x < toX; x++) {
            for (int y = fromY; y < toY; y++) {
                cache(x, y, zoomFrom);
            }
        }
        if (zoomFrom < zoomTo) {
            cacheArea(fromX * 2, toX * 2, fromY * 2, toY * 2, zoomFrom + 1, zoomTo);
        }
    }

    public void save(ObjectOutputStream out) throws IOException {
        out.writeObject(cache);
    }

    public void load(ObjectInputStream in) throws IOException, ClassNotFoundException {
        this.cache = (LeastRecentlyUsedMap<int[], TileData>) in.readObject();
    }

    static class TileData {

        final int width, height;

        final byte[] data;

        public TileData(int width, int height, byte[] data) {
            this.width = width;
            this.height = height;
            this.data = data;
        }

        static TileData fromTile(Tile tile)
        {
            return new TileData(tile.width, tile.height, tile.data);
        }

        Tile toTile()
        {
            return new Tile(width, height, data);
        }
    }
}
