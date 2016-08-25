package cgeo.geocaching.ui;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ProgressBar;

import cgeo.geocaching.activity.AbstractViewPagerActivity.PageViewCreator;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * View creator which destroys the created view on every {@link #notifyDataSetChanged()}.
 * View is lazy loaded if possible, since creating View can take some time (100ms+)
 *
 */
public abstract class AbstractCachingPageViewCreator<ViewClass extends View> implements PageViewCreator {

    private View view;

    private ViewClass dispatchedView;

    private boolean firstTime = true;

    @Override
    public final void notifyDataSetChanged() {
        view = null;
    }

    @Override
    public final View getView(final ViewGroup parentView) {
        if (view == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && firstTime) {
                // create lazy view only for the first time, when Activity is first opened,
                // subsequent calls are faster (due the Android caching?) and creating lazy view would
                // cause nasty UI lag (showing loading screen if not expected for couple of milliseconds)
                view = getLazyView(parentView);
            } else {
                view = dispatchedView = getDispatchedView(parentView);
            }
            firstTime = false;
        }
        return view;
    }

    /** allow descendant to access (read only) view created by previously called getDispatchedView() from getView(ViewFroup parentView) */
    protected ViewClass getView() {
        return dispatchedView;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private View getLazyView(final ViewGroup parentView) {
        final GridLayout view = new GridLayout(parentView.getContext());
        view.setColumnCount(1);

        ProgressBar loading = new ProgressBar(parentView.getContext());
        loading.setIndeterminate(true);

        view.addView(loading);

        // postpone creating dispatched view for later
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                View v = dispatchedView = getDispatchedView(view);
                view.removeAllViews();
                view.addView(v);
            }
        });

        return view;
    }

    @Override
    @SuppressFBWarnings("USM_USELESS_ABSTRACT_METHOD")
    public abstract ViewClass getDispatchedView(final ViewGroup parentView);

    /**
     * Gets the state of the view but returns an empty state if not overridden
     *
     * @return empty bundle
     */
    @Nullable
    @Override
    public Bundle getViewState() {
        return new Bundle();
    }

    /**
     * Restores the state of the view but just returns if not overridden.
     */
    @Override
    public void setViewState(@NonNull final Bundle state) {
    }
}
