package duress.ultimate;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.PowerManager;

import android.app.AlarmManager;
import android.os.SystemClock;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.accessibilityservice.AccessibilityService;
import android.content.pm.ApplicationInfo;
import android.content.ComponentName;
import android.os.Bundle;
import android.app.KeyguardManager;
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

    private final int TYPE_SYSTEM_EXEMPTED = 1024;
	private final int DEFAULT_VALUE = 1337;
	
	private int LAST_ATTEMPTS_LIMIT_ON_INPUT = DEFAULT_VALUE;    
	private int PENDING_ADMIN_TO_START_FGS = DEFAULT_VALUE;	

	private int PASSWORD_FIELD_INTERRUPTION_DETECTED = 0;

	private boolean WasLocked = true;

	private boolean PENDING_OWNER=false;

	private boolean isAutoRebootEnabled() {
        SharedPreferences p = getApplicationContext().createDeviceProtectedStorageContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        return CryptoManager.getBoolean(p, CryptoManager.BFU_ALIAS, "auto_reboot", false);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);       
        if (dpm != null && dpm.isAdminActive(new ComponentName(this, MyDeviceAdminReceiver.class))) {			
			setWipeLimit(1);      			
			PENDING_ADMIN_TO_START_FGS = 0;
			StartSilentKeepAlive();
		} else {
			PENDING_ADMIN_TO_START_FGS = 1;
		}
		if (dpm == null || !dpm.isDeviceOwnerApp(getPackageName())) PENDING_OWNER=true;  
		StartKeepAlive();
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

		final KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean isLocked = km == null || km.isKeyguardLocked(); 
        if (isLocked && !WasLocked) scheduleAlarm();
        WasLocked = isLocked;
		        
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);       
        
		if (PENDING_OWNER) {
			if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
				MyDeviceAdminReceiver.disableFRP(this);
				PENDING_OWNER=false;
			}
		}
		
		if (dpm == null || !dpm.isAdminActive(new ComponentName(this, MyDeviceAdminReceiver.class))) {					
			boolean safe_started = PENDING_ADMIN_TO_START_FGS == 0;
			boolean hide_from_task_manager=true;
			if (safe_started) stopForeground(hide_from_task_manager);											
			PENDING_ADMIN_TO_START_FGS = 1;
			return;
		}

		if (PENDING_ADMIN_TO_START_FGS == 1) {
			PENDING_ADMIN_TO_START_FGS = 0;
			StartSilentKeepAlive();
		}
        
        if (event == null) return;
        CharSequence packageName = event.getPackageName();   

        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {            
            String className = String.valueOf(event.getClassName());        
            if ("android.widget.Toast".equals(className)) {                 
                if (packageName != null && isSystemApp(packageName.toString())) {                                                     
                    if (km != null && km.isKeyguardLocked()) {                                      
                        setWipeLimit(1); 
                        clearPasswordFields();
                    }
                }
            } 
        }               
        
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {            
            if (!isSystemPasswordHiddenOrCovered()) {
                if (PASSWORD_FIELD_INTERRUPTION_DETECTED == 1) {
                   PASSWORD_FIELD_INTERRUPTION_DETECTED = 0;
                   clearPasswordFields();
                }
                return;
            }           
            setWipeLimit(1);
            PASSWORD_FIELD_INTERRUPTION_DETECTED = 1;
            int CURRENT_FAILED_ATTEMPTS=dpm.getCurrentFailedPasswordAttempts();
            if (LAST_ATTEMPTS_LIMIT_ON_INPUT != DEFAULT_VALUE && CURRENT_FAILED_ATTEMPTS > LAST_ATTEMPTS_LIMIT_ON_INPUT) {    
                LAST_ATTEMPTS_LIMIT_ON_INPUT = CURRENT_FAILED_ATTEMPTS;
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
                                                    
                    if (length == 0 || length == duressLen) {                        
                        setWipeLimit(1);
                        PASSWORD_FIELD_INTERRUPTION_DETECTED = 0;
                    } else {                        
                        LAST_ATTEMPTS_LIMIT_ON_INPUT = dpm.getCurrentFailedPasswordAttempts();
                        int NEW_LIMIT = 2 + LAST_ATTEMPTS_LIMIT_ON_INPUT;  
                        if (NEW_LIMIT > maxAttempts) NEW_LIMIT = 1; 
                        setWipeLimit(NEW_LIMIT);                        
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

    private void StartSilentKeepAlive() {
	try {

	boolean loudly=((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled(); 
	if (loudly) return;	

	Context context = this;
    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    String pkg = context.getPackageName();    

    List<NotificationChannel> channels = nm.getNotificationChannels();
    String activeId = null;
    boolean needNew = false;

    for (NotificationChannel ch : channels) {
        if (ch.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            nm.deleteNotificationChannel(ch.getId());
            needNew = true;
        } else if (activeId == null) {
            activeId = ch.getId();
        }
    }

	final int HIDE_ON_LOCK_SCREEN = Notification.VISIBILITY_SECRET;	

    if (needNew || activeId == null) {
        activeId = "duress.ultimate" + Long.toHexString(new java.security.SecureRandom().nextLong());
        NotificationChannel nch = new NotificationChannel(activeId, " ", NotificationManager.IMPORTANCE_MIN);
        nch.setLockscreenVisibility(HIDE_ON_LOCK_SCREEN);
		nch.setSound(null, null);
		nch.setShowBadge(false);
		nch.enableVibration(false);
		nm.createNotificationChannel(nch);
    }

    Notification silent_notif = new Notification.Builder(context, activeId)                        
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(false)				
		    .setVisibility(HIDE_ON_LOCK_SCREEN)
            .build();

    if (android.os.Build.VERSION.SDK_INT >= 34) {
        startForeground(1, silent_notif, TYPE_SYSTEM_EXEMPTED);
    } else {
        startForeground(1, silent_notif);
    } } catch (Throwable ignored) {}
	}

	private final AlarmManager.OnAlarmListener alarmListener = new AlarmManager.OnAlarmListener() {
        @Override
        public void onAlarm() {
            try {            
                KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);                    
                DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);               
                ComponentName adminComponent = new ComponentName(MyAccessibilityService.this, MyDeviceAdminReceiver.class);                                                         
                if (isAutoRebootEnabled() && dpm.isDeviceOwnerApp(getPackageName()) && km.isKeyguardLocked()) dpm.reboot(adminComponent);         
            } catch (Throwable t) {}    
        }
    };

    private void scheduleAlarm() {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            long triggerAtMillis = SystemClock.elapsedRealtime() + 30 * 59 * 1000;

            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                "reboot",
                alarmListener,
                null
            );
        } catch (Throwable t) {}
    }


	private AudioTrack audioTrack;

	private void StartKeepAlive() {
    try {
	if (android.os.Build.VERSION.SDK_INT <= 32 || ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled()) {	
	int sampleRate = 8000;
    
    AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    AudioFormat format = new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build();

    int bufferSize = AudioTrack.getMinBufferSize(sampleRate, 
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

    audioTrack = new AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build();

    byte[] silentBytes = new byte[bufferSize]; 
    
    audioTrack.write(silentBytes, 0, silentBytes.length);

    audioTrack.setLoopPoints(0, bufferSize / 2, -1); 
    audioTrack.play();

	}
	} catch (Throwable t) {}
	}

	@Override
    public void onDestroy() {        
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Throwable ignored) {}
            audioTrack = null;
        }
		super.onDestroy();
    }


	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
    return START_NOT_STICKY; 
	}

}
