package cgeo.geocaching.maps.google.v2;


import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.apache.commons.lang3.builder.HashCodeBuilder;


/**
 * simple wrapper of googlemaps *Options, implements equals() and hashCode()
 */
public class MapObjectOptions {

    /**
     * one of *Options
     */
    protected final Object options;

    /**
     * cached hashCode
     */
    int hashCode = 0;

    public MapObjectOptions(Object options) {
        checkInstance(options);
        this.options = options;
    }

    protected static void checkInstance(Object options) {
        if (options == null) {
            throw new NullPointerException("What?");
        }
        if (!(
                options instanceof MarkerOptions ||
                        options instanceof CircleOptions ||
                        options instanceof PolylineOptions
        )) {
            throw new IllegalArgumentException("Options not valid google maps object options, instance of " + options.getClass().getName());
        }
    }

    public static MapObjectOptions from(Object opts) {
        if (opts instanceof MapObjectOptions) {
            return (MapObjectOptions) opts;
        }
        return new MapObjectOptions(opts);
    }

    protected static boolean equalsOptions(Object opts1, Object opts2) {
        if (opts1.getClass() != opts2.getClass()) return false;
        if (opts1 instanceof MarkerOptions) {
            return equals((MarkerOptions) opts1, (MarkerOptions) opts2);
        } else if (opts1 instanceof CircleOptions) {
            return equals((CircleOptions) opts1, (CircleOptions) opts2);
        } else if (opts1 instanceof PolylineOptions) {
            return equals((PolylineOptions) opts1, (PolylineOptions) opts2);
        } else {
            return false;
        }
    }

    protected static boolean objEquals(Object a, Object b) {
        return (a == null) ? (b == null) : a.equals(b);
    }

    protected static boolean equals(MarkerOptions a, MarkerOptions b) {
        return a.getAlpha() == b.getAlpha() &&
                a.getAnchorU() == b.getAnchorU() &&
                a.getAnchorV() == b.getAnchorV() &&
                objEquals(a.getIcon(), b.getIcon()) &&
                a.getInfoWindowAnchorU() == b.getInfoWindowAnchorU() &&
                a.getInfoWindowAnchorV() == b.getInfoWindowAnchorV() &&
                objEquals(a.getPosition(), b.getPosition()) &&
                a.getRotation() == b.getRotation() &&
                objEquals(a.getSnippet(), b.getSnippet()) &&
                objEquals(a.getTitle(), b.getTitle()) &&
                a.getZIndex() == b.getZIndex() &&
                a.isDraggable() == b.isDraggable() &&
                a.isFlat() == b.isFlat() &&
                a.isVisible() == b.isVisible();
    }

    protected static boolean equals(CircleOptions a, CircleOptions b) {
        return a.getZIndex() == b.getZIndex() &&
                objEquals(a.getCenter(), b.getCenter()) &&
                a.getFillColor() == b.getFillColor() &&
                a.getRadius() == b.getRadius() &&
                a.getStrokeColor() == b.getStrokeColor() &&
                a.getStrokeWidth() == b.getStrokeWidth() &&
                a.isVisible() == b.isVisible() &&
                a.isClickable() == b.isClickable();
    }

    protected static boolean equals(PolylineOptions a, PolylineOptions b) {
        return a.getZIndex() == b.getZIndex() &&
                a.getColor() == b.getZIndex() &&
                objEquals(a.getPoints(), b.getPoints()) &&
                a.getWidth() == b.getWidth() &&
                a.isClickable() == b.isClickable() &&
                a.isVisible() == b.isVisible() &&
                a.isGeodesic() == b.isGeodesic();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return equalsOptions(options, ((MapObjectOptions) o).options);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            if (options instanceof MarkerOptions) {
                return hashCode = hashCode((MarkerOptions) options);
            } else if (options instanceof CircleOptions) {
                return hashCode = hashCode((CircleOptions) options);
            } else if (options instanceof PolylineOptions) {
                return hashCode = hashCode((PolylineOptions) options);
            } else {
                throw new IllegalStateException();
            }
        }
        return hashCode;
    }

    private int hashCode(PolylineOptions options) {
        return new HashCodeBuilder()
                .append(options.getZIndex())
                .append(options.getColor())
                .append(options.getPoints())
                .append(options.getWidth())
                .append(options.isClickable())
                .append(options.isGeodesic())
                .append(options.isVisible())
                .toHashCode();
    }

    private int hashCode(CircleOptions options) {
        return new HashCodeBuilder()
                .append(options.getZIndex())
                .append(options.getCenter())
                .append(options.getFillColor())
                .append(options.getRadius())
                .append(options.getStrokeColor())
                .append(options.getStrokeWidth())
                .append(options.isVisible())
                .append(options.isClickable())
                .toHashCode();
    }

    private int hashCode(MarkerOptions options) {
        return new HashCodeBuilder()
                .append(options.getAlpha())
                .append(options.getAnchorU())
                .append(options.getAnchorV())
                .append(options.getIcon())
                .append(options.getInfoWindowAnchorU())
                .append(options.getInfoWindowAnchorV())
                .append(options.getPosition())
                .append(options.getRotation())
                .append(options.getSnippet())
                .append(options.getTitle())
                .append(options.getZIndex())
                .append(options.isVisible())
                .append(options.isDraggable())
                .append(options.isFlat())
                .toHashCode();
    }

    public Object addToGoogleMap(GoogleMap googleMap) {
        if (options instanceof MarkerOptions) {
            return googleMap.addMarker((MarkerOptions) options);
        } else if (options instanceof CircleOptions) {
            return googleMap.addCircle((CircleOptions) options);
        } else if (options instanceof PolylineOptions) {
            return googleMap.addPolyline((PolylineOptions) options);
        } else {
            throw new IllegalStateException("Invalid options type, check should be performed constructor, this should not happpen");
        }
    }

    public Object getOptions() {
        return options;
    }
}
