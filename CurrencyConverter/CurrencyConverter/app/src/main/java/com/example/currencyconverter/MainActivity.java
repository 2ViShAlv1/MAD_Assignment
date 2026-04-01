package com.example.currencyconverter;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // ── Static rate data ──────────────────────────────────────────
    private static final Map<String, Double> RATES_TO_USD = new HashMap<>();
    static {
        RATES_TO_USD.put("USD", 1.0);
        RATES_TO_USD.put("INR", 83.52);
        RATES_TO_USD.put("JPY", 149.85);
        RATES_TO_USD.put("EUR", 0.9215);
    }

    private static final String[] CURRENCIES = {"USD", "INR", "JPY", "EUR"};

    private static final Map<String, String> CURRENCY_SYMBOLS = new HashMap<>();
    static {
        CURRENCY_SYMBOLS.put("USD", "$");
        CURRENCY_SYMBOLS.put("INR", "\u20b9");
        CURRENCY_SYMBOLS.put("JPY", "\u00a5");
        CURRENCY_SYMBOLS.put("EUR", "\u20ac");
    }

    private static final Map<String, String> CURRENCY_NAMES = new HashMap<>();
    static {
        CURRENCY_NAMES.put("USD", "US Dollar");
        CURRENCY_NAMES.put("INR", "Indian Rupee");
        CURRENCY_NAMES.put("JPY", "Japanese Yen");
        CURRENCY_NAMES.put("EUR", "Euro");
    }

    // ── View references ───────────────────────────────────────────
    private Spinner      spinnerFrom;
    private Spinner      spinnerTo;
    private EditText     etAmount;
    private TextView     tvResult;
    private TextView     tvFromLabel;
    private TextView     tvToLabel;
    private TextView     tvRateInfo;
    private ImageButton  btnSwap;
    private LinearLayout llRatesTable;

    private boolean isUpdating   = false;
    private String  fromCurrency = "USD";
    private String  toCurrency   = "INR";

    // ── Lifecycle ─────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyThemeFromPrefs();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        initViews();
        setupSpinners();
        setupAmountWatcher();
        setupSwapButton();
        updateRateInfo();
        buildRatesTable();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyThemeFromPrefs();
    }

    // ── Theme ─────────────────────────────────────────────────────
    private void applyThemeFromPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = prefs.getString("theme_preference", "system");
        switch (theme) {
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    // ── View init ─────────────────────────────────────────────────
    private void initViews() {
        spinnerFrom  = findViewById(R.id.spinner_from);
        spinnerTo    = findViewById(R.id.spinner_to);
        etAmount     = findViewById(R.id.et_amount);
        tvResult     = findViewById(R.id.tv_result);
        tvFromLabel  = findViewById(R.id.tv_from_label);
        tvToLabel    = findViewById(R.id.tv_to_label);
        tvRateInfo   = findViewById(R.id.tv_rate_info);
        btnSwap      = findViewById(R.id.btn_swap);
        llRatesTable = findViewById(R.id.ll_rates_table);
    }

    // ── Spinners ──────────────────────────────────────────────────
    private void setupSpinners() {
        String[] names = new String[CURRENCIES.length];
        String[] symbols = new String[CURRENCIES.length];
        for (int i = 0; i < CURRENCIES.length; i++) {
            names[i] = CURRENCY_NAMES.get(CURRENCIES[i]);
            symbols[i] = CURRENCY_SYMBOLS.get(CURRENCIES[i]);
        }

        CurrencySpinnerAdapter adapter = new CurrencySpinnerAdapter(
                this, CURRENCIES, names, symbols);
        spinnerFrom.setAdapter(adapter);
        spinnerTo.setAdapter(adapter);
        spinnerFrom.setSelection(0); // USD
        spinnerTo.setSelection(1);   // INR

        spinnerFrom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                fromCurrency = CURRENCIES[pos];
                tvFromLabel.setText(fromCurrency);
                updateRateInfo();
                performConversion();
                buildRatesTable();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        spinnerTo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                toCurrency = CURRENCIES[pos];
                tvToLabel.setText(toCurrency);
                updateRateInfo();
                performConversion();
                buildRatesTable();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    // ── Amount watcher ────────────────────────────────────────────
    private void setupAmountWatcher() {
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (!isUpdating) performConversion();
            }
        });
        etAmount.setText("1");
    }

    // ── Swap button ───────────────────────────────────────────────
    private void setupSwapButton() {
        btnSwap.setOnClickListener(v -> {
            btnSwap.animate().rotation(btnSwap.getRotation() + 180).setDuration(280).start();
            String tmp   = fromCurrency;
            fromCurrency = toCurrency;
            toCurrency   = tmp;
            isUpdating   = true;
            spinnerFrom.setSelection(posOf(fromCurrency));
            spinnerTo.setSelection(posOf(toCurrency));
            isUpdating   = false;
            tvFromLabel.setText(fromCurrency);
            tvToLabel.setText(toCurrency);
            updateRateInfo();
            performConversion();
            buildRatesTable();
        });
    }

    // ── Quick chips ───────────────────────────────────────────────
    public void onChipClicked(View view) {
        int id = view.getId();
        if      (id == R.id.chip_1)     setAmount("1");
        else if (id == R.id.chip_10)    setAmount("10");
        else if (id == R.id.chip_100)   setAmount("100");
        else if (id == R.id.chip_1000)  setAmount("1000");
        else if (id == R.id.chip_10000) setAmount("10000");
    }

    private void setAmount(String amount) {
        isUpdating = true;
        etAmount.setText(amount);
        etAmount.setSelection(amount.length());
        isUpdating = false;
        performConversion();
    }

    // ── Conversion ────────────────────────────────────────────────
    private void performConversion() {
        String input = etAmount.getText().toString().trim();
        if (input.isEmpty() || input.equals(".")) { tvResult.setText("0"); return; }
        try {
            double amount = Double.parseDouble(input);
            double result = convert(amount, fromCurrency, toCurrency);
            tvResult.setText(fmt(result, toCurrency));
        } catch (NumberFormatException e) {
            tvResult.setText("\u2014");
        }
    }

    private double convert(double amount, String from, String to) {
        return (amount / RATES_TO_USD.get(from)) * RATES_TO_USD.get(to);
    }

    private String fmt(double value, String currency) {
        DecimalFormat df = currency.equals("JPY")
                ? new DecimalFormat("#,##0")
                : new DecimalFormat("#,##0.00");
        return df.format(value);
    }

    private void updateRateInfo() {
        if (fromCurrency == null || toCurrency == null) return;
        double rate   = convert(1.0, fromCurrency, toCurrency);
        String symbol = CURRENCY_SYMBOLS.get(toCurrency);
        DecimalFormat df = toCurrency.equals("JPY")
                ? new DecimalFormat("#,##0.00")
                : new DecimalFormat("#,##0.0000");
        tvRateInfo.setText("1 " + fromCurrency + " = " + symbol + df.format(rate) + " " + toCurrency);
    }

    // ── Rates reference table ─────────────────────────────────────
    private void buildRatesTable() {
        if (llRatesTable == null) return;
        llRatesTable.removeAllViews();

        int dp8  = dp(8);
        int dp12 = dp(12);
        int dp1  = dp(1);

        // Header row
        LinearLayout header = makeRow(true);
        header.addView(makeCell("Currency", true, Gravity.START, 0, 1.2f));
        header.addView(makeCell("Symbol",   true, Gravity.CENTER, 0, 0.6f));
        header.addView(makeCell("1 " + fromCurrency + " =", true, Gravity.END, 0, 1.2f));
        llRatesTable.addView(header);

        // Divider
        llRatesTable.addView(makeDivider());

        // Data rows — all 4 currencies as destination
        boolean alternate = false;
        for (String currency : CURRENCIES) {
            double rate   = convert(1.0, fromCurrency, currency);
            String symbol = CURRENCY_SYMBOLS.get(currency);
            String name   = CURRENCY_NAMES.get(currency);

            LinearLayout row = makeRow(false);
            if (alternate) {
                row.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.table_row_alt));
            }

            // Currency name + code column
            LinearLayout nameCol = new LinearLayout(this);
            nameCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams nameParams =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
            nameCol.setLayoutParams(nameParams);
            nameCol.setPadding(dp12, dp8, dp8, dp8);

            TextView tvCode = new TextView(this);
            tvCode.setText(currency);
            tvCode.setTextSize(14);
            tvCode.setTypeface(null, Typeface.BOLD);
            tvCode.setTextColor(getColorAttr(android.R.attr.textColorPrimary));
            if (currency.equals(fromCurrency)) {
                tvCode.setTextColor(getPrimaryColor());
            }
            nameCol.addView(tvCode);

            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextSize(11);
            tvName.setTextColor(getColorAttr(android.R.attr.textColorSecondary));
            nameCol.addView(tvName);

            row.addView(nameCol);

            // Symbol column
            row.addView(makeCell(symbol, false, Gravity.CENTER, 0, 0.6f));

            // Rate column
            String rateStr = currency.equals("JPY")
                    ? new DecimalFormat("#,##0.00").format(rate)
                    : new DecimalFormat("#,##0.0000").format(rate);
            TextView tvRate = (TextView) makeCell(rateStr, false, Gravity.END, 0, 1.2f);
            if (currency.equals(toCurrency)) {
                tvRate.setTextColor(getPrimaryColor());
                tvRate.setTypeface(null, Typeface.BOLD);
            }
            row.addView(tvRate);

            llRatesTable.addView(row);

            if (!currency.equals(CURRENCIES[CURRENCIES.length - 1])) {
                llRatesTable.addView(makeDivider());
            }
            alternate = !alternate;
        }
    }

    private LinearLayout makeRow(boolean isHeader) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        if (isHeader) {
            row.setBackgroundColor(0x11000000);
        }
        return row;
    }

    private View makeCell(String text, boolean bold, int gravity, int padStart, float weight) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(bold ? 11 : 14);
        tv.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        tv.setTextColor(bold
                ? getColorAttr(android.R.attr.textColorSecondary)
                : getColorAttr(android.R.attr.textColorPrimary));
        tv.setGravity(gravity);
        tv.setAllCaps(bold);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(lp);
        int dp12 = dp(12);
        int dp8  = dp(8);
        tv.setPadding(dp12, dp8, dp12, dp8);
        return tv;
    }

    private View makeDivider() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        v.setBackgroundColor(0x22888888);
        return v;
    }

    // ── Helpers ───────────────────────────────────────────────────
    private int posOf(String currency) {
        for (int i = 0; i < CURRENCIES.length; i++) {
            if (CURRENCIES[i].equals(currency)) return i;
        }
        return 0;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int getColorAttr(int attr) {
        int[] attrs = {attr};
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        int color = ta.getColor(0, Color.GRAY);
        ta.recycle();
        return color;
    }

    private int getPrimaryColor() {
        int[] attrs = {com.google.android.material.R.attr.colorPrimary};
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        int color = ta.getColor(0, Color.BLUE);
        ta.recycle();
        return color;
    }

    // ── Menu ──────────────────────────────────────────────────────
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
