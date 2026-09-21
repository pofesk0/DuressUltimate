package duress.ultimate;

import android.app.Activity;

public class ZeroActivity extends Activity {
    @Override
    protected void onResume() {
        super.onResume();
        finish();
    }
}
