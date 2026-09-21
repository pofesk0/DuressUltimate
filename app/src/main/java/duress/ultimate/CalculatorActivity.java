package duress.ultimate;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class CalculatorActivity extends Activity {

    private static final String PREFS = "prefs";
    private static final String SECRET_CODE_HASH = "secret_code_hash";
    private static final String SECRET_CODE_SALT = "secret_code_salt";

    private static final int COLOR_BG             = 0xFFFFFFFF;
    private static final int COLOR_TEXT           = 0xFF202124;
    private static final int COLOR_TEXT_SECONDARY = 0xFF5F6368;
    private static final int COLOR_ERROR          = 0xFFD93025;

    private static final int COLOR_DIGIT_BG   = 0xFFF1F3F4;
    private static final int COLOR_OP_BG      = 0xFFFFFFFF;
    private static final int COLOR_OP_STROKE  = 0xFFD93025;
    private static final int COLOR_EQUALS_BG  = 0xFFD93025;
    private static final int COLOR_RED_TEXT  = 0xFFC5221F;

    private static final int MAX_NUMBER_LENGTH = 15;

    private TextView expressionView;
    private TextView resultView;

    private String committed = "";
    private String currentInput = "";
    private String lastExpression = "";
    private boolean justEvaluated = false;
    private boolean showError = false;

    private volatile int counter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
                
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN);
            
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setFitsSystemWindows(true);

        LinearLayout displayBox = new LinearLayout(this);
        displayBox.setOrientation(LinearLayout.VERTICAL);
        displayBox.setGravity(Gravity.BOTTOM | Gravity.END);
        displayBox.setPadding(dp(28), dp(16), dp(28), dp(20));
        root.addView(displayBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        expressionView = new TextView(this);
        expressionView.setGravity(Gravity.END);
        expressionView.setTextColor(COLOR_TEXT);
        expressionView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        displayBox.addView(expressionView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        resultView = new TextView(this);
        resultView.setGravity(Gravity.END);
        resultView.setTextColor(COLOR_TEXT_SECONDARY);
        resultView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resultParams.topMargin = dp(4);
        displayBox.addView(resultView, resultParams);

        LinearLayout keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);
        keypad.setPadding(dp(12), dp(8), dp(12), dp(16));
        root.addView(keypad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.6f));

        String[][] rows = {
                {"AC", "( )", "%", "÷"},
                {"7",  "8",   "9", "×"},
                {"4",  "5",   "6", "−"},
                {"1",  "2",   "3", "+"},
                {"0",  ".",   "⌫", "="}
        };

        for (String[] row : rows) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            keypad.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

            for (String label : row) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
                p.setMargins(dp(4), dp(4), dp(4), dp(4));
                line.addView(createButton(label), p);
            }
        }

        setContentView(root);
        refresh();
    }

    private Button createButton(final String label) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setStateListAnimator(null);
        btn.setMinWidth(0);
        btn.setMinHeight(0);
        btn.setMinimumWidth(0);
        btn.setMinimumHeight(0);
        btn.setPadding(0, 0, 0, 0);
        btn.setGravity(Gravity.CENTER);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        int bg;
        int fg;
        int stroke = 0;
        int ripple = 0x22000000;
        float size;

        if (label.equals("=")) {
            bg = COLOR_EQUALS_BG;
            fg = 0xFFFFFFFF;
            ripple = 0x44FFFFFF;
            size = 34;
        } else if (label.matches("[+−×÷]")) {
            bg = COLOR_OP_BG;
            fg = COLOR_RED_TEXT;
            stroke = COLOR_OP_STROKE;
            ripple = 0x22D93025;
            size = 32;
        } else if (label.equals("AC")) {
            bg = COLOR_OP_BG;
            fg = COLOR_RED_TEXT;
            stroke = COLOR_OP_STROKE;
            ripple = 0x22D93025;
            size = 22;
        } else if (label.equals("( )") || label.equals("%") || label.equals("⌫")) {
            bg = COLOR_OP_BG;
            fg = COLOR_RED_TEXT;
            stroke = COLOR_OP_STROKE;
            ripple = 0x22D93025;
            size = 26;
        } else {
            bg = COLOR_DIGIT_BG;
            fg = COLOR_TEXT;
            size = 30;
        }

        btn.setTextColor(fg);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        btn.setBackground(makeBackground(bg, ripple, stroke, stroke != 0 ? dp(2) : 0));

        btn.setOnClickListener(v -> handleButtonClick(label));

        if (label.equals("⌫")) {
            btn.setOnLongClickListener(v -> {
                resetAll();
                refresh();
                return true;
            });
        }
        return btn;
    }

    private Drawable makeBackground(int fill, int rippleColor) {
        return makeBackground(fill, rippleColor, 0, 0);
    }

    private Drawable makeBackground(int fill, int rippleColor, int strokeColor, int strokeWidthPx) {
        float radius = dp(30);

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(radius);
        if (strokeWidthPx > 0) shape.setStroke(strokeWidthPx, strokeColor);

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFF000000);
        mask.setCornerRadius(radius);

        return new RippleDrawable(ColorStateList.valueOf(rippleColor), shape, mask);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    private void refresh() {
        if (justEvaluated) {
            expressionView.setTextColor(COLOR_TEXT_SECONDARY);
            expressionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            expressionView.setText(lastExpression);

            resultView.setTextColor(COLOR_TEXT);
            resultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, resultSize(currentInput.length()));
            resultView.setText(currentInput);
        } else {
            String expr = committed + currentInput;

            expressionView.setTextColor(COLOR_TEXT);
            expressionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, expressionSize(expr.length()));
            expressionView.setText(expr.isEmpty() ? "0" : expr);

            resultView.setTextColor(showError ? COLOR_ERROR : COLOR_TEXT_SECONDARY);
            resultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
            resultView.setText(showError ? "Error" : previewText(expr));
        }
    }

    private float expressionSize(int len) {
        if (len <= 10) return 56;
        if (len <= 14) return 44;
        if (len <= 20) return 34;
        if (len <= 40) return 28;
        return 22;
    }

    private float resultSize(int len) {
        if (len <= 8) return 56;
        if (len <= 11) return 44;
        if (len <= 15) return 34;
        return 26;
    }

    private void handleButtonClick(String value) {
        showError = false;

        if (value.matches("[0-9]")) {
            inputDigit(value);

        } else if (value.equals(".")) {
            inputDecimal();

        } else if (value.matches("[+−×÷]")) {
            inputOperator(value);

        } else if (value.equals("%")) {
            inputPercent();

        } else if (value.equals("( )")) {
            inputParenthesis();

        } else if (value.equals("⌫")) {
            backspace();

        } else if (value.equals("AC")) {
            resetAll();

        } else if (value.equals("=")) {
            if (checkSecretCode(currentInput)) {
                resetAll();
                refresh();
                return;
            }
            evaluateExpression();
        }

        refresh();
    }

    private void resetAll() {
        committed = "";
        currentInput = "";
        lastExpression = "";
        justEvaluated = false;
        showError = false;
    }

    private void startFreshIfEvaluated() {
        if (justEvaluated) {
            committed = "";
            currentInput = "";
            justEvaluated = false;
        }
    }

    private boolean endsWithClosingToken() {
        return committed.endsWith(")") || committed.endsWith("%");
    }

    private void inputDigit(String d) {
        startFreshIfEvaluated();
        if (currentInput.isEmpty() && endsWithClosingToken()) committed += "×";
        if (currentInput.equals("0")) currentInput = "";
        if (currentInput.length() >= MAX_NUMBER_LENGTH) return;
        currentInput += d;
    }

    private void inputDecimal() {
        startFreshIfEvaluated();
        if (currentInput.isEmpty()) {
            if (endsWithClosingToken()) committed += "×";
            currentInput = "0.";
            return;
        }
        if (!currentInput.contains(".") && currentInput.length() < MAX_NUMBER_LENGTH) {
            currentInput += ".";
        }
    }

    private static boolean isOperator(char c) {
        return c == '+' || c == '−' || c == '×' || c == '÷';
    }

    private void inputOperator(String op) {
        if (justEvaluated) {
            committed = "";
            justEvaluated = false;
        }

        if (!currentInput.isEmpty()) {
            committed += currentInput + op;
            currentInput = "";
            return;
        }

        if (committed.isEmpty() || committed.endsWith("(")) {
            if (op.equals("−")) committed += op;
            return;
        }

        char last = committed.charAt(committed.length() - 1);
        if (isOperator(last)) {
            int n = committed.length();
            boolean unary = n == 1 || "(+−×÷".indexOf(committed.charAt(n - 2)) >= 0;
            if (unary) return;

            if (op.equals("−") && (last == '×' || last == '÷')) {
                committed += op;
                return;
            }
            committed = committed.substring(0, n - 1) + op;
            return;
        }
        
        committed += op;
    }

    private void inputPercent() {
        if (justEvaluated) {
            committed = "";
            justEvaluated = false;
        }
        if (!currentInput.isEmpty()) {
            committed += currentInput + "%";
            currentInput = "";
        } else if (endsWithClosingToken()) {
            committed += "%";
        }
    }

    private void inputParenthesis() {
        startFreshIfEvaluated();

        String full = committed + currentInput;
        int open = 0;
        for (int i = 0; i < full.length(); i++) {
            char c = full.charAt(i);
            if (c == '(') open++;
            else if (c == ')') open--;
        }

        char last = full.isEmpty() ? 0 : full.charAt(full.length() - 1);
        boolean numberLike = Character.isDigit(last) || last == '.' || last == ')' || last == '%';

        if (open > 0 && numberLike) {
            committed += currentInput + ")";
        } else {
            committed += currentInput + (numberLike ? "×" : "") + "(";
        }
        currentInput = "";
    }

    private void backspace() {
        if (justEvaluated) {
            justEvaluated = false;
            committed = "";
        }

        if (!currentInput.isEmpty()) {
            currentInput = currentInput.substring(0, currentInput.length() - 1);
            if (currentInput.equals("−")) currentInput = "";
            return;
        }

        if (!committed.isEmpty()) {
            committed = committed.substring(0, committed.length() - 1);

            int i = committed.length();
            while (i > 0 && (Character.isDigit(committed.charAt(i - 1)) || committed.charAt(i - 1) == '.')) {
                i--;
            }
            currentInput = committed.substring(i);
            committed = committed.substring(0, i);
        }
    }

    private void evaluateExpression() {
        if (justEvaluated) return;

        String full = committed + currentInput;
        if (full.isEmpty()) return;

        String prepared = balance(full);
        Double result = evaluate(prepared);
        if (result == null) {
            showError = true;
            return;
        }

        lastExpression = prepared + " =";
        currentInput = format(result);
        committed = "";
        justEvaluated = true;
    }

    private String balance(String e) {
        while (!e.isEmpty()) {
            char last = e.charAt(e.length() - 1);
            if (isOperator(last) || last == '(') {
                e = e.substring(0, e.length() - 1);
            } else {
                break;
            }
        }
        int open = 0;
        for (int i = 0; i < e.length(); i++) {
            char c = e.charAt(i);
            if (c == '(') open++;
            else if (c == ')') open--;
        }
        StringBuilder sb = new StringBuilder(e);
        for (int i = 0; i < open; i++) sb.append(')');
        return sb.toString();
    }

    private Double evaluate(String expression) {
        try {
            double v = new ExpressionParser(expression).parse();
            if (Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String previewText(String expr) {
        if (expr.isEmpty()) return "";
        String prepared = balance(expr);
        if (prepared.isEmpty() || prepared.matches("−?[0-9.]+")) return "";
        Double v = evaluate(prepared);
        return v == null ? "" : format(v);
    }

    private String format(double v) {
        BigDecimal bd = new BigDecimal(v).round(new MathContext(12)).stripTrailingZeros();
        if (bd.signum() == 0) return "0";
        return bd.toPlainString().replace("-", "−");
    }

    private static class ExpressionParser {
        private final String s;
        private int pos = 0;
        private boolean factorPercent = false;
        private boolean termPercent = false;

        ExpressionParser(String s) {
            this.s = s;
        }

        double parse() {
            double v = parseExpression();
            if (pos != s.length()) throw new IllegalArgumentException("Unexpected token");
            return v;
        }

        private double parseExpression() {
            double left = parseTerm();
            while (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '−')) {
                char op = s.charAt(pos++);
                double right = parseTerm();
                if (termPercent) right = left * right;
                left = (op == '+') ? left + right : left - right;
            }
            return left;
        }

        private double parseTerm() {
            double v = parseFactor();
            boolean pct = factorPercent;
            while (pos < s.length() && (s.charAt(pos) == '×' || s.charAt(pos) == '÷')) {
                char op = s.charAt(pos++);
                double r = parseFactor();
                v = (op == '×') ? v * r : v / r;
                pct = false;
            }
            termPercent = pct;
            return v;
        }

        private double parseFactor() {
            if (pos >= s.length()) throw new IllegalArgumentException("Unexpected end");

            char c = s.charAt(pos);
            if (c == '−') {
                pos++;
                return -parseFactor();
            }
            if (c == '+') {
                pos++;
                return parseFactor();
            }

            double v;
            if (c == '(') {
                pos++;
                v = parseExpression();
                if (pos >= s.length() || s.charAt(pos) != ')') {
                    throw new IllegalArgumentException("Missing )");
                }
                pos++;
            } else {
                int start = pos;
                while (pos < s.length()
                        && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                    pos++;
                }
                if (start == pos) throw new IllegalArgumentException("Number expected");
                v = Double.parseDouble(s.substring(start, pos));
            }

            boolean pct = false;
            while (pos < s.length() && s.charAt(pos) == '%') {
                pos++;
                v /= 100.0;
                pct = true;
            }
            factorPercent = pct;
            return v;
        }
    }

    private boolean checkSecretCode(String data) {
        if (data == null) return false; 
           
        if (data.length() < 5) return false;
        data = data.substring(data.length() - 5);
        if (data.length() > 5) return false;
                                    
        Context deContext = getApplicationContext().createDeviceProtectedStorageContext();
        SharedPreferences dePrefs = deContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        String savedHash = CryptoManager.getString(dePrefs, CryptoManager.DE_ALIAS, SECRET_CODE_HASH, null);
        String savedSalt = CryptoManager.getString(dePrefs, CryptoManager.DE_ALIAS, SECRET_CODE_SALT, null);

        if (savedHash == null || savedSalt == null) return false;
            
        String inputHash = hashPin(data, savedSalt);
        if (!savedHash.equals(inputHash)) return false;
        
        Intent i = new Intent(this, EntryActivity.class);
        startActivity(i);
                
        return true;
    }

    @Override
    protected void onPause() {
    super.onPause();
    finish();
    }

    private String hashPin(String pin, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.decode(salt, Base64.NO_WRAP));
            byte[] hash = md.digest(pin.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
