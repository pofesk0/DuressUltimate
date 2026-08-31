package duress.ultimate;

import java.util.Collections;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class MyDeviceAdminReceiver extends DeviceAdminReceiver {
        
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        disableFRP(context);
    }
  
    static void disableFRP(Context context) {
           try {
           DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
           if (!dpm.isDeviceOwnerApp(context.getPackageName())) return;
           ComponentName admin = new ComponentName(context, MyDeviceAdminReceiver.class);

           if (android.os.Build.VERSION.SDK_INT >= 30) {
                  android.app.admin.FactoryResetProtectionPolicy frpPolicy =       
                  new android.app.admin.FactoryResetProtectionPolicy.Builder()
                  .setFactoryResetProtectionAccounts(Collections.emptyList())        
                  .setFactoryResetProtectionEnabled(false)
                  .build();
            dpm.setFactoryResetProtectionPolicy(admin, frpPolicy);
                               
           } else {
                   android.os.Bundle restrictions = new android.os.Bundle();
                   restrictions.putBoolean("disableFactoryResetProtectionAdmin", true);
                   dpm.setApplicationRestrictions(admin, "com.google.android.gms", restrictions);
           }

           Intent intent = new Intent("com.google.android.gms.auth.FRP_CONFIG_CHANGED");
           intent.setPackage("com.google.android.gms");
           context.sendBroadcast(intent);
           
           } catch (Throwable t) {}
   }
        
    @Override
    public void onEnabled(Context context, Intent intent) {         
        Toast.makeText(context, "Device Admin Enabled", Toast.LENGTH_SHORT).show();        
    }
    
    @Override
    public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context, "Device Admin Disabled", Toast.LENGTH_SHORT).show();
    }
}
