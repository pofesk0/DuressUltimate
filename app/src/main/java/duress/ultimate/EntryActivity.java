package duress.ultimate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.app.KeyguardManager;

public class EntryActivity extends Activity {

    static boolean isLogged=true;
	
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);       
		KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguardManager.isKeyguardSecure()) {
		isLogged=false;	
        Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(null, null);         
        startActivityForResult(intent, 1337);        
        } else {
		navigateToMainActivity();
        }
    }
	
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if (requestCode == 1337) {
			if (resultCode == RESULT_OK) {			
				navigateToMainActivity();
			} else {
                isLogged=false;	
				finishAndRemoveTask();
			}
		}
	}

	private void navigateToMainActivity() {        
        isLogged=true;
		startActivity(new Intent(this, MainActivity.class));
        finish();
    }

}
