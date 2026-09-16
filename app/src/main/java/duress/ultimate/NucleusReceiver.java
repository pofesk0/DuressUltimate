package duress.ultimate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NucleusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {                
          wiper.isUSBwipe(context);                  
    }
}
