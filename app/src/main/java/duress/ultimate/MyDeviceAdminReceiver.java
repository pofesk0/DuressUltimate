package duress.ultimate;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class MyDeviceAdminReceiver extends DeviceAdminReceiver {
        
    @Override
    public void onEnabled(Context context, Intent intent) { 
        setWipeLimit(context);
        Toast.makeText(context, "Device Admin Enabled", Toast.LENGTH_SHORT).show();        
    }

    private void setWipeLimit(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminName = new ComponentName(context, MyDeviceAdminReceiver.class);
            dpm.setMaximumFailedPasswordsForWipe(adminName, 1);
        } catch (Throwable ignored) {} 
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context, "Device Admin Disabled", Toast.LENGTH_SHORT).show();
    }
}
