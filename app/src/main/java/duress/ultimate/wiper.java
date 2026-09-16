package duress.ultimate;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

class wiper {
 
  static void isUSBwipe(Context context) {
    if (context==null) return;
    SharedPreferences p = context.getApplicationContext().createDeviceProtectedStorageContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
    boolean wipe = CryptoManager.getBoolean(p, CryptoManager.BFU_ALIAS, "usb_wipe", false);
    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);	   
    boolean isDeviceOwner = dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());	
    if (wipe && isDeviceOwner) {
       int flags = DevicePolicyManager.WIPE_RESET_PROTECTION_DATA | DevicePolicyManager.WIPE_EUICC | DevicePolicyManager.WIPE_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= 34) {            			
          dpm.wipeDevice(flags);
        } else {
          dpm.wipeData(flags);
        }	   														 
    } }

}
