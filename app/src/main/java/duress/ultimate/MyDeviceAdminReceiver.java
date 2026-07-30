package duress.ultimate;

import android.app.*;
import android.app.admin.*;
import android.content.*;
import android.widget.*;

public class MyDeviceAdminReceiver extends DeviceAdminReceiver {
    	
    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context,"Device Admin Enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context,"Device Admin Disabled", Toast.LENGTH_SHORT).show();
    }
}
