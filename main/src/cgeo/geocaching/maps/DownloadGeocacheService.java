package cgeo.geocaching.maps;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.SparseArray;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

import cgeo.geocaching.R;
import cgeo.geocaching.models.Geocache;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.utils.CancellableHandler;
import cgeo.geocaching.utils.Log;
import rx.Observable;
import rx.Observer;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by matej on 26.8.16.
 */
public class DownloadGeocacheService extends Service {


    public static final String EXTRA_REQUEST = DownloadRequest.class.getName();
    private static final String EXTRA_CANCEL_NOTIFICATION_ID = "cancel_notification_id";


    private NotificationManager mNotifyManager;
    protected final IdGenerator idGenerator = new IdGenerator();
    protected final SparseArray<NotificationUpdater> notifications = new SparseArray<>();


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    NotificationManager getNotifyManager() {
        if (mNotifyManager == null) {
            mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return mNotifyManager;
    }

    private NotificationCompat.Builder createNotificationBuilder(int id) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.drawable.cgeo);
        builder.setCategory(NotificationCompat.CATEGORY_PROGRESS);
        CharSequence title = getResources().getText(R.string.cache_dialog_offline_save_message);
        builder.setContentTitle(title);
        builder.setOngoing(true);
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.setTicker(title);
        PendingIntent cancelIntent = createCancelPendingIntent(id);
        builder.addAction(
                R.drawable.ic_menu_remove,
                getResources().getText(R.string.waypoint_cancel_edit), // TODO add new resource copied from waypoint_cancel_edit
                cancelIntent
        );
        builder.setDeleteIntent(cancelIntent); // probably useless, it is ongoing (= not deletable) notification
        builder.setContentIntent(cancelIntent); // what would user expects on click?
        return builder;
    }


    private PendingIntent createCancelPendingIntent(int id) {

        Intent intent = new Intent(this, getClass());
        intent.putExtra(EXTRA_CANCEL_NOTIFICATION_ID, id);
        return PendingIntent.getService(
                this,
                id, // id must be passed here, otherwise intent is cached with old id
                intent,
                PendingIntent.FLAG_ONE_SHOT // no more flags needed, pendingintent is used only once
        );
    }

    public void addRequest(DownloadRequest req) {
        int id = idGenerator.next();
        NotificationUpdater updater = new NotificationUpdater(id);
        notifications.put(id, updater);
        downloadCaches(createNotificationBuilder(id), req, updater.getHandler())
                .subscribe(updater);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // intent for canceling pending downloading?
        int notificationToCancel = intent.getIntExtra(EXTRA_CANCEL_NOTIFICATION_ID, 0);
        if (notificationToCancel != 0) {
            Log.i("DOWNLOAD: notification to cancel: " + notificationToCancel);
            NotificationUpdater updater = notifications.get(notificationToCancel);
            if (updater != null) {
                updater.cancel();
            } else {
                Log.i("DOWNLOAD: notificationUpdater not found");
            }
        }

        // intent for adding new downloading?
        Serializable s = intent.getSerializableExtra(EXTRA_REQUEST);
        if (s instanceof DownloadRequest) {
            addRequest((DownloadRequest) s);
        }

        return START_NOT_STICKY;
    }

    public static class DownloadRequest implements Serializable {

        public final Set<String> geocodes;
        public final Set<Integer> lists;

        DownloadRequest(Set<String> geocodes, Set<Integer> lists) {
            this.geocodes = Collections.unmodifiableSet(geocodes);
            this.lists = Collections.unmodifiableSet(lists);
        }
    }


    Observable<Notification> downloadCaches(final NotificationCompat.Builder builder, final DownloadRequest request, final CancellableHandler handler) {
        final long startTime = System.currentTimeMillis();
        final Resources res = getResources();
        return Observable.from(request.geocodes)
                .flatMap(new Func1<String, Observable<String>>() {
                    @Override
                    public Observable<String> call(String geocode) {
                        return Observable.just(geocode)
                                .doOnNext(new Action1<String>() {
                                    @Override
                                    public void call(String geocode) {
                                        if (!DataStore.isOffline(geocode, null)) {
                                            Geocache.storeCache(null, geocode, request.lists, false, handler);
                                        }
                                    }
                                }).subscribeOn(Schedulers.io());
                    }
                })
                .startWith("first") // generate first item to show first notification
                .filter(new Func1<String, Boolean>() { // stop generating notification if handler is cancelled
                    @Override
                    public Boolean call(String s) {
                        return handler == null || !handler.isCancelled();
                    }
                })
                .map(new Func1<String, Notification>() {
                    private int done = -1; // done is incremented first and first String is not geocache, only to start notification

                    @Override
                    public Notification call(String downloadedGeocode) {
                        Log.i("DOWNLOAD sending to notification: " + downloadedGeocode);
                        done++;
                        int total = request.geocodes.size();
                        builder.setProgress(total, done, false);
                        builder.setContentTitle("Downloaded " + (done) + "/" + total);


                        if (done > 0) {
                            final int secondsElapsed = (int) ((System.currentTimeMillis() - startTime) / 1000);
                            final int secondsRemaining;

                            secondsRemaining = (total - done) * secondsElapsed / done;

                            if (secondsRemaining < 40) {
                                builder.setContentText(res.getString(R.string.caches_downloading) + " " + res.getString(R.string.caches_eta_ltm));
                            } else {
                                final int minsRemaining = secondsRemaining / 60;
                                builder.setContentText(res.getString(R.string.caches_downloading) + " " + res.getQuantityString(R.plurals.caches_eta_mins, minsRemaining, minsRemaining));
                            }
                        } else {
                            builder.setContentText(res.getString(R.string.caches_downloading) + " ?");
                        }

                        return builder.build();
                    }
                });

    }


    private class NotificationUpdater implements Observer<Notification> {

        final int id;
        private final CancellableHandler handler;

        NotificationUpdater(int id) {
            this.id = id;
            handler = new CancellableHandler() {
                @Override
                protected void handleRegularMessage(Message message) {
                    // TODO update notification on every message?
                }
            };
        }

        @Override
        public void onCompleted() {
            cancel();
        }

        @Override
        public void onError(Throwable e) {
            // oops what to do here?
            Log.e("Error during async downloading caches", e);
        }

        @Override
        public void onNext(Notification notification) {
            getNotifyManager().notify(id, notification);
        }

        public void cancel() {
            handler.cancel(); // set handler as cancelled, storing cache will be canceled
            notifications.delete(id);  // remove reference to this NotificationUpdater, it is no more needed
            getNotifyManager().cancel(id); // and remove notification from status bar
        }

        public CancellableHandler getHandler() {
            return handler;
        }
    }

    private class IdGenerator {
        private int id = 0;

        int next() {
            id++;
            if (id == 0) {
                Log.w("Id generator for notification overflow");
                id = 1; // avoid zero as id
            }
            return id;
        }
    }

}


