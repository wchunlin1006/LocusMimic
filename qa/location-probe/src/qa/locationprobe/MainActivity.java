package qa.locationprobe;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity implements LocationListener {
    private static final int REQUEST_LOCATION = 41;
    private static final String TAG = "LOCUS_PROBE";

    private LocationManager locationManager;
    private TextView output;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 54, 36, 36);
        root.setBackgroundColor(Color.rgb(247, 249, 252));

        TextView title = new TextView(this);
        title.setText("LocusMimic QA Probe");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(18, 38, 56));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        output = new TextView(this);
        output.setText("等待定位权限...");
        output.setTextSize(17);
        output.setTextColor(Color.rgb(30, 50, 65));
        output.setPadding(0, 42, 0, 42);
        output.setTextIsSelectable(true);
        root.addView(output, new LinearLayout.LayoutParams(-1, 0, 1));

        Button refresh = new Button(this);
        refresh.setText("刷新定位");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startLocation(); }
        });
        root.addView(refresh, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        ensurePermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPermission()) startLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationManager != null) locationManager.removeUpdates(this);
    }

    private boolean hasPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ensurePermission() {
        if (hasPermission()) {
            startLocation();
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_LOCATION && hasPermission()) startLocation();
        else output.setText("定位权限被拒绝");
    }

    private void startLocation() {
        if (!hasPermission()) {
            ensurePermission();
            return;
        }
        output.setText("等待 LocationManager 回调...");
        locationManager.removeUpdates(this);
        String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER};
        for (String provider : providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    Location last = locationManager.getLastKnownLocation(provider);
                    if (last != null) showLocation(last, "last-known");
                    locationManager.requestLocationUpdates(provider, 500L, 0f, this,
                            Looper.getMainLooper());
                }
            } catch (RuntimeException error) {
                Log.e(TAG, "provider=" + provider, error);
            }
        }
    }

    @Override public void onLocationChanged(Location location) { showLocation(location, "callback"); }

    private void showLocation(Location location, String source) {
        boolean mock = Build.VERSION.SDK_INT >= 31
                ? location.isMock()
                : location.isFromMockProvider();
        String vertical = Build.VERSION.SDK_INT >= 26 && location.hasVerticalAccuracy()
                ? String.format(Locale.US, "%.2f m", location.getVerticalAccuracyMeters())
                : "n/a";
        String speedAccuracy = Build.VERSION.SDK_INT >= 26 && location.hasSpeedAccuracy()
                ? String.format(Locale.US, "%.2f m/s", location.getSpeedAccuracyMetersPerSecond())
                : "n/a";
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date(location.getTime()));
        String text = String.format(Locale.US,
                "source: %s\nprovider: %s\nlatitude: %.6f\nlongitude: %.6f\naccuracy: %.2f m\nverticalAccuracy: %s\naltitude: %.2f m\nspeed: %.2f m/s\nspeedAccuracy: %s\nbearing: %.2f\nisMock: %s\ntime: %s",
                source, location.getProvider(), location.getLatitude(), location.getLongitude(),
                location.getAccuracy(), vertical, location.getAltitude(), location.getSpeed(),
                speedAccuracy, location.getBearing(), mock, when);
        output.setText(text);
        Log.i(TAG, text.replace('\n', ' '));
    }
}
