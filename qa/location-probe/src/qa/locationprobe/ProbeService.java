package qa.locationprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.Locale;

public final class ProbeService extends Service implements LocationListener {
    private static final String TAG = "LOCUS_PROBE_SERVICE";
    private LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        String channelId = "locus_probe_qa";
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
                channelId, "Locus QA Probe", NotificationManager.IMPORTANCE_LOW));
        Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("Locus QA Probe")
                .setContentText("Sampling LocationManager")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
        startForeground(42, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "permission=denied");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        manager.removeUpdates(this);
        for (String provider : new String[]{LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, 250L, 0f, this,
                            Looper.getMainLooper());
                }
            } catch (RuntimeException error) {
                Log.e(TAG, "provider=" + provider, error);
            }
        }
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                Log.i(TAG, "sample=timeout");
                stopSelf();
            }
        }, 8000L);
        return START_NOT_STICKY;
    }

    @Override
    public void onLocationChanged(Location location) {
        boolean mock = Build.VERSION.SDK_INT >= 31
                ? location.isMock()
                : location.isFromMockProvider();
        String vertical = Build.VERSION.SDK_INT >= 26 && location.hasVerticalAccuracy()
                ? String.format(Locale.US, "%.2f", location.getVerticalAccuracyMeters())
                : "n/a";
        Log.i(TAG, String.format(Locale.US,
                "provider=%s lat=%.6f lon=%.6f accuracy=%.2f verticalAccuracy=%s altitude=%.2f speed=%.2f isMock=%s time=%d",
                location.getProvider(), location.getLatitude(), location.getLongitude(),
                location.getAccuracy(), vertical, location.getAltitude(), location.getSpeed(),
                mock, location.getTime()));
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (manager != null) manager.removeUpdates(this);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
