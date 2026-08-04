package duress.ultimate;

import android.accessibilityservice.AccessibilityService;
import android.content.pm.ApplicationInfo;
import android.content.ComponentName;
import android.os.Bundle;
import android.app.KeyguardManager;
import android.os.UserManager;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class MyAccessibilityService extends AccessibilityService {

    private int Y = 1337;

    private int dexA=0;

    private boolean isComponentEnabled() {
        ComponentName componentName = new ComponentName(this, MainActivity.class);
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

        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {            
            String className = String.valueOf(event.getClassName());        
            if ("android.widget.Toast".equals(className)) {                 
                if (packageName != null && isSystemApp(packageName.toString())) {                                                     
                    final KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);            
                    if (km != null && km.isKeyguardLocked()) {                                      
                        setWipeLimit(1); 
                        clearPasswordFields();
                    }
                }
            } 
        }               
        
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {            
            if (!isSystemPasswordHiddenOrCovered()) {
                if (dexA==1) {
                   dexA=0;
                   clearPasswordFields();
                }
                return;
            }           
            setWipeLimit(1);
            dexA=1;
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
            
            int maxAttempts = CryptoManager.getInt(prefs, CryptoManager.BFU_ALIAS, "max_attempts", 1);
            if (maxAttempts < 1 || maxAttempts > 5) {                                       
                    maxAttempts = 1;                            
            }                        
            
            if (node.isPassword()) {
                CharSequence text = node.getText();
                int length = (text != null) ? text.length() : 0;
                                                    
                    //This is only preventive measures. 0–3 is what the system does not check when sending from the screen unlock password input field; it does not spend the attempt limit. Only sending a length of duressLen, which is greater than 3, spends it here.
                    if (length <= 3 || length == duressLen) {                        
                        setWipeLimit(1);
                        dexA=0;
                    } else {                        
                        Y = dpm.getCurrentFailedPasswordAttempts();
                        int X = 2 + Y;  
                        if (X > maxAttempts) X = 1; 
                        setWipeLimit(X);                        
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
    
    private boolean isSystemPasswordHiddenOrCovered() {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) return true;

        Rect passwordBounds = null;
        int passwordWindowIndex = -1; 
  
        for (int i = 0; i < windows.size(); i++) {   
            AccessibilityWindowInfo window = windows.get(i);
            AccessibilityNodeInfo root = window.getRoot();    
            if (root != null) {       
                CharSequence pkgName = root.getPackageName();         
                if (pkgName != null && isSystemApp(pkgName.toString())) {         
                    passwordBounds = findPasswordBounds(root);         
                    if (passwordBounds != null) {             
                        passwordWindowIndex = i;
                        root.recycle();           
                        break;         
                    }        
                }        
                root.recycle();     
            }
        }
   
        boolean isCovered = false;

        if (passwordBounds == null) {  
            isCovered = true;
        } else {
            int passwordArea = passwordBounds.width() * passwordBounds.height();
            
            if (passwordArea <= 0) {
                isCovered = true;
            } else {
                for (int i = passwordWindowIndex + 1; i < windows.size(); i++) {  
                    AccessibilityWindowInfo window = windows.get(i);
                    Rect windowBounds = new Rect(); 
                    window.getBoundsInScreen(windowBounds);   
                    
                    Rect intersection = new Rect();   
                    if (intersection.setIntersect(passwordBounds, windowBounds)) {      
                        int intersectionArea = intersection.width() * intersection.height();     
                        double coveredPercent = (double) intersectionArea / passwordArea;                   
                        if (coveredPercent >= 0.7) {         
                            isCovered = true;
                            break; 
                        }    
                    } 
                }
            }
        }

        for (AccessibilityWindowInfo window : windows) {
            window.recycle();
        }

        return isCovered;
    }

    private Rect findPasswordBounds(AccessibilityNodeInfo node) {   
        if (node == null) return null;   
        
        if (node.isPassword()) {   
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            return bounds;  
        }  
        
        for (int i = 0; i < node.getChildCount(); i++) { 
            AccessibilityNodeInfo child = node.getChild(i); 
            if (child != null) {           
                Rect foundBounds = findPasswordBounds(child);          
                child.recycle();
                if (foundBounds != null) {               
                    return foundBounds;            
                }                      
            }    
        }   
        return null;
    }


    private void clearPasswordFields() {
        try {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;
        clearPasswordFieldsRecursive(rootNode);
        rootNode.recycle();
        } catch (Throwable e) {}
    }

    private void clearPasswordFieldsRecursive(AccessibilityNodeInfo node) {
        if (node == null) return;
        
        if (node.isPassword() && node.isEditable()) {
            CharSequence pkg = node.getPackageName();
            if (pkg != null && isSystemApp(pkg.toString())) {               
                CharSequence text = node.getText();
                if (text != null && text.length() >= 4) {
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                }
            }
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                clearPasswordFieldsRecursive(child);
                child.recycle();
            }
        }
    }

}
