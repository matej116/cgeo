package cgeo.geocaching;

import cgeo.geocaching.activity.AbstractActivity;
import cgeo.geocaching.activity.AbstractListActivity;
import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.activity.FilteredActivity;
import cgeo.geocaching.activity.Progress;
import cgeo.geocaching.apps.cachelist.CacheListApp;
import cgeo.geocaching.apps.cachelist.CacheListAppUtils;
import cgeo.geocaching.apps.cachelist.CacheListApps;
import cgeo.geocaching.apps.cachelist.ListNavigationSelectionActionProvider;
import cgeo.geocaching.apps.navi.NavigationAppFactory;
import cgeo.geocaching.command.AbstractCachesCommand;
import cgeo.geocaching.command.CopyToListCommand;
import cgeo.geocaching.command.DeleteListCommand;
import cgeo.geocaching.command.MakeListUniqueCommand;
import cgeo.geocaching.command.MoveToListAndRemoveFromOthersCommand;
import cgeo.geocaching.command.MoveToListCommand;
import cgeo.geocaching.command.RenameListCommand;
import cgeo.geocaching.compatibility.Compatibility;
import cgeo.geocaching.connector.gc.PocketQueryListActivity;
import cgeo.geocaching.enumerations.CacheListType;
import cgeo.geocaching.enumerations.CacheType;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.StatusCode;
import cgeo.geocaching.export.BatchUploadModifiedCoordinates;
import cgeo.geocaching.export.FieldNoteExport;
import cgeo.geocaching.export.GpxExport;
import cgeo.geocaching.export.PersonalNoteExport;
import cgeo.geocaching.files.GPXImporter;
import cgeo.geocaching.files.GpxFileListActivity;
import cgeo.geocaching.filter.FilterActivity;
import cgeo.geocaching.filter.IFilter;
import cgeo.geocaching.list.AbstractList;
import cgeo.geocaching.list.ListMarker;
import cgeo.geocaching.list.ListNameMemento;
import cgeo.geocaching.list.PseudoList;
import cgeo.geocaching.list.StoredList;
import cgeo.geocaching.loaders.AbstractSearchLoader;
import cgeo.geocaching.loaders.AbstractSearchLoader.CacheListLoaderType;
import cgeo.geocaching.loaders.CoordsGeocacheListLoader;
import cgeo.geocaching.loaders.FinderGeocacheListLoader;
import cgeo.geocaching.loaders.HistoryGeocacheListLoader;
import cgeo.geocaching.loaders.KeywordGeocacheListLoader;
import cgeo.geocaching.loaders.NextPageGeocacheListLoader;
import cgeo.geocaching.loaders.OfflineGeocacheListLoader;
import cgeo.geocaching.loaders.OwnerGeocacheListLoader;
import cgeo.geocaching.loaders.PocketGeocacheListLoader;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.log.LoggingUI;
import cgeo.geocaching.maps.DefaultMap;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.models.PocketQuery;
import cgeo.geocaching.network.Cookies;
import cgeo.geocaching.network.DownloadProgress;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.network.Send2CgeoDownloader;
import cgeo.geocaching.permission.PermissionHandler;
import cgeo.geocaching.permission.PermissionRequestContext;
import cgeo.geocaching.permission.RestartLocationPermissionGrantedCallback;
import cgeo.geocaching.sensors.GeoData;
import cgeo.geocaching.sensors.GeoDirHandler;
import cgeo.geocaching.sensors.Sensors;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.settings.SettingsActivity;
import cgeo.geocaching.sorting.CacheComparator;
import cgeo.geocaching.sorting.SortActionProvider;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.ui.CacheListAdapter;
import cgeo.geocaching.ui.WeakReferenceHandler;
import cgeo.geocaching.ui.dialog.Dialogs;
import cgeo.geocaching.utils.AndroidRxUtils;
import cgeo.geocaching.utils.AngleUtils;
import cgeo.geocaching.utils.CalendarUtils;
import cgeo.geocaching.utils.DisposableHandler;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.MapMarkerUtils;
import cgeo.geocaching.utils.functions.Action1;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class CacheListActivity extends AbstractListActivity implements FilteredActivity, LoaderManager.LoaderCallbacks<SearchResult> {

    private static final int MAX_LIST_ITEMS = 1000;
    private static final int REFRESH_WARNING_THRESHOLD = 100;

    private static final int REQUEST_CODE_IMPORT_GPX = 1;
    private static final int REQUEST_CODE_RESTART = 2;
    private static final int REQUEST_CODE_IMPORT_PQ = 3;

    private static final String STATE_FILTER = "currentFilter";
    private static final String STATE_INVERSE_SORT = "currentInverseSort";
    private static final String STATE_LIST_TYPE = "currentListType";
    private static final String STATE_LIST_ID = "currentListId";
    private static final String STATE_MARKER_ID = "currentMarkerId";

    private static final String BUNDLE_ACTION_KEY = "afterLoadAction";

    private CacheListType type = null;
    private Geopoint coords = null;
    private SearchResult search = null;
    /** The list of shown caches shared with Adapter. Don't manipulate outside of main thread only with Handler */
    private final List<Geocache> cacheList = new ArrayList<>();
    private CacheListAdapter adapter = null;
    private View listFooter = null;
    private TextView listFooterText = null;
    private final Progress progress = new Progress();
    private String title = "";
    private int detailTotal = 0;
    private final AtomicInteger detailProgress = new AtomicInteger(0);
    private long detailProgressTime = 0L;
    private int listId = StoredList.TEMPORARY_LIST.id; // Only meaningful for the OFFLINE type
    private int markerId = ListMarker.NO_MARKER.markerId;
    private final GeoDirHandler geoDirHandler = new GeoDirHandler() {

        @Override
        public void updateDirection(final float direction) {
            if (Settings.isLiveList()) {
                adapter.setActualHeading(AngleUtils.getDirectionNow(direction));
            }
        }

        @Override
        public void updateGeoData(final GeoData geoData) {
            adapter.setActualCoordinates(geoData.getCoords());
        }

    };
    private ContextMenuInfo lastMenuInfo;
    private String contextMenuGeocode = "";
    private final CompositeDisposable resumeDisposables = new CompositeDisposable();
    private final ListNameMemento listNameMemento = new ListNameMemento();

    private final Handler loadCachesHandler = new LoadCachesHandler(this);
    private final DisposableHandler clearOfflineLogsHandler = new ClearOfflineLogsHandler(this);
    private final Handler importGpxAttachementFinishedHandler = new ImportGpxAttachementFinishedHandler(this);

    private AbstractSearchLoader currentLoader;

    private static class LoadCachesHandler extends WeakReferenceHandler<CacheListActivity> {

        protected LoadCachesHandler(final CacheListActivity activity) {
            super(activity);
        }

        @Override
        public void handleMessage(final Message msg) {
            final CacheListActivity activity = getReference();
            if (activity == null) {
                return;
            }
            activity.handleCachesLoaded();
        }
    }

    // FIXME: This method has mostly been replaced by the loaders. But it still contains a license agreement check.
    public void handleCachesLoaded() {
        try {
            updateAdapter();

            updateTitle();

            showFooterMoreCaches();

            if (search != null && search.getError() == StatusCode.UNAPPROVED_LICENSE) {
                showLicenseConfirmationDialog();
            } else if (search != null && search.getError() != StatusCode.NO_ERROR) {
                showToast(res.getString(R.string.err_download_fail) + ' ' + search.getError().getErrorString(res) + '.');

                hideLoading();
                showProgress(false);

                finish();
                return;
            }

            setAdapterCurrentCoordinates(false);
        } catch (final Exception e) {
            showToast(res.getString(R.string.err_detail_cache_find_any));
            Log.e("CacheListActivity.loadCachesHandler", e);

            hideLoading();
            showProgress(false);

            finish();
            return;
        }

        try {
            hideLoading();
            showProgress(false);
        } catch (final Exception e2) {
            Log.e("CacheListActivity.loadCachesHandler.2", e2);
        }

        adapter.setSelectMode(false);
    }

    private void showLicenseConfirmationDialog() {
        final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(res.getString(R.string.license));
        dialog.setMessage(res.getString(R.string.err_license));
        dialog.setCancelable(true);
        dialog.setNegativeButton(res.getString(R.string.license_dismiss), (dialog1, id) -> {
            Cookies.clearCookies();
            dialog1.cancel();
        });
        dialog.setPositiveButton(res.getString(R.string.license_show), (dialog12, id) -> {
            Cookies.clearCookies();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.geocaching.com/software/agreement.aspx?ID=0")));
        });

        final AlertDialog alert = dialog.create();
        alert.show();
    }

    /**
     * Loads the caches and fills the {@link #cacheList} according to {@link #search} content.
     *
     * If {@link #search} is {@code null}, this does nothing.
     */

    private void replaceCacheListFromSearch() {
        if (search != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cacheList.clear();

                    // The database search was moved into the UI call intentionally. If this is done before the runOnUIThread,
                    // then we have 2 sets of caches in memory. This can lead to OOM for huge cache lists.
                    final Set<Geocache> cachesFromSearchResult = search.getCachesFromSearchResult(LoadFlags.LOAD_CACHE_OR_DB);

                    cacheList.addAll(cachesFromSearchResult);
                    adapter.reFilter();
                    updateTitle();
                    showFooterMoreCaches();
                }
            });
        }
    }

    private static String getCacheNumberString(final Resources res, final int count) {
        return res.getQuantityString(R.plurals.cache_counts, count, count);
    }

    protected void updateTitle() {
        setTitle(title);
        adapter.setCurrentListTitle(title);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(getCurrentSubtitle());
        }
        refreshSpinnerAdapter();
    }

    private static final class LoadDetailsHandler extends DisposableHandler {
        private final WeakReference<CacheListActivity> activityRef;

        LoadDetailsHandler(final CacheListActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        protected void handleDispose() {
            final CacheListActivity activity = activityRef.get();
            if (activity != null) {
                super.handleDispose();
                activity.replaceCacheListFromSearch();
            }
        }

        @Override
        public void handleRegularMessage(final Message msg) {
            final CacheListActivity activity = activityRef.get();
            if (activity != null) {
                activity.updateAdapter();

                final Progress progress = activity.progress;
                if (msg.what == DownloadProgress.MSG_LOADED) {
                    ((Geocache) msg.obj).setStatusChecked(false);

                    final CacheListAdapter adapter = activity.adapter;
                    adapter.notifyDataSetChanged();

                    final int dp = activity.detailProgress.get();
                    final int secondsElapsed = (int) ((System.currentTimeMillis() - activity.detailProgressTime) / 1000);
                    final int minutesRemaining = (activity.detailTotal - dp) * secondsElapsed / (dp > 0 ? dp : 1) / 60;

                    final Resources res = activity.res;
                    progress.setProgress(dp);
                    if (minutesRemaining < 1) {
                        progress.setMessage(res.getString(R.string.caches_downloading) + " " + res.getString(R.string.caches_eta_ltm));
                    } else {
                        progress.setMessage(res.getString(R.string.caches_downloading) + " " + res.getQuantityString(R.plurals.caches_eta_mins, minutesRemaining, minutesRemaining));
                    }
                } else {
                    new AsyncTask<Void, Void, Set<Geocache>>() {
                        @Override
                        protected Set<Geocache> doInBackground(final Void... params) {
                            final SearchResult search = activity.search;
                            return search != null ? search.getCachesFromSearchResult(LoadFlags.LOAD_CACHE_OR_DB) : null;
                        }

                        @Override
                        protected void onPostExecute(final Set<Geocache> result) {
                            if (CollectionUtils.isNotEmpty(result)) {
                                final List<Geocache> cacheList = activity.cacheList;
                                cacheList.clear();
                                cacheList.addAll(result);
                                activity.adapter.reFilter();
                            }
                            activity.setAdapterCurrentCoordinates(false);

                            activity.showProgress(false);
                            progress.dismiss();
                        }
                    }.execute();
                }
            }
        }
    }

    /**
     * TODO Possibly parts should be a Thread not a Handler
     */
    private static final class DownloadFromWebHandler extends DisposableHandler {
        private final WeakReference<CacheListActivity> activityRef;

        DownloadFromWebHandler(final CacheListActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        public void handleRegularMessage(final Message msg) {
            final CacheListActivity activity = activityRef.get();
            if (activity != null) {
                activity.updateAdapter();

                final CacheListAdapter adapter = activity.adapter;
                adapter.notifyDataSetChanged();

                final Progress progress = activity.progress;
                switch (msg.what) {
                    case DownloadProgress.MSG_WAITING:  //no caches
                        progress.setMessage(activity.res.getString(R.string.web_import_waiting));
                        break;
                    case DownloadProgress.MSG_LOADING: {  //cache downloading
                        final Resources res = activity.res;
                        progress.setMessage(res.getString(R.string.web_downloading) + ' ' + msg.obj + res.getString(R.string.ellipsis));
                        break;
                    }
                    case DownloadProgress.MSG_LOADED: {  //Cache downloaded
                        final Resources res = activity.res;
                        progress.setMessage(res.getString(R.string.web_downloaded) + ' ' + msg.obj + res.getString(R.string.ellipsis));
                        activity.refreshCurrentList();
                        break;
                    }
                    case DownloadProgress.MSG_SERVER_FAIL:
                        progress.dismiss();
                        activity.showToast(activity.res.getString(R.string.sendToCgeo_download_fail));
                        activity.finish();
                        break;
                    case DownloadProgress.MSG_NO_REGISTRATION:
                        progress.dismiss();
                        activity.showToast(activity.res.getString(R.string.sendToCgeo_no_registration));
                        activity.finish();
                        break;
                    default:  // MSG_DONE
                        adapter.setSelectMode(false);
                        activity.replaceCacheListFromSearch();
                        progress.dismiss();
                        break;
                }
            }
        }
    }

    private static final class ClearOfflineLogsHandler extends DisposableHandler {
        private final WeakReference<CacheListActivity> activityRef;

        ClearOfflineLogsHandler(final CacheListActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        public void handleRegularMessage(final Message msg) {
            final CacheListActivity activity = activityRef.get();
            if (activity != null) {
                activity.adapter.setSelectMode(false);

                activity.refreshCurrentList();

                activity.replaceCacheListFromSearch();

                activity.progress.dismiss();
            }
        }
    }

    private static final class ImportGpxAttachementFinishedHandler extends WeakReferenceHandler<CacheListActivity> {

        ImportGpxAttachementFinishedHandler(final CacheListActivity activity) {
            super(activity);
        }

        @Override
        public void handleMessage(final Message msg) {
            final CacheListActivity activity = getReference();
            if (activity != null) {
                activity.refreshCurrentList();
            }
        }
    }

    public CacheListActivity() {
        super(true);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme();

        setContentView(R.layout.cacheslist_activity);

        // get parameters
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            type = Intents.getListType(getIntent());
            coords = extras.getParcelable(Intents.EXTRA_COORDS);
        } else {
            extras = new Bundle();
        }
        if (isInvokedFromAttachment()) {
            type = CacheListType.OFFLINE;
            if (coords == null) {
                coords = Geopoint.ZERO;
            }
        }
        if (type == CacheListType.NEAREST) {
            coords = Sensors.getInstance().currentGeo().getCoords();
        }

        setTitle(title);

        // Check whether we're recreating a previously destroyed instance
        if (savedInstanceState != null) {
            // Restore value of members from saved state
            currentFilter = savedInstanceState.getParcelable(STATE_FILTER);
            currentInverseSort = savedInstanceState.getBoolean(STATE_INVERSE_SORT);
            type = CacheListType.values()[savedInstanceState.getInt(STATE_LIST_TYPE, type.ordinal())];
            listId = savedInstanceState.getInt(STATE_LIST_ID);
            markerId = savedInstanceState.getInt(STATE_MARKER_ID);
        }

        initAdapter();

        prepareFilterBar();

        if (type.canSwitch) {
            initActionBarSpinner();
        }

        currentLoader = (AbstractSearchLoader) getSupportLoaderManager().initLoader(type.getLoaderId(), extras, this);

        // init
        if (CollectionUtils.isNotEmpty(cacheList)) {
            // currentLoader can be null if this activity is created from a map, as onCreateLoader() will return null.
            if (currentLoader != null && currentLoader.isStarted()) {
                showFooterLoadingCaches();
            } else {
                showFooterMoreCaches();
            }
        }

        if (isInvokedFromAttachment()) {
            listNameMemento.rememberTerm(extras.getString(Intents.EXTRA_NAME));
            importGpxAttachement();
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);

        // Save the current Filter
        savedInstanceState.putParcelable(STATE_FILTER, currentFilter);
        savedInstanceState.putBoolean(STATE_INVERSE_SORT, adapter.getInverseSort());
        savedInstanceState.putInt(STATE_LIST_TYPE, type.ordinal());
        savedInstanceState.putInt(STATE_LIST_ID, listId);
        savedInstanceState.putInt(STATE_MARKER_ID, markerId);
    }

    /**
     * Action bar spinner adapter. {@code null} for list types that don't allow switching (search results, ...).
     */
    CacheListSpinnerAdapter mCacheListSpinnerAdapter;

    /**
     * remember current filter when switching between lists, so it can be re-applied afterwards
     */
    private IFilter currentFilter = null;
    private boolean currentInverseSort = false;

    private SortActionProvider sortProvider;

    private void initActionBarSpinner() {
        mCacheListSpinnerAdapter = new CacheListSpinnerAdapter(this, R.layout.support_simple_spinner_dropdown_item);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setListNavigationCallbacks(mCacheListSpinnerAdapter, new ActionBar.OnNavigationListener() {
                @Override
                public boolean onNavigationItemSelected(final int i, final long l) {
                    final int newListId = mCacheListSpinnerAdapter.getItem(i).id;
                    if (newListId != listId) {
                        switchListById(newListId);
                    }
                    return true;
                }
            });
        }
    }

    private void refreshSpinnerAdapter() {
        /* If the activity does not use the Spinner this will be null */
        if (mCacheListSpinnerAdapter == null) {
            return;
        }
        mCacheListSpinnerAdapter.clear();

        final AbstractList list = AbstractList.getListById(listId);

        for (final AbstractList l: StoredList.UserInterface.getMenuLists(false, PseudoList.NEW_LIST.id)) {
            mCacheListSpinnerAdapter.add(l);
        }

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSelectedNavigationItem(mCacheListSpinnerAdapter.getPosition(list));
        }
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (currentLoader != null && currentLoader.isLoading()) {
            showFooterLoadingCaches();
        }
    }

    private boolean isConcreteList() {
        return type == CacheListType.OFFLINE &&
                (listId == StoredList.STANDARD_LIST_ID || listId >= DataStore.customListIdOffset);
    }

    private boolean isInvokedFromAttachment() {
        final Intent intent = getIntent();
        return Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null;
    }

    private void importGpxAttachement() {
        new StoredList.UserInterface(this).promptForListSelection(R.string.gpx_import_select_list_title, new Action1<Integer>() {

            @Override
            public void call(final Integer listId) {
                new GPXImporter(CacheListActivity.this, listId, importGpxAttachementFinishedHandler).importGPX();
                switchListById(listId);
            }
        }, true, 0, listNameMemento);
    }

    @Override
    public void onResume() {
        super.onResume();

        // resume location access
        PermissionHandler.executeIfLocationPermissionGranted(this, new RestartLocationPermissionGrantedCallback(PermissionRequestContext.CacheListActivity) {

            @Override
            public void executeAfter() {
                resumeDisposables.add(geoDirHandler.start(GeoDirHandler.UPDATE_GEODATA | GeoDirHandler.UPDATE_DIRECTION | GeoDirHandler.LOW_POWER, 250, TimeUnit.MILLISECONDS));
            }
        });


        adapter.setSelectMode(false);
        setAdapterCurrentCoordinates(true);

        if (search != null) {
            replaceCacheListFromSearch();
            loadCachesHandler.sendEmptyMessage(0);
        }

        // refresh standard list if it has changed (new caches downloaded)
        if (type == CacheListType.OFFLINE && (listId >= StoredList.STANDARD_LIST_ID || listId == PseudoList.ALL_LIST.id) && search != null) {
            final SearchResult newSearch = DataStore.getBatchOfStoredCaches(coords, Settings.getCacheType(), listId);
            if (newSearch.getTotalCountGC() != search.getTotalCountGC()) {
                refreshCurrentList();
            }
        }

        // always refresh history, an offline log might have been deleted
        if (type == CacheListType.HISTORY) {
            new LastPositionHelper(this).refreshListAtLastPosition();
        }
    }

    private void setAdapterCurrentCoordinates(final boolean forceSort) {
        adapter.setActualCoordinates(Sensors.getInstance().currentGeo().getCoords());
        if (forceSort) {
            adapter.forceSort();
        }
    }

    @Override
    public void onPause() {
        resumeDisposables.clear();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.cache_list_options, menu);

        sortProvider = (SortActionProvider) MenuItemCompat.getActionProvider(menu.findItem(R.id.menu_sort));
        assert sortProvider != null;  // We set it in the XML file
        sortProvider.setSelection(adapter.getCacheComparator());
        sortProvider.setIsEventsOnly(adapter.isEventsOnly());
        sortProvider.setClickListener(new Action1<CacheComparator>() {

            @Override
            public void call(final CacheComparator selectedComparator) {
                final CacheComparator oldComparator = adapter.getCacheComparator();
                // selecting the same sorting twice will toggle the order
                if (selectedComparator != null && oldComparator != null && selectedComparator.getClass().equals(oldComparator.getClass())) {
                    adapter.toggleInverseSort();
                } else {
                    // always reset the inversion for a new sorting criteria
                    adapter.resetInverseSort();
                }
                setComparator(selectedComparator);
                sortProvider.setSelection(selectedComparator);
            }
        });

        ListNavigationSelectionActionProvider.initialize(menu.findItem(R.id.menu_cache_list_app_provider), new ListNavigationSelectionActionProvider.Callback() {

            @Override
            public void onListNavigationSelected(final CacheListApp app) {
                app.invoke(CacheListAppUtils.filterCoords(cacheList), CacheListActivity.this, getFilteredSearch());
            }
        });

        return true;
    }

    private static void setVisibleEnabled(final Menu menu, final int itemId, final boolean visible, final boolean enabled) {
        final MenuItem item = menu.findItem(itemId);
        item.setVisible(visible);
        item.setEnabled(enabled);
    }

    private static void setVisible(final Menu menu, final int itemId, final boolean visible) {
        menu.findItem(itemId).setVisible(visible);
    }

    private static void setEnabled(final Menu menu, final int itemId, final boolean enabled) {
        menu.findItem(itemId).setEnabled(enabled);
    }

    /**
     * Menu items which are not at all usable with the current list type should be hidden.
     * Menu items which are usable with the current list type but not in the current situation should be disabled.
     */
    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);

        final boolean isHistory = type == CacheListType.HISTORY;
        final boolean isOffline = type == CacheListType.OFFLINE;
        final boolean isEmpty = cacheList.isEmpty();
        final boolean isConcrete = isConcreteList();
        final boolean isNonDefaultList = isConcrete && listId != StoredList.STANDARD_LIST_ID;
        final List<CacheListApp> listNavigationApps = CacheListApps.getActiveApps();

        try {
            // toplevel menu items
            setEnabled(menu, R.id.menu_show_on_map, !isEmpty);
            setEnabled(menu, R.id.menu_filter, search != null && search.getCount() > 0);
            setVisibleEnabled(menu, R.id.menu_sort, !isHistory, !isEmpty);
            if (adapter.isSelectMode()) {
                menu.findItem(R.id.menu_switch_select_mode).setTitle(res.getString(R.string.caches_select_mode_exit))
                        .setIcon(R.drawable.ic_menu_clear_playlist);
            } else {
                menu.findItem(R.id.menu_switch_select_mode).setTitle(res.getString(R.string.caches_select_mode))
                        .setIcon(R.drawable.ic_menu_agenda);
            }
            setEnabled(menu, R.id.menu_switch_select_mode, !isEmpty);
            setVisible(menu, R.id.menu_invert_selection, adapter.isSelectMode()); // exception to the general rule: only show in select mode
            setVisibleEnabled(menu, R.id.menu_cache_list_app_provider, listNavigationApps.size() > 1, !isEmpty);
            setVisibleEnabled(menu, R.id.menu_cache_list_app, listNavigationApps.size() == 1, !isEmpty);

            // Manage Caches submenu
            setEnabled(menu, R.id.menu_refresh_stored, !isEmpty);
            if (!isOffline && !isHistory) {
                menu.findItem(R.id.menu_refresh_stored).setTitle(R.string.caches_store_offline);
            }
            setVisibleEnabled(menu, R.id.menu_move_to_list, isHistory || isOffline, !isEmpty);
            setVisibleEnabled(menu, R.id.menu_copy_to_list, isHistory || isOffline, !isEmpty);
            setVisibleEnabled(menu, R.id.menu_drop_caches, isHistory || containsStoredCaches(), !isEmpty);
            setVisibleEnabled(menu, R.id.menu_delete_events, isConcrete, !isEmpty && containsPastEvents());
            setVisibleEnabled(menu, R.id.menu_clear_offline_logs, isHistory || isOffline, !isEmpty && containsOfflineLogs());
            setVisibleEnabled(menu, R.id.menu_remove_from_history, isHistory, !isEmpty);
            setMenuItemLabel(menu, R.id.menu_remove_from_history, R.string.cache_remove_from_history, R.string.cache_clear_history);
            if (isOffline || type == CacheListType.HISTORY) { // only offline list
                setMenuItemLabel(menu, R.id.menu_drop_caches, R.string.caches_remove_selected, R.string.caches_remove_all);
                setMenuItemLabel(menu, R.id.menu_refresh_stored, R.string.caches_refresh_selected, R.string.caches_refresh_all);
                setMenuItemLabel(menu, R.id.menu_move_to_list, R.string.caches_move_selected, R.string.caches_move_all);
                setMenuItemLabel(menu, R.id.menu_copy_to_list, R.string.caches_copy_selected, R.string.caches_copy_all);
            } else { // search and global list (all other than offline and history)
                setMenuItemLabel(menu, R.id.menu_refresh_stored, R.string.caches_store_selected, R.string.caches_store_offline);
            }

            // Manage Lists submenu
            setVisible(menu, R.id.menu_lists, isOffline);
            setVisible(menu, R.id.menu_drop_list, isNonDefaultList);
            setVisible(menu, R.id.menu_rename_list, isNonDefaultList);
            setVisibleEnabled(menu, R.id.menu_make_list_unique, listId != PseudoList.ALL_LIST.id, !isEmpty);
            setVisible(menu, R.id.menu_set_listmarker, isNonDefaultList);

            // Import submenu
            setVisible(menu, R.id.menu_import, isOffline && listId != PseudoList.ALL_LIST.id);
            setEnabled(menu, R.id.menu_import_android, Compatibility.isStorageAccessFrameworkAvailable());
            setEnabled(menu, R.id.menu_import_pq, Settings.isGCConnectorActive() && Settings.isGCPremiumMember());

            // Export
            setVisibleEnabled(menu, R.id.menu_export, isHistory || isOffline, !isEmpty);
        } catch (final RuntimeException e) {
            Log.e("CacheListActivity.onPrepareOptionsMenu", e);
        }

        return true;
    }

    private boolean containsStoredCaches() {
        for (final Geocache cache : adapter.getCheckedOrAllCaches()) {
            if (cache.isOffline()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPastEvents() {
        for (final Geocache cache : adapter.getCheckedOrAllCaches()) {
            if (CalendarUtils.isPastEvent(cache)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsOfflineLogs() {
        for (final Geocache cache : adapter.getCheckedOrAllCaches()) {
            if (cache.isLogOffline()) {
                return true;
            }
        }
        return false;
    }

    private void setMenuItemLabel(final Menu menu, final int menuId, @StringRes final int resIdSelection, @StringRes final int resId) {
        final MenuItem menuItem = menu.findItem(menuId);
        if (menuItem == null) {
            return;
        }
        final boolean hasSelection = adapter != null && adapter.getCheckedCount() > 0;
        if (hasSelection) {
            menuItem.setTitle(res.getString(resIdSelection) + " (" + adapter.getCheckedCount() + ")");
        } else {
            menuItem.setTitle(res.getString(resId));
        }
    }

    private class ListMarkerAdapter extends ArrayAdapter<ListMarker> {

        private ArrayList<ListMarker> items;
        private Context context;

        ListMarkerAdapter(final Context context, final int viewResId, final ArrayList<ListMarker> items) {
            super(context, viewResId, items);
            this.items = items;
            this.context = context;
        }

        public View getView(final int position, final View convertView, final ViewGroup parent) {
            View v = convertView;

            if (v == null) {
                final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = inflater.inflate(R.layout.cachelist_listmarker_item, null);
            }
            final ListMarker item = items.get(position);
            if (item != null) {
                final ImageView iv = (ImageView) v.findViewById(R.id.listmarker_item_marker);
                final TextView tv = (TextView) v.findViewById(R.id.listmarker_item_text);
                if (item.markerId == ListMarker.NO_MARKER.markerId) {
                    iv.setImageBitmap(null);
                    tv.setText(context.getString(R.string.caches_listmarker_none) + (item.markerId == markerId ? " " + context.getString(R.string.caches_listmarker_selected) : ""));
                } else {
                    iv.setImageResource(item.resDrawable);
                    tv.setText(String.format(context.getString(R.string.caches_listmarker_marker), item.markerId) + (item.markerId == markerId ? " " + context.getString(R.string.caches_listmarker_selected) : ""));
                }
            }
            return v;
        }
    }

    private void selectListMarker() {
        final ArrayList<ListMarker> items = new ArrayList<>();
        for (final ListMarker temp : ListMarker.values()) {
            items.add(temp);
        }
        final ListMarkerAdapter adapter2 = new ListMarkerAdapter(this, R.layout.cachelist_listmarker_item,  items);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.caches_set_listmarker);
        builder.setAdapter(adapter2, (dialog, itemSelected) -> {
            final ListMarker selectedItem = adapter2.getItem(itemSelected);
            DataStore.setListMarker(listId, selectedItem.markerId);
            markerId = selectedItem.markerId;
            MapMarkerUtils.resetLists();
            adapter.notifyDataSetChanged();
        });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_show_on_map:
                goMap();
                return true;
            case R.id.menu_switch_select_mode:
                adapter.switchSelectMode();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_refresh_stored:
                refreshStored(adapter.getCheckedOrAllCaches());
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_drop_caches:
                deleteCaches(adapter.getCheckedOrAllCaches());
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_import_pq:
                importPq();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_import_gpx:
                importGpx();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_import_android:
                importGpxFromAndroid();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_create_list:
                new StoredList.UserInterface(this).promptForListCreation(getListSwitchingRunnable(), StringUtils.EMPTY);
                refreshSpinnerAdapter();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_drop_list:
                removeList();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_rename_list:
                renameList();
                return true;
            case R.id.menu_invert_selection:
                adapter.invertSelection();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_filter:
                showFilterMenu(null);
                return true;
            case R.id.menu_import_web:
                importWeb();
                return true;
            case R.id.menu_export_gpx:
                new GpxExport().export(adapter.getCheckedOrAllCaches(), this);
                return true;
            case R.id.menu_export_fieldnotes:
                new FieldNoteExport().export(adapter.getCheckedOrAllCaches(), this);
                return true;
            case R.id.menu_export_persnotes:
                new PersonalNoteExport().export(adapter.getCheckedOrAllCaches(), this);
                return true;
            case R.id.menu_upload_modifiedcoords:
                final Activity that = this;
                Dialogs.confirm(this, R.string.caches_upload_modifiedcoords, R.string.caches_upload_modifiedcoords_warning, (dialog, which) -> new BatchUploadModifiedCoordinates(true).export(adapter.getCheckedOrAllCaches(), that));
                return true;
            case R.id.menu_upload_allcoords:
                final Activity that2 = this;
                Dialogs.confirm(this, R.string.caches_upload_allcoords, R.string.caches_upload_allcoords_warning, (dialog, which) -> new BatchUploadModifiedCoordinates(false).export(adapter.getCheckedOrAllCaches(), that2));
                return true;
            case R.id.menu_remove_from_history:
                removeFromHistoryCheck();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_move_to_list:
                moveCachesToOtherList(adapter.getCheckedOrAllCaches());
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_copy_to_list:
                copyCachesToOtherList(adapter.getCheckedOrAllCaches());
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_delete_events:
                deletePastEvents();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_clear_offline_logs:
                clearOfflineLogs();
                invalidateOptionsMenuCompatible();
                return true;
            case R.id.menu_cache_list_app:
                if (cacheToShow()) {
                    CacheListApps.getActiveApps().get(0).invoke(CacheListAppUtils.filterCoords(cacheList), this, getFilteredSearch());
                }
                return true;
            case R.id.menu_make_list_unique:
                new MakeListUniqueCommand(this, listId) {

                    @Override
                    protected void onFinished() {
                        refreshSpinnerAdapter();
                    }

                    @Override
                    protected void onFinishedUndo() {
                        refreshSpinnerAdapter();
                    }

                }.execute();
                return true;
            case R.id.menu_set_listmarker:
                selectListMarker();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkIfEmptyAndRemoveAfterConfirm() {
        final boolean isNonDefaultList = isConcreteList() && listId != StoredList.STANDARD_LIST_ID;
        // Check local cacheList first, and Datastore only if needed (because of filtered lists)
        // Checking is done in this order for performance reasons
        if (isNonDefaultList && CollectionUtils.isEmpty(cacheList)
                && DataStore.getAllStoredCachesCount(CacheType.ALL, listId) == 0) {
            // ask user, if he wants to delete the now empty list
            Dialogs.confirmYesNo(this, R.string.list_dialog_remove_title, R.string.list_dialog_remove_nowempty, (dialog, whichButton) -> removeListInternal());
        }
    }

    private boolean cacheToShow() {
        if (search == null || CollectionUtils.isEmpty(cacheList)) {
            showToast(res.getString(R.string.warn_no_cache_coord));
            return false;
        }
        return true;
    }

    private SearchResult getFilteredSearch() {
        return new SearchResult(Geocache.getGeocodes(adapter.getFilteredList()));
    }

    private void deletePastEvents() {
        final List<Geocache> deletion = new ArrayList<>();
        for (final Geocache cache : adapter.getCheckedOrAllCaches()) {
            if (CalendarUtils.isPastEvent(cache)) {
                deletion.add(cache);
            }
        }
        deleteCaches(deletion);
    }

    private void clearOfflineLogs() {
        Dialogs.confirmYesNo(this, R.string.caches_clear_offlinelogs, R.string.caches_clear_offlinelogs_message, (dialog, which) -> {
            progress.show(CacheListActivity.this, null, res.getString(R.string.caches_clear_offlinelogs_progress), true, clearOfflineLogsHandler.disposeMessage());
            clearOfflineLogs(clearOfflineLogsHandler, adapter.getCheckedOrAllCaches());
        });
    }

    /**
     * called from the filter bar view
     */
    @Override
    public void showFilterMenu(final View view) {
        if (view != null && Settings.getCacheType() != CacheType.ALL) {
            Dialogs.selectGlobalTypeFilter(this, new Action1<CacheType>() {
                @Override
                public void call(final CacheType cacheType) {
                    refreshCurrentList();
                    prepareFilterBar();
                }
            });
        } else {
            FilterActivity.selectFilter(this);
        }
    }

    private void setComparator(final CacheComparator comparator) {
        adapter.setComparator(comparator);
        currentInverseSort = adapter.getInverseSort();
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View view, final ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, view, info);

        AdapterContextMenuInfo adapterInfo = null;
        try {
            adapterInfo = (AdapterContextMenuInfo) info;
        } catch (final Exception e) {
            Log.w("CacheListActivity.onCreateContextMenu", e);
        }

        if (adapterInfo == null || adapterInfo.position >= adapter.getCount()) {
            return;
        }
        final Geocache cache = adapter.getItem(adapterInfo.position);

        menu.setHeaderTitle(StringUtils.defaultIfBlank(cache.getName(), cache.getGeocode()));

        contextMenuGeocode = cache.getGeocode();

        getMenuInflater().inflate(R.menu.cache_list_context, menu);

        menu.findItem(R.id.menu_default_navigation).setTitle(NavigationAppFactory.getDefaultNavigationApplication().getName());
        final boolean hasCoords = cache.getCoords() != null;
        menu.findItem(R.id.menu_default_navigation).setVisible(hasCoords);
        menu.findItem(R.id.menu_navigate).setVisible(hasCoords);
        menu.findItem(R.id.menu_cache_details).setVisible(hasCoords);
        final boolean isOffline = cache.isOffline();
        menu.findItem(R.id.menu_drop_cache).setVisible(isOffline);
        menu.findItem(R.id.menu_move_to_list).setVisible(isOffline);
        menu.findItem(R.id.menu_copy_to_list).setVisible(isOffline);
        menu.findItem(R.id.menu_refresh).setVisible(isOffline);
        menu.findItem(R.id.menu_store_cache).setVisible(!isOffline);

        LoggingUI.onPrepareOptionsMenu(menu, cache);
    }

    private void moveCachesToOtherList(final Collection<Geocache> caches) {
        if (isConcreteList()) {
            new MoveToListCommand(this, caches, listId) {
                private LastPositionHelper lastPositionHelper;

                @Override
                protected void doCommand() {
                    lastPositionHelper = new LastPositionHelper(CacheListActivity.this);
                    super.doCommand();
                }

                @Override
                protected void onFinished() {
                    lastPositionHelper.refreshListAtLastPosition();
                }

            }.execute();
        } else {
            new MoveToListAndRemoveFromOthersCommand(this, caches) {
                private LastPositionHelper lastPositionHelper;

                @Override
                protected void doCommand() {
                    lastPositionHelper = new LastPositionHelper(CacheListActivity.this);
                    super.doCommand();
                }

                @Override
                protected void onFinished() {
                    lastPositionHelper.refreshListAtLastPosition();
                }

            }.execute();
        }
    }

    private void copyCachesToOtherList(final Collection<Geocache> caches) {
        new CopyToListCommand(this, caches, listId) {

            @Override
            protected void onFinished() {
                adapter.setSelectMode(false);
                refreshCurrentList(AfterLoadAction.CHECK_IF_EMPTY);
            }

        }.execute();
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        ContextMenu.ContextMenuInfo info = item.getMenuInfo();

        // restore menu info for sub menu items, see
        // https://code.google.com/p/android/issues/detail?id=7139
        if (info == null) {
            info = lastMenuInfo;
            lastMenuInfo = null;
        }

        AdapterContextMenuInfo adapterInfo = null;
        try {
            adapterInfo = (AdapterContextMenuInfo) info;
        } catch (final Exception e) {
            Log.w("CacheListActivity.onContextItemSelected", e);
        }

        final Geocache cache = adapterInfo != null ? getCacheFromAdapter(adapterInfo) : null;

        // just in case the list got resorted while we are executing this code
        if (cache == null || adapterInfo == null) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.menu_default_navigation:
                NavigationAppFactory.startDefaultNavigationApplication(1, this, cache);
                break;
            case R.id.menu_navigate:
                NavigationAppFactory.showNavigationMenu(this, cache, null, null);
                break;
            case R.id.menu_cache_details:
                CacheDetailActivity.startActivity(this, cache.getGeocode(), cache.getName());
                break;
            case R.id.menu_drop_cache:
                deleteCaches(Collections.singletonList(cache));
                break;
            case R.id.menu_move_to_list:
                moveCachesToOtherList(Collections.singletonList(cache));
                break;
            case R.id.menu_copy_to_list:
                copyCachesToOtherList(Collections.singletonList(cache));
                break;
            case R.id.menu_store_cache:
            case R.id.menu_refresh:
                refreshStored(Collections.singletonList(cache));
                break;
            default:
                // we must remember the menu info for the sub menu, there is a bug
                // in Android:
                // https://code.google.com/p/android/issues/detail?id=7139
                lastMenuInfo = info;
                final View selectedView = adapterInfo.targetView;
                LoggingUI.onMenuItemSelected(item, this, cache, new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(final DialogInterface dialog) {
                        if (selectedView != null) {
                            final CacheListAdapter.ViewHolder holder = (CacheListAdapter.ViewHolder) selectedView.getTag();
                            if (holder != null) {
                                CacheListAdapter.updateViewHolder(holder, cache, res);
                            }
                        }
                    }
                });
        }

        return true;
    }

    /**
     * Extract a cache from adapter data.
     *
     * @param adapterInfo
     *            an adapterInfo
     * @return the pointed cache
     */
    private Geocache getCacheFromAdapter(final AdapterContextMenuInfo adapterInfo) {
        final Geocache cache = adapter.getItem(adapterInfo.position);
        if (cache.getGeocode().equalsIgnoreCase(contextMenuGeocode)) {
            return cache;
        }

        return adapter.findCacheByGeocode(contextMenuGeocode);
    }

    private void setFilter(final IFilter filter) {
        currentFilter = filter;
        adapter.setFilter(filter);
        prepareFilterBar();
        updateTitle();
        invalidateOptionsMenuCompatible();
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && adapter.isSelectMode()) {
            adapter.setSelectMode(false);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void initAdapter() {
        final ListView listView = getListView();
        registerForContextMenu(listView);

        adapter = new CacheListAdapter(this, cacheList, type);
        adapter.setStoredLists(Settings.showListsInCacheList() ? StoredList.UserInterface.getMenuLists(true, PseudoList.NEW_LIST.id) : null);
        adapter.setFilter(currentFilter);

        if (listFooter == null) {
            listFooter = getLayoutInflater().inflate(R.layout.cacheslist_footer, listView, false);
            listFooter.setClickable(true);
            listFooter.setOnClickListener(new MoreCachesListener());
            listFooterText = ButterKnife.findById(listFooter, R.id.more_caches);
            listView.addFooterView(listFooter);
        }
        setListAdapter(adapter);

        adapter.setInverseSort(currentInverseSort);
        adapter.forceSort();
    }

    private void updateAdapter() {
        adapter.notifyDataSetChanged();
        adapter.reFilter();
        adapter.checkSpecialSortOrder();
        adapter.forceSort();
    }

    private void showFooterLoadingCaches() {
        // no footer for offline lists
        if (listFooter == null) {
            return;
        }
        listFooterText.setText(res.getString(R.string.caches_more_caches_loading));
        listFooter.setClickable(false);
        listFooter.setOnClickListener(null);
    }

    private void showFooterMoreCaches() {
        // no footer in offline lists
        if (listFooter == null) {
            return;
        }

        boolean enableMore = type != CacheListType.OFFLINE && cacheList.size() < MAX_LIST_ITEMS;
        if (enableMore && search != null) {
            final int count = search.getTotalCountGC();
            enableMore = count > 0 && cacheList.size() < count;
        }

        listFooter.setClickable(enableMore);
        if (enableMore) {
            listFooterText.setText(res.getString(R.string.caches_more_caches) + " (" + res.getString(R.string.caches_more_caches_currently) + ": " + cacheList.size() + ")");
            listFooter.setOnClickListener(new MoreCachesListener());
        } else if (type != CacheListType.OFFLINE) {
            listFooterText.setText(res.getString(CollectionUtils.isEmpty(cacheList) ? R.string.caches_no_cache : R.string.caches_more_caches_no));
            listFooter.setOnClickListener(null);
        } else {
            // hiding footer for offline list is not possible, it must be removed instead
            // http://stackoverflow.com/questions/7576099/hiding-footer-in-listview
            getListView().removeFooterView(listFooter);
        }
    }

    private void importGpx() {
        GpxFileListActivity.startSubActivity(this, listId, REQUEST_CODE_RESTART);
    }

    private void importGpxFromAndroid() {
        Compatibility.importGpxFromStorageAccessFramework(this, REQUEST_CODE_IMPORT_GPX);
    }

    private void importPq() {
        PocketQueryListActivity.startSubActivity(this, REQUEST_CODE_IMPORT_PQ);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_IMPORT_GPX && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.  Pull that uri using "resultData.getData()"
            if (data != null) {
                final Uri uri = data.getData();
                new GPXImporter(this, listId, importGpxAttachementFinishedHandler).importGPX(uri, null, getDisplayName(uri));
            }
        } else if (requestCode == REQUEST_CODE_IMPORT_PQ && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                final Uri uri = data.getData();
                new GPXImporter(this, listId, importGpxAttachementFinishedHandler).importGPX(uri, data.getType(), null);
            }
        } else if (requestCode == FilterActivity.REQUEST_SELECT_FILTER && resultCode == Activity.RESULT_OK) {
            final int[] filterIndex = data.getIntArrayExtra(FilterActivity.EXTRA_FILTER_RESULT);
            setFilter(FilterActivity.getFilterFromPosition(filterIndex[0], filterIndex[1]));
        } else if (requestCode == REQUEST_CODE_RESTART && resultCode == Activity.RESULT_OK) {
            restartActivity();
        }

        if (type == CacheListType.OFFLINE && requestCode != REQUEST_CODE_RESTART) {
            refreshCurrentList();
        }
    }

    private String getDisplayName(final Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            }
        } finally {
            if (cursor != null) {
                cursor.close(); // no Closable Cursor below sdk 16
            }
        }
        return null;
    }

    public void refreshStored(final List<Geocache> caches) {
        if (type == CacheListType.OFFLINE && caches.size() > REFRESH_WARNING_THRESHOLD) {
            Dialogs.confirmYesNo(this, R.string.caches_refresh_all, R.string.caches_refresh_all_warning, (dialog, id) -> {
                refreshStoredConfirmed(caches);
                dialog.cancel();
            });
        } else {
            refreshStoredConfirmed(caches);
        }
    }

    private void refreshStoredConfirmed(final List<Geocache> caches) {
        detailTotal = caches.size();
        if (detailTotal == 0) {
            return;
        }

        if (!Network.isConnected()) {
            showToast(getString(R.string.err_server));
            return;
        }

        if (Settings.getChooseList() && type != CacheListType.OFFLINE && type != CacheListType.HISTORY) {
            // let user select list to store cache in
            new StoredList.UserInterface(this).promptForMultiListSelection(R.string.lists_title,
                    new Action1<Set<Integer>>() {
                        @Override
                        public void call(final Set<Integer> selectedListIds) {
                            refreshStoredInternal(caches, selectedListIds);
                        }
                    }, true, Collections.singleton(StoredList.TEMPORARY_LIST.id), Collections.<Integer>emptySet(), listNameMemento, false);
        } else {
            final Set<Integer> additionalListIds = new HashSet<>();
            if (type != CacheListType.OFFLINE && type != CacheListType.HISTORY) {
                additionalListIds.add(StoredList.STANDARD_LIST_ID);
            }
            refreshStoredInternal(caches, additionalListIds);
        }
    }

    private void refreshStoredInternal(final List<Geocache> caches, final Set<Integer> additionalListIds) {
        detailProgress.set(0);

        showProgress(false);

        final int etaTime = detailTotal * 25 / 60;
        final String message;
        if (etaTime < 1) {
            message = res.getString(R.string.caches_downloading) + " " + res.getString(R.string.caches_eta_ltm);
        } else {
            message = res.getString(R.string.caches_downloading) + " " + res.getQuantityString(R.plurals.caches_eta_mins, etaTime, etaTime);
        }

        final LoadDetailsHandler loadDetailsHandler = new LoadDetailsHandler(this);
        progress.show(this, null, message, ProgressDialog.STYLE_HORIZONTAL, loadDetailsHandler.disposeMessage());
        progress.setMaxProgressAndReset(detailTotal);

        detailProgressTime = System.currentTimeMillis();

        loadDetails(loadDetailsHandler, caches, additionalListIds);
    }

    public void removeFromHistoryCheck() {
        final int message = (adapter != null && adapter.getCheckedCount() > 0) ? R.string.cache_remove_from_history
                : R.string.cache_clear_history;
        Dialogs.confirmYesNo(this, R.string.caches_removing_from_history, message, (dialog, id) -> {
            removeFromHistory();
            dialog.cancel();
        });
    }

    private void removeFromHistory() {
        final List<Geocache> caches = adapter.getCheckedOrAllCaches();
        final Collection<String> geocodes = new ArrayList<>(caches.size());
        for (final Geocache cache : caches) {
            geocodes.add(cache.getGeocode());
        }
        DataStore.clearVisitDate(geocodes);
        DataStore.clearLogsOffline(caches);
        refreshCurrentList();
    }

    private void importWeb() {
        // menu is also shown with no device connected
        if (!Settings.isRegisteredForSend2cgeo()) {
            Dialogs.confirm(this, R.string.web_import_title, R.string.init_sendToCgeo_description, (dialog, which) -> SettingsActivity.openForScreen(R.string.preference_screen_sendtocgeo, CacheListActivity.this));
            return;
        }

        detailProgress.set(0);
        showProgress(false);
        final DownloadFromWebHandler downloadFromWebHandler = new DownloadFromWebHandler(this);
        progress.show(this, null, res.getString(R.string.web_import_waiting), true, downloadFromWebHandler.disposeMessage());
        Send2CgeoDownloader.loadFromWeb(downloadFromWebHandler, listId);
    }

    private void deleteCaches(@NonNull final Collection<Geocache> caches) {
        new DeleteCachesFromListCommand(this, caches, listId).execute();
    }

    /**
     * Method to asynchronously refresh the caches details.
     */
    private void loadDetails(final DisposableHandler handler, final List<Geocache> caches, final Set<Integer> additionalListIds) {
        final Observable<Geocache> allCaches;
        allCaches = Observable.fromIterable(caches);
        final Observable<Geocache> loaded = allCaches.flatMap(new Function<Geocache, Observable<Geocache>>() {
            @Override
            public Observable<Geocache> apply(final Geocache cache) {
                return Observable.create(new ObservableOnSubscribe<Geocache>() {
                    @Override
                    public void subscribe(final ObservableEmitter<Geocache> emitter) throws Exception {
                        cache.refreshSynchronous(null, additionalListIds);
                        detailProgress.incrementAndGet();
                        handler.obtainMessage(DownloadProgress.MSG_LOADED, cache).sendToTarget();
                        emitter.onComplete();
                    }
                }).subscribeOn(AndroidRxUtils.refreshScheduler);
            }
        }).doOnComplete(new Action() {
            @Override
            public void run() {
                handler.sendEmptyMessage(DownloadProgress.MSG_DONE);
            }
        });
        handler.add(loaded.subscribe());
    }

    private static final class LastPositionHelper {
        private final WeakReference<CacheListActivity> activityRef;
        private final int lastListPosition;

        LastPositionHelper(@NonNull final CacheListActivity context) {
            super();
            this.lastListPosition = context.getListView().getFirstVisiblePosition();
            this.activityRef = new WeakReference<>(context);
        }

        public void refreshListAtLastPosition() {
            final CacheListActivity activity = activityRef.get();
            if (activity != null) {
                activity.adapter.setSelectMode(false);
                activity.refreshCurrentList(AfterLoadAction.CHECK_IF_EMPTY);
                activity.replaceCacheListFromSearch();
                if (lastListPosition > 0) {
                    activity.getListView().setSelection(lastListPosition);
                }
            }
        }
    }

    private static final class DeleteCachesFromListCommand extends AbstractCachesCommand {

        private final LastPositionHelper lastPositionHelper;
        private final int listId;
        private final Map<String, Set<Integer>> oldCachesLists = new HashMap<>();

        DeleteCachesFromListCommand(@NonNull final CacheListActivity context, final Collection<Geocache> caches, final int listId) {
            super(context, caches, R.string.command_delete_caches_progress);
            this.lastPositionHelper = new LastPositionHelper(context);
            this.listId = listId;
        }

        @Override
        public void onFinished() {
            lastPositionHelper.refreshListAtLastPosition();
        }

        @Override
        protected void doCommand() {
            if (appliesToAllLists()) {
                oldCachesLists.putAll(DataStore.markDropped(getCaches()));
            } else {
                DataStore.removeFromList(getCaches(), listId);
            }
        }

        private boolean appliesToAllLists() {
            return listId == PseudoList.ALL_LIST.id || listId == PseudoList.HISTORY_LIST.id || listId == StoredList.TEMPORARY_LIST.id;
        }

        @Override
        protected void undoCommand() {
            if (appliesToAllLists()) {
                DataStore.addToLists(getCaches(), oldCachesLists);
            } else {
                DataStore.addToList(getCaches(), listId);
            }
        }

        @Override
        @NonNull
        protected String getResultMessage() {
            final int size = getCaches().size();
            return getContext().getResources().getQuantityString(R.plurals.command_delete_caches_result, size, size);
        }
    }

    private static void clearOfflineLogs(final Handler handler, final List<Geocache> selectedCaches) {
        Schedulers.io().scheduleDirect(() -> {
            DataStore.clearLogsOffline(selectedCaches);
            handler.sendEmptyMessage(DownloadProgress.MSG_DONE);
        });
    }

    private class MoreCachesListener implements View.OnClickListener {

        @Override
        public void onClick(final View arg0) {
            showProgress(true);
            showFooterLoadingCaches();

            getSupportLoaderManager().restartLoader(CacheListLoaderType.NEXT_PAGE.getLoaderId(), null, CacheListActivity.this);
            // the loader for subsequent pages takes over - therefore the initial loader needs to be destroyed
            getSupportLoaderManager().destroyLoader(type.getLoaderId());
        }
    }

    private void hideLoading() {
        final ListView list = getListView();
        if (list.getVisibility() == View.GONE) {
            list.setVisibility(View.VISIBLE);
            final View loading = findViewById(R.id.loading);
            loading.setVisibility(View.GONE);
        }
    }

    @NonNull
    private Action1<Integer> getListSwitchingRunnable() {
        return new Action1<Integer>() {

            @Override
            public void call(final Integer selectedListId) {
                switchListById(selectedListId);
            }
        };
    }

    private void switchListById(final int id) {
        switchListById(id, AfterLoadAction.NO_ACTION);
    }

    private void switchListById(final int id, @NonNull final AfterLoadAction action) {
        if (id < 0) {
            return;
        }

        final Bundle extras = new Bundle();
        extras.putSerializable(BUNDLE_ACTION_KEY, action);

        if (id == PseudoList.HISTORY_LIST.id) {
            type = CacheListType.HISTORY;
            getSupportLoaderManager().destroyLoader(CacheListType.OFFLINE.getLoaderId());
            currentLoader = (AbstractSearchLoader) getSupportLoaderManager().restartLoader(CacheListType.HISTORY.getLoaderId(), extras, this);
        } else {
            if (id == PseudoList.ALL_LIST.id) {
                listId = id;
                title = res.getString(R.string.list_all_lists);
                markerId = ListMarker.NO_MARKER.markerId;
            } else {
                final StoredList list = DataStore.getList(id);
                listId = list.id;
                title = list.title;
                markerId = list.markerId;
            }
            type = CacheListType.OFFLINE;

            getSupportLoaderManager().destroyLoader(CacheListType.HISTORY.getLoaderId());
            extras.putAll(OfflineGeocacheListLoader.getBundleForList(listId));
            currentLoader = (OfflineGeocacheListLoader) getSupportLoaderManager().restartLoader(CacheListType.OFFLINE.getLoaderId(), extras, this);

            Settings.setLastDisplayedList(listId);
        }

        initAdapter();

        showProgress(true);
        showFooterLoadingCaches();
        adapter.setSelectMode(false);
        invalidateOptionsMenuCompatible();
    }

    private void renameList() {
        (new RenameListCommand(this, listId) {

            @Override
            protected void onFinished() {
                refreshCurrentList();
            }
        }).execute();
    }

    private void removeListInternal() {
        new DeleteListCommand(this, listId) {

            private String oldListName;

            @Override
            protected boolean canExecute() {
                oldListName = DataStore.getList(listId).getTitle();
                return super.canExecute();
            }

            @Override
            protected void onFinished() {
                refreshSpinnerAdapter();
                switchListById(StoredList.STANDARD_LIST_ID);
            }

            @Override
            protected void onFinishedUndo() {
                refreshSpinnerAdapter();
                for (final StoredList list : DataStore.getLists()) {
                    if (oldListName.equals(list.getTitle())) {
                        switchListById(list.id);
                    }
                }
            }

        }.execute();
    }

    private void removeList() {
        // if there are no caches on this list, don't bother the user with questions.
        // there is no harm in deleting the list, he could recreate it easily
        if (CollectionUtils.isEmpty(cacheList)) {
            removeListInternal();
            return;
        }

        // ask him, if there are caches on the list
        Dialogs.confirm(this, R.string.list_dialog_remove_title, R.string.list_dialog_remove_description, R.string.list_dialog_remove, (dialog, whichButton) -> removeListInternal());
    }

    public void goMap() {
        if (!cacheToShow()) {
            return;
        }

        // apply filter settings (if there's a filter)
        final SearchResult searchToUse = getFilteredSearch();
        DefaultMap.startActivitySearch(this, searchToUse, title);
    }

    private void refreshCurrentList() {
        refreshCurrentList(AfterLoadAction.NO_ACTION);
    }

    private void refreshCurrentList(@NonNull final AfterLoadAction action) {
        // do not refresh any of the dynamic search result lists but history, which might have been cleared
        if (type != CacheListType.OFFLINE && type != CacheListType.HISTORY) {
            return;
        }
        refreshSpinnerAdapter();
        switchListById(listId, action);
    }

    public static void startActivityOffline(final Context context) {
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(cachesIntent, CacheListType.OFFLINE);
        context.startActivity(cachesIntent);
    }

    public static void startActivityOwner(final Context context, final String userName) {
        if (!checkNonBlankUsername(context, userName)) {
            return;
        }
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(cachesIntent, CacheListType.OWNER);
        cachesIntent.putExtra(Intents.EXTRA_USERNAME, userName);
        context.startActivity(cachesIntent);
    }

    /**
     * Check if a given username is valid (non blank), and show a toast if it isn't.
     *
     * @param context an activity
     * @param username the username to check
     * @return <tt>true</tt> if the username is not blank, <tt>false</tt> otherwise
     */
    private static boolean checkNonBlankUsername(final Context context, final String username) {
        if (StringUtils.isBlank(username)) {
            ActivityMixin.showToast(context, R.string.warn_no_username);
            return false;
        }
        return true;
    }

    public static void startActivityFinder(final Context context, final String userName) {
        if (!checkNonBlankUsername(context, userName)) {
            return;
        }
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(cachesIntent, CacheListType.FINDER);
        cachesIntent.putExtra(Intents.EXTRA_USERNAME, userName);
        context.startActivity(cachesIntent);
    }

    private void prepareFilterBar() {
        final List<String> filterNames = getFilterNames();
        if (filterNames.isEmpty()) {
            findViewById(R.id.filter_bar).setVisibility(View.GONE);
        } else {
            final TextView filterTextView = ButterKnife.findById(this, R.id.filter_text);
            filterTextView.setText(TextUtils.join(", ", filterNames));
            findViewById(R.id.filter_bar).setVisibility(View.VISIBLE);
        }
    }

    @NonNull
    private List<String> getFilterNames() {
        final List<String> filters = new ArrayList<>();
        if (Settings.getCacheType() != CacheType.ALL) {
            filters.add(Settings.getCacheType().getL10n());
        }
        if (adapter.isFiltered()) {
            filters.add(adapter.getFilterName());
        }
        return filters;
    }

    public static Intent getNearestIntent(final Activity context) {
        return Intents.putListType(new Intent(context, CacheListActivity.class), CacheListType.NEAREST);
    }

    public static Intent getHistoryIntent(final Context context) {
        return Intents.putListType(new Intent(context, CacheListActivity.class), CacheListType.HISTORY);
    }

    public static void startActivityAddress(final Context context, final Geopoint coords, final String address) {
        final Intent addressIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(addressIntent, CacheListType.ADDRESS);
        addressIntent.putExtra(Intents.EXTRA_COORDS, coords);
        addressIntent.putExtra(Intents.EXTRA_ADDRESS, address);
        context.startActivity(addressIntent);
    }

    /**
     * start list activity, by searching around the given point.
     *
     * @param name
     *            name of coordinates, will lead to a title like "Around ..." instead of directly showing the
     *            coordinates as title
     */
    public static void startActivityCoordinates(final AbstractActivity context, final Geopoint coords, @Nullable final String name) {
        if (!isValidCoords(context, coords)) {
            return;
        }
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(cachesIntent, CacheListType.COORDINATE);
        cachesIntent.putExtra(Intents.EXTRA_COORDS, coords);
        if (StringUtils.isNotEmpty(name)) {
            cachesIntent.putExtra(Intents.EXTRA_TITLE, context.getString(R.string.around, name));
        }
        context.startActivity(cachesIntent);
    }

    private static boolean isValidCoords(final AbstractActivity context, final Geopoint coords) {
        if (coords == null) {
            context.showToast(CgeoApplication.getInstance().getString(R.string.warn_no_coordinates));
            return false;
        }
        return true;
    }

    public static void startActivityKeyword(final AbstractActivity context, final String keyword) {
        if (keyword == null) {
            context.showToast(CgeoApplication.getInstance().getString(R.string.warn_no_keyword));
            return;
        }
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(cachesIntent, CacheListType.KEYWORD);
        cachesIntent.putExtra(Intents.EXTRA_KEYWORD, keyword);
        context.startActivity(cachesIntent);
    }

    public static void startActivityMap(final Context context, final SearchResult search) {
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        cachesIntent.putExtra(Intents.EXTRA_SEARCH, search);
        Intents.putListType(cachesIntent, CacheListType.MAP);
        context.startActivity(cachesIntent);
    }

    public static void startActivityPocketDownload(@NonNull final Context context, @NonNull final PocketQuery pocketQuery) {
        final String guid = pocketQuery.getGuid();
        if (guid == null) {
            ActivityMixin.showToast(context, CgeoApplication.getInstance().getString(R.string.warn_pocket_query_select));
            return;
        }
        startActivityWithAttachment(context, pocketQuery);
    }

    public static void startActivityPocket(@NonNull final Context context, @NonNull final PocketQuery pocketQuery) {
        final String guid = pocketQuery.getGuid();
        if (guid == null) {
            ActivityMixin.showToast(context, CgeoApplication.getInstance().getString(R.string.warn_pocket_query_select));
            return;
        }
        startActivityPocket(context, pocketQuery, CacheListType.POCKET);
    }

    private static void startActivityWithAttachment(@NonNull final Context context, @NonNull final PocketQuery pocketQuery) {
        final Uri uri = pocketQuery.getUri();
        final Intent cachesIntent = new Intent(Intent.ACTION_VIEW, uri, context, CacheListActivity.class);
        cachesIntent.setDataAndType(uri, "application/zip");
        cachesIntent.putExtra(Intents.EXTRA_NAME, pocketQuery.getName());
        context.startActivity(cachesIntent);
    }

    private static void startActivityPocket(@NonNull final Context context, @NonNull final PocketQuery pocketQuery, final CacheListType cacheListType) {
        final Intent cachesIntent = new Intent(context, CacheListActivity.class);
        Intents.putListType(cachesIntent, cacheListType);
        cachesIntent.putExtra(Intents.EXTRA_NAME, pocketQuery.getName());
        cachesIntent.putExtra(Intents.EXTRA_POCKET_GUID, pocketQuery.getGuid());
        context.startActivity(cachesIntent);
    }

    // Loaders

    @Override
    public Loader<SearchResult> onCreateLoader(final int type, final Bundle extras) {
        if (type >= CacheListLoaderType.values().length) {
            throw new IllegalArgumentException("invalid loader type " + type);
        }
        final CacheListLoaderType enumType = CacheListLoaderType.values()[type];
        AbstractSearchLoader loader = null;
        switch (enumType) {
            case OFFLINE:
                // open either the requested or the last list
                if (extras.containsKey(Intents.EXTRA_LIST_ID)) {
                    listId = extras.getInt(Intents.EXTRA_LIST_ID);
                } else {
                    listId = Settings.getLastDisplayedList();
                }
                if (listId == PseudoList.ALL_LIST.id) {
                    title = res.getString(R.string.list_all_lists);
                    markerId = ListMarker.NO_MARKER.markerId;
                } else if (listId <= StoredList.TEMPORARY_LIST.id) {
                    listId = StoredList.STANDARD_LIST_ID;
                    title = res.getString(R.string.stored_caches_button);
                    markerId = ListMarker.NO_MARKER.markerId;
                } else {
                    final StoredList list = DataStore.getList(listId);
                    // list.id may be different if listId was not valid
                    if (list.id != listId) {
                        showToast(getString(R.string.list_not_available));
                    }
                    listId = list.id;
                    title = list.title;
                    markerId = list.markerId;
                }

                loader = new OfflineGeocacheListLoader(this, coords, listId);

                break;
            case HISTORY:
                title = res.getString(R.string.caches_history);
                listId = PseudoList.HISTORY_LIST.id;
                markerId = ListMarker.NO_MARKER.markerId;
                loader = new HistoryGeocacheListLoader(this, coords);
                break;
            case NEAREST:
                title = res.getString(R.string.caches_nearby);
                markerId = ListMarker.NO_MARKER.markerId;
                loader = new CoordsGeocacheListLoader(this, coords);
                break;
            case COORDINATE:
                title = coords.toString();
                markerId = ListMarker.NO_MARKER.markerId;
                loader = new CoordsGeocacheListLoader(this, coords);
                break;
            case KEYWORD:
                final String keyword = extras.getString(Intents.EXTRA_KEYWORD);
                markerId = ListMarker.NO_MARKER.markerId;
                title = listNameMemento.rememberTerm(keyword);
                if (keyword != null) {
                    loader = new KeywordGeocacheListLoader(this, keyword);
                }
                break;
            case ADDRESS:
                final String address = extras.getString(Intents.EXTRA_ADDRESS);
                if (StringUtils.isNotBlank(address)) {
                    title = listNameMemento.rememberTerm(address);
                } else {
                    title = coords.toString();
                }
                markerId = ListMarker.NO_MARKER.markerId;
                loader = new CoordsGeocacheListLoader(this, coords);
                break;
            case FINDER:
                final String username = extras.getString(Intents.EXTRA_USERNAME);
                title = listNameMemento.rememberTerm(username);
                markerId = ListMarker.NO_MARKER.markerId;
                if (username != null) {
                    loader = new FinderGeocacheListLoader(this, username);
                }
                break;
            case OWNER:
                final String ownerName = extras.getString(Intents.EXTRA_USERNAME);
                title = listNameMemento.rememberTerm(ownerName);
                markerId = ListMarker.NO_MARKER.markerId;
                if (ownerName != null) {
                    loader = new OwnerGeocacheListLoader(this, ownerName);
                }
                break;
            case MAP:
                //TODO Build Null loader
                title = res.getString(R.string.map_map);
                markerId = ListMarker.NO_MARKER.markerId;
                search = (SearchResult) extras.get(Intents.EXTRA_SEARCH);
                replaceCacheListFromSearch();
                loadCachesHandler.sendMessage(Message.obtain());
                break;
            case NEXT_PAGE:
                loader = new NextPageGeocacheListLoader(this, search);
                break;
            case POCKET:
                final String guid = extras.getString(Intents.EXTRA_POCKET_GUID);
                title = listNameMemento.rememberTerm(extras.getString(Intents.EXTRA_NAME));
                markerId = ListMarker.NO_MARKER.markerId;
                loader = new PocketGeocacheListLoader(this, guid);
                break;
        }
        // if there is a title given in the activity start request, use this one instead of the default
        if (extras != null && StringUtils.isNotBlank(extras.getString(Intents.EXTRA_TITLE))) {
            title = extras.getString(Intents.EXTRA_TITLE);
        }
        if (loader != null && extras != null && extras.getSerializable(BUNDLE_ACTION_KEY) != null) {
            final AfterLoadAction action = (AfterLoadAction) extras.getSerializable(BUNDLE_ACTION_KEY);
            loader.setAfterLoadAction(action);
        }
        updateTitle();
        showProgress(true);
        showFooterLoadingCaches();

        return loader;
    }

    @Override
    public void onLoadFinished(final Loader<SearchResult> arg0, final SearchResult searchIn) {
        // The database search was moved into the UI call intentionally. If this is done before the runOnUIThread,
        // then we have 2 sets of caches in memory. This can lead to OOM for huge cache lists.
        if (searchIn != null) {
            cacheList.clear();
            final Set<Geocache> cachesFromSearchResult = searchIn.getCachesFromSearchResult(LoadFlags.LOAD_CACHE_OR_DB);
            cacheList.addAll(cachesFromSearchResult);
            search = searchIn;
            updateAdapter();
            updateTitle();
            showFooterMoreCaches();
        }
        showProgress(false);
        hideLoading();
        invalidateOptionsMenuCompatible();
        if (arg0 instanceof AbstractSearchLoader) {
            switch (((AbstractSearchLoader) arg0).getAfterLoadAction()) {
                case CHECK_IF_EMPTY:
                    checkIfEmptyAndRemoveAfterConfirm();
                    break;
                case NO_ACTION:
                    break;
            }
        }
    }

    @Override
    public void onLoaderReset(final Loader<SearchResult> arg0) {
        //Not interesting
    }

    /**
     * Allow the title bar spinner to show the same subtitle like the activity itself would show.
     *
     */
    public CharSequence getCacheListSubtitle(@NonNull final AbstractList list) {
        // if this is the current list, be aware of filtering
        if (list.id == listId) {
            return getCurrentSubtitle();
        }
        // otherwise return the overall number
        final int numberOfCaches = list.getNumberOfCaches();
        if (numberOfCaches < 0) {
            return StringUtils.EMPTY;
        }
        return getCacheNumberString(getResources(), numberOfCaches);
    }

    /**
     * Calculate the subtitle of the current list depending on (optional) filters.
     *
     */
    private CharSequence getCurrentSubtitle() {
        if (search == null) {
            return getCacheNumberString(getResources(), 0);
        }
        final StringBuilder result = new StringBuilder();
        if (adapter.isFiltered()) {
            result.append(adapter.getCount()).append('/');
        }
        result.append(getCacheNumberString(getResources(), search.getCount()));
        return result.toString();
    }

    /**
     * Used to indicate if an action should be taken after the AbstractSearchLoader has finished
     */
    public enum AfterLoadAction {
        /** Take no action */
        NO_ACTION,
        /** Check if the list is empty and prompt for deletion */
        CHECK_IF_EMPTY
    }
}
