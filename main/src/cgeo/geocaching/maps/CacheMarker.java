package cgeo.geocaching.maps;


import android.graphics.drawable.Drawable;


public class CacheMarker {

    private int hashCode;
    protected final Drawable drawable;

    public CacheMarker(int hashCode, Drawable drawable) {
        this.hashCode = hashCode;
        this.drawable = drawable;
    }

    /**
     * fallback contructor
     * @param drawable
     */
    public CacheMarker(Drawable drawable) {
        this(0, drawable);
    }

    public Drawable getDrawable() {
        return drawable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CacheMarker that = (CacheMarker) o;

        if (hashCode == 0) {
            return this.drawable.equals(that.drawable);
        } else {
            return hashCode == that.hashCode;
        }
    }

    @Override
    public int hashCode() {
        return hashCode == 0 ? drawable.hashCode() : hashCode;
    }
}

