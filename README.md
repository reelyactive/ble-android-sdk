# reelyactive-ble-android-sdk
This SDK allows you to scan beacons and advertise as a beacon.

The SDK is compatible with all versions of Android supporting BLE (ie. 4.3+).

Gradle
======

Add it to your project :

```groovy
compile 'com.reelyactive:blesdk:0.3.2'
```

Use
===
Simple way
----------

Add this call in your Application class :

```java
registerActivityLifecycleCallbacks(new ReelyAwareApplicationCallback(this) {
    [...]
});
```

And make sure any activity which needs to get notified about bluetooth event implement ReelyAwareActivity :
```java
public class ReelyAwareScanActivity extends Activity implements ReelyAwareActivity {

    @Override
    public void onScanStarted() {}

    @Override
    public void onScanStopped() {}

    @Override
    public void onEnterRegion(ScanResult beacon) {}

    @Override
    public void onLeaveRegion(ScanResult beacon) {}
}
```