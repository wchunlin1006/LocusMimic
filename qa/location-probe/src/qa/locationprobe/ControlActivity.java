package qa.locationprobe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** Sends typed extras that Android's am command cannot express as doubles. */
public final class ControlActivity extends Activity {
    private static final String TAG = "LOCUS_CONTROL_PROBE";
    private static final String TARGET_PACKAGE = "com.locusmimic.app";
    private static final String TARGET_RECEIVER =
            "com.locusmimic.app.manager.control.ControlReceiver";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        Intent source = getIntent();
        String command = source.getStringExtra("command");
        Intent target = new Intent(actionFor(command));
        target.setClassName(TARGET_PACKAGE, TARGET_RECEIVER);
        target.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        target.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        copyDouble(source, target, "latitude");
        copyDouble(source, target, "longitude");
        copyFloat(source, target, "accuracy");
        copyBoolean(source, target, "start");

        sendBroadcast(target);
        Log.i(TAG, "sent command=" + command + " extras=" + target.getExtras());
        finish();
    }

    private static String actionFor(String command) {
        if ("start".equals(command)) return TARGET_PACKAGE + ".action.START";
        if ("stop".equals(command)) return TARGET_PACKAGE + ".action.STOP";
        if ("set".equals(command)) return TARGET_PACKAGE + ".action.SET_LOCATION";
        throw new IllegalArgumentException("Unknown command: " + command);
    }

    private static void copyDouble(Intent source, Intent target, String name) {
        String value = source.getStringExtra(name);
        if (value != null) target.putExtra(name, Double.parseDouble(value));
    }

    private static void copyFloat(Intent source, Intent target, String name) {
        String value = source.getStringExtra(name);
        if (value != null) target.putExtra(name, Float.parseFloat(value));
    }

    private static void copyBoolean(Intent source, Intent target, String name) {
        String value = source.getStringExtra(name);
        if (value != null) target.putExtra(name, Boolean.parseBoolean(value));
    }
}
