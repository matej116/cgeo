package cgeo.geocaching.utils;

import org.eclipse.jdt.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;

final public class IOUtils {

    private IOUtils() {
        // utility class
    }

    public static void closeQuietly(@Nullable final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final IOException e) {
                Log.w("closeQuietly: unable to close " + closeable, e);
            }
        }
    }

}
