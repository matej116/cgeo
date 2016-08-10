package cgeo.geocaching.maps.google.v2;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;
import cgeo.geocaching.maps.AbstractMapProvider;
import cgeo.geocaching.maps.AbstractMapSource;
import cgeo.geocaching.maps.interfaces.MapItemFactory;
import cgeo.geocaching.maps.interfaces.MapProvider;
import cgeo.geocaching.maps.interfaces.MapSource;

import android.app.Activity;
import android.content.res.Resources;

public final class GoogleMapProvider extends AbstractMapProvider {

    public static final String GOOGLE_MAP_ID = "GOOGLE_MAP";
    public static final String GOOGLE_SATELLITE_ID = "GOOGLE_SATELLITE";
    public static final String MAPYCZ_ID = "MAPY.CZ";

    private final MapItemFactory mapItemFactory;

    private GoogleMapProvider() {
        final Resources resources = CgeoApplication.getInstance().getResources();

        registerMapSource(new GoogleMapSource(this, resources.getString(R.string.map_source_google_map)));
        registerMapSource(new GoogleSatelliteSource(this, resources.getString(R.string.map_source_google_satellite)));

        registerMapSource(new MapyCzSource(this, resources.getString(R.string.map_source_google_mapy_cz)));
        registerMapSource(new MapyCzSource(this, resources.getString(R.string.map_source_google_mapy_cz_bing), "bing"));
        registerMapSource(new MapyCzSource(this, resources.getString(R.string.map_source_google_mapy_cz_tourist), "wturist-m"));
        registerMapSource(new MapyCzSource(this, resources.getString(R.string.map_source_google_mapy_cz_custom), null));

        mapItemFactory = new GoogleMapItemFactory();
    }

    private static class Holder {
        private static final GoogleMapProvider INSTANCE = new GoogleMapProvider();
    }

    public static GoogleMapProvider getInstance() {
        return Holder.INSTANCE;
    }

    public static boolean isSatelliteSource(final MapSource mapSource) {
        return mapSource instanceof GoogleSatelliteSource;
    }

    @Override
    public Class<? extends Activity> getMapClass() {
        return GoogleMapActivity.class;
    }

    @Override
    public int getMapViewId() {
        return R.id.map;
    }

    @Override
    public int getMapLayoutId() {
        return R.layout.map_google;
    }

    @Override
    public MapItemFactory getMapItemFactory() {
        return mapItemFactory;
    }

    @Override
    public boolean isSameActivity(final MapSource source1, final MapSource source2) {
        return true;
    }

    private abstract static class AbstractGoogleMapSource extends AbstractMapSource {

        protected AbstractGoogleMapSource(final String id, final MapProvider mapProvider, final String name) {
            super(id, mapProvider, name);
        }

    }

    private static final class GoogleMapSource extends AbstractGoogleMapSource {

        GoogleMapSource(final MapProvider mapProvider, final String name) {
            super(GOOGLE_MAP_ID, mapProvider, name);
        }

    }

    private static final class GoogleSatelliteSource extends AbstractGoogleMapSource {

        GoogleSatelliteSource(final MapProvider mapProvider, final String name) {
            super(GOOGLE_SATELLITE_ID, mapProvider, name);
        }

    }

    public static final class MapyCzSource extends AbstractGoogleMapSource {

        private final String type;


        MapyCzSource(final MapProvider mapProvider, final String name, String type) {
            super(MAPYCZ_ID + "_" + type, mapProvider, name);
            this.type = type;
        }

        MapyCzSource(final MapProvider mapProvider, final String name) {
            this(mapProvider, name, "base-m");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        public String getType() {
            return type;
        }

        public MapyCzSource derive(String type) {
            return new MapyCzSource(this.getMapProvider(), this.getName(), type);
        }
    }

}
