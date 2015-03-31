package com.reelyactive.blesdk.application;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import com.reelyactive.blesdk.service.BleService;
import com.reelyactive.blesdk.service.BleServiceCallback;
import com.reelyactive.blesdk.support.ble.ScanFilter;
import com.reelyactive.blesdk.support.ble.ScanResult;

import java.util.concurrent.atomic.AtomicInteger;

import hugo.weaving.DebugLog;

/**
 * This class provides a convenient way to make your application aware of any Reelceivers.
 * <p/>
 * Register it using {@link android.app.Application#registerActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks)}<br/>
 * <p/>
 * Extend it to customize the behaviour of your application.
 * <p/>
 * The default behaviour is to bind to the {@link BleService} as soon as the app is created.
 *
 * @see android.app.Application.ActivityLifecycleCallbacks
 */
@SuppressWarnings("unused")
public abstract class ReelyAwareApplicationCallback implements Application.ActivityLifecycleCallbacks, BleServiceCallback {
    private static final String TAG = ReelyAwareApplicationCallback.class.getSimpleName();
    private final Context context;
    private final ServiceConnection serviceConnection;
    private boolean isBound = false;
    private final AtomicInteger activityCount = new AtomicInteger();
    private Activity current;
    private BleService service;

    /**
     * As soon as the component is created, we bindBleService to the {@link BleService}
     *
     * @param context The application's {@link Context}
     */
    public ReelyAwareApplicationCallback(Context context) {
        this.context = context;
        this.serviceConnection = new BleServiceConnection();
        bindBleService();
    }

    /**
     * The default behaviour is to check if a {@link ReelyAwareActivity} is running, and call a scan if so.<br/>
     * See {@link #startScan()} and {@link #getScanType()}
     *
     * @param activity The resumed {@link Activity}
     */
    @Override
    public void onActivityResumed(Activity activity) {
        Log.d(TAG, "activity resumed");
        current = activity;
        if (isReelyAware(activity) && activityCount.incrementAndGet() == 1 && isBound) {
            updateScanType(getScanType());
            startScan();
        }
    }

    /**
     * The default behaviour is to check if any {@link ReelyAwareActivity} is still running, and call a scan if so.<br/>
     * See {@link #startScan()} and {@link #getScanType()}
     *
     * @param activity The resumed {@link Activity}.
     */
    @Override
    public void onActivityPaused(Activity activity) {
        Log.d(TAG, "activity paused");
        current = null;
        if (isReelyAware(activity) && activityCount.decrementAndGet() <= 0 && isBound) {
            updateScanType(getScanType());
            startScan();
        }
    }

    /**
     * This method sends a scan request to the {@link BleService}.
     */
    protected void startScan() {
        service.startScan();
    }

    /**
     * This method sets the scan type of the {@link BleService}.
     *
     * @param scanType The {@link BleService.ScanType scan type}
     */
    protected void updateScanType(BleService.ScanType scanType) {
        service.setScanType(scanType);
    }

    /**
     * This method sets the scan filter of the {@link BleService}.
     *
     * @param scanFilter The {@link ScanFilter scan filter}
     */
    protected void updateScanFilter(ScanFilter scanFilter) {
        service.setScanFilter(scanFilter);
    }

    /**
     * Override this in order to chose which scan filter is to be used before {@link #startScan} is called when the service starts.
     *
     * @return The {@link ScanFilter} to be used in the current application state.
     */
    protected ScanFilter getScanFilter() {
        return new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString("7265656C-7941-6374-6976-652055554944")).build();
    }

    /**
     * Override this in order to chose which kind of scan is to be used before {@link #startScan} is called when the service starts.
     *
     * @return The {@link BleService.ScanType} to be used in the current application state.
     */
    protected BleService.ScanType getScanType() {
        return activityCount.get() == 0 ? BleService.ScanType.LOW_POWER : BleService.ScanType.ACTIVE;
    }

    /**
     * Get the number of {@link ReelyAwareActivity ReelyAwareActivities} currently running (0 or 1 basically)
     *
     * @return The number of {@link ReelyAwareActivity ReelyAwareActivities} running
     */
    protected int getActivityCount() {
        return activityCount.get();
    }

    /**
     * This method is called when a {@link BleService.Event} is received.<br/>
     * Its default behaviour is to notify the currently running {@link ReelyAwareActivity} (if any).<br/>
     * Override this and you can customize the behaviour of the application.
     *
     * @param event The {@link BleService.Event} received from the {@link BleService}.
     */
    @Override
    public void onBleEvent(BleService.Event event, Object data) {
        switch (event) {
            case IN_REGION:
                Log.d(TAG, "Application entered region");
                if (isReelyAware(getCurrentActivity())) {
                    ((ReelyAwareActivity) getCurrentActivity()).onEnterRegion((ScanResult) data);
                }
                break;
            case OUT_REGION:
                Log.d(TAG, "Application left region");
                if (isReelyAware(getCurrentActivity())) {
                    ((ReelyAwareActivity) getCurrentActivity()).onLeaveRegion((ScanResult) data);
                }
                break;
            case SCAN_STARTED:
                Log.d(TAG, "Scan started");
                if (isReelyAware(getCurrentActivity())) {
                    ((ReelyAwareActivity) getCurrentActivity()).onScanStarted();
                }
                break;
            case SCAN_STOPPED:
                Log.d(TAG, "Scan stopped");
                if (isReelyAware(getCurrentActivity())) {
                    ((ReelyAwareActivity) getCurrentActivity()).onScanStopped();
                }
                break;
            default:
                Log.d(TAG, "Unhandled BLE Event : " + event);
                break;
        }
    }

    /**
     * Access the application {@link Context}.
     *
     * @return The application {@link Context}.
     */
    protected Context getContext() {
        return context;
    }

    /**
     * Get currently running Activity.
     *
     * @return The currently running Activity.
     */
    protected Activity getCurrentActivity() {
        return current;
    }

    /**
     * Get access to the underlying service.
     *
     * @return The {@link com.reelyactive.blesdk.service.BleService} instance running.
     */
    protected BleService getBleService() {
        return service;
    }

    /**
     * Find out if an {@link Activity} implements {@link ReelyAwareActivity}
     *
     * @param activity The {@link Activity}
     * @return true if the {@link Activity} implements {@link ReelyAwareActivity}, false if not.
     */
    protected static boolean isReelyAware(Activity activity) {
        return activity != null && ReelyAwareActivity.class.isInstance(activity);
    }

    /**
     * This method is called when the {@link BleService is available}.<br/>
     * The default behaviour is to start a scan.
     */
    @DebugLog
    protected void onBleServiceBound() {
        updateScanType(getScanType());
        updateScanFilter(getScanFilter());
        startScan();
    }


    /**
     * ************* PRIVATE STUFF ******************
     */

    protected void bindBleService() {
        context.bindService(new Intent(context, BleService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }


    protected void unbindBleService() {
        if (isBound) {
            service.unregisterClient(this);
            context.unbindService(serviceConnection);
        }
    }

    final class BleServiceConnection implements ServiceConnection {
        @Override
        @DebugLog
        public void onServiceConnected(ComponentName name, IBinder remoteService) {
            isBound = true;
            service = ((BleService.LocalBinder) remoteService).getService();
            service.registerClient(ReelyAwareApplicationCallback.this);
            onBleServiceBound();
        }

        @Override
        @DebugLog
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    }

}
