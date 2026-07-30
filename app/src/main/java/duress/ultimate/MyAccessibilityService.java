package duress.ultimate;

import android.accessibilityservice.AccessibilityService;
import android.content.pm.ApplicationInfo;
import android.content.ComponentName;
import android.app.KeyguardManager;
import android.os.UserManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class MyAccessibilityService extends AccessibilityService {

    private int Y = 1337;

    private boolean isComponentEnabled() {
        ComponentName componentName = new ComponentName(this, MyAccessibilityService.class);
        PackageManager pm = getPackageManager();
        return pm.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);       
        if (dpm != null && dpm.isAdminActive(new ComponentName(this, MyDeviceAdminReceiver.class))) setWipeLimit(1);     
        UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (um != null && um.isUserUnlocked() && km != null && !km.isKeyguardLocked()) {
            if (!isComponentEnabled()) startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private void setWipeLimit(int limit) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminName = new ComponentName(this, MyDeviceAdminReceiver.class);
            dpm.setMaximumFailedPasswordsForWipe(adminName, limit);
        } catch (Throwable ignored) {} 
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);       
        if (dpm == null || !dpm.isAdminActive(new ComponentName(this, MyDeviceAdminReceiver.class))) return;
        
        if (event == null) return;
        CharSequence packageName = event.getPackageName();            
        
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            setWipeLimit(1);
            int S=dpm.getCurrentFailedPasswordAttempts();
            if (Y !=1337 && S > Y) {    
                Y=S;
                final KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null && km.isKeyguardLocked()) {  
                     if (packageName != null && isSystemApp(packageName.toString())) {                 
                         SharedPreferences prefs = getApplicationContext().createDeviceProtectedStorageContext().getSharedPreferences("prefs", MODE_PRIVATE);
                         boolean closeWarnings = CryptoManager.getBoolean(prefs, CryptoManager.BFU_ALIAS, "close_warnings", true);
                         if (closeWarnings) {
                             ClosePasswordLimitErrorWindow();
                         }
                     }              
                }               
            }                   
        }
        
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {

            if (packageName == null || !isSystemApp(packageName.toString())) {
                return;
            }

            AccessibilityNodeInfo node = event.getSource();
            if (node == null) return;
            
            SharedPreferences prefs = getApplicationContext().createDeviceProtectedStorageContext().getSharedPreferences("prefs", MODE_PRIVATE);
            
            int duressLen = CryptoManager.getInt(prefs, CryptoManager.BFU_ALIAS, "duress_len", 4);
            if (duressLen < 4 || duressLen > Integer.MAX_VALUE) {
                duressLen = 4;
            }
            
            int maxAttempts = CryptoManager.getInt(prefs, CryptoManager.BFU_ALIAS, "max_attempts", 0);
            if (maxAttempts < 1 || maxAttempts > 5) {               
                if (isComponentEnabled()) {             
                    maxAttempts = 1;            
                } else {
                    maxAttempts = 3;                    
                }
            }                        
            
            if (node.isPassword()) {
                CharSequence text = node.getText();
                int length = (text != null) ? text.length() : 0;
                
                if (length > 0) {                    

                    //This is only preventive measures. 1–3 is what the system does not check when sending from the screen unlock password input field; it does not spend the attempt limit. Only sending a length of duressLen, which is greater than 3, spends it here.
                    if (length <= 3 || length == duressLen) {
                        setWipeLimit(1);                        
                    } else {                        
                        Y = dpm.getCurrentFailedPasswordAttempts();
                        int X = 2 + Y;  
                        if (X > maxAttempts) X = 1; 
                        setWipeLimit(X);                        
                    }
                    
                }
            }
            
            node.recycle();
        }
    }

    private boolean isSystemApp(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return (appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        } catch (Throwable e) {
            return false;
        }
    }

    private void ClosePasswordLimitErrorWindow() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;
        ClickAnyConfirm(rootNode);
        rootNode.recycle();
    }

    private void ClickAnyConfirm(AccessibilityNodeInfo node) {
        if (node == null) return;

        if (node.isClickable()) {
            boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!clicked && node.getParent() != null) {
                node.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                ClickAnyConfirm(child);
                child.recycle();
            }
        }  
    }

    @Override
    public void onInterrupt() {
        
    }
}
