package duress.ultimate;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class ProxyActivity extends Activity {

    private static final String PREFS = "prefs";
    private static final String SECRET_CODE_HASH = "secret_code_hash";
    private static final String SECRET_CODE_SALT = "secret_code_salt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context deContext = getApplicationContext().createDeviceProtectedStorageContext();
        SharedPreferences dePrefs = deContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        String savedHash = CryptoManager.getString(dePrefs, CryptoManager.DE_ALIAS, SECRET_CODE_HASH, null);
        String savedSalt = CryptoManager.getString(dePrefs, CryptoManager.DE_ALIAS, SECRET_CODE_SALT, null);

        Intent intent;
        if (savedHash != null && savedSalt != null) {
            intent = new Intent(this, CalculatorActivity.class);
        } else {
            intent = new Intent(this, EntryActivity.class);
        }

        startActivity(intent);
        finish();
    }
}
