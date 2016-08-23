package cgeo.geocaching.maps.google.v2.TileProviders;

import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileProvider;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;


public class MapyCzTileProvider implements TileProvider {

    protected static final String TYPE_DEFAULT = "base-m";
    public static final int TILE_WIDTH = 256;
    public static final int TILE_HEIGHT = 256;

    protected final String type;


    public MapyCzTileProvider(String type) {
        this.type = type;
    }

    public MapyCzTileProvider() {
        this(TYPE_DEFAULT);
    }

    protected URL getURL(int x, int y, int zoom)
    {
        try {
            return new URL("https://m" + ((x+y) % 4 + 1) + ".mapserver.mapy.cz/" + type + "/" + zoom + '-' + x + '-' + y);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Tile getTile(int x, int y, int zoom) {
        URL url = getURL(x,y,zoom);
        try {
            URLConnection conn = url.openConnection();
            if (conn instanceof HttpURLConnection) {
                HttpURLConnection httpConn = (HttpURLConnection) conn;
                httpConn.setInstanceFollowRedirects(false);
                int code = httpConn.getResponseCode();
                if (code >= 300 && code < 500) {
                    return NO_TILE;
                }
            }
            byte[] data = IOUtils.toByteArray(conn.getInputStream());
            return new Tile(TILE_WIDTH, TILE_HEIGHT, data);
        } catch (IOException ex) {
            return null; // inspired by google maps's UrlTileProvider, probably will initialize exponential back off
        }
    }


}
