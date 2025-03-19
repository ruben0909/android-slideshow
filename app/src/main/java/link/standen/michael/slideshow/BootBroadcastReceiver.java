package link.standen.michael.slideshow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_REBOOT.equals(intent.getAction())) {

            // Iniciar tu aplicación después de que se complete el arranque
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("link.standen.michael.slideshow");
            if (launchIntent != null) {
                context.startActivity(launchIntent);
            }
        }
    }
}
