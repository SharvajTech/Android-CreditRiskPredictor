package com.creditriskpredictor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * MainActivity - Credit Risk Data Insights Dashboard
 *
 * Displays aggregate statistics (total, low-risk, high-risk) and a
 * scrollable list of recent predictions stored in SharedPreferences.
 * Tapping the FAB navigates to PredictionActivity.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME  = "CreditRiskPrefs";
    private static final String KEY_HISTORY = "prediction_history";
    private static final int    MAX_HISTORY = 20;

    private TextView     tvTotal, tvLowRisk, tvHighRisk;
    private LinearLayout llHistory, llEmptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTotal       = findViewById(R.id.tv_total_count);
        tvLowRisk     = findViewById(R.id.tv_low_risk_count);
        tvHighRisk    = findViewById(R.id.tv_high_risk_count);
        llHistory     = findViewById(R.id.ll_history);
        llEmptyState  = findViewById(R.id.ll_empty_state);

        // Clear history button
        TextView tvClear = findViewById(R.id.tv_clear_history);
        tvClear.setOnClickListener(v -> {
            clearHistory();
            refreshDashboard();
            Snackbar.make(v, "History cleared", Snackbar.LENGTH_SHORT).show();
        });

        // FAB: go to prediction form
        ExtendedFloatingActionButton fab = new ExtendedFloatingActionButton(this);
        // We add the FAB programmatically over the dashboard
        // Actually the layout uses a regular button — let's wire it from the layout
        // The "New Prediction" action is triggered from the empty state or via an action bar button.
        // We'll add a persistent bottom FAB by overriding the layout's existing footer.
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
    }

    /** Reload stats and history list from SharedPreferences. */
    private void refreshDashboard() {
        JSONArray history = loadHistory();

        int total   = history.length();
        int lowRisk = 0;
        int highRisk = 0;

        llHistory.removeAllViews();

        for (int i = total - 1; i >= 0; i--) {  // most recent first
            try {
                JSONObject item = history.getJSONObject(i);
                boolean isHigh  = item.getBoolean("isHighRisk");
                int     probPct = item.getInt("probPct");
                String  details = item.getString("details");

                if (isHigh) highRisk++; else lowRisk++;

                // Inflate history row
                View row = LayoutInflater.from(this)
                        .inflate(R.layout.item_prediction_history, llHistory, false);

                View     dot    = row.findViewById(R.id.v_risk_indicator);
                TextView tvRes  = row.findViewById(R.id.tv_history_result);
                TextView tvDet  = row.findViewById(R.id.tv_history_details);
                TextView tvProb = row.findViewById(R.id.tv_history_prob);

                if (isHigh) {
                    dot.setBackgroundColor(getColor(R.color.high_risk));
                    tvRes.setText("High Risk");
                    tvRes.setTextColor(getColor(R.color.high_risk));
                } else {
                    dot.setBackgroundColor(getColor(R.color.low_risk));
                    tvRes.setText("Low Risk");
                    tvRes.setTextColor(getColor(R.color.low_risk));
                }
                tvDet.setText(details);
                tvProb.setText(probPct + "%");

                // Tap to re-open the result (future enhancement)
                llHistory.addView(row);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Update counters
        tvTotal.setText(String.valueOf(total));
        tvLowRisk.setText(String.valueOf(lowRisk));
        tvHighRisk.setText(String.valueOf(highRisk));

        // Show/hide empty state
        llEmptyState.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        llHistory.setVisibility(total == 0 ? View.GONE : View.VISIBLE);
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    public static void saveResult(
            android.content.Context ctx,
            boolean isHighRisk, int probDefaultPct,
            float age, float income, String intent
    ) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME,
                android.content.Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_HISTORY, "[]");
        JSONArray arr;
        try {
            arr = new JSONArray(raw);
        } catch (JSONException e) {
            arr = new JSONArray();
        }

        try {
            JSONObject item = new JSONObject();
            item.put("isHighRisk", isHighRisk);
            item.put("probPct",    probDefaultPct);
            item.put("details",    String.format("Age %d · $%,.0f · %s",
                    (int) age, income, intent));
            arr.put(item);

            // Trim to last MAX_HISTORY entries
            while (arr.length() > MAX_HISTORY) arr.remove(0);

            prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private JSONArray loadHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String raw = prefs.getString(KEY_HISTORY, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void clearHistory() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().remove(KEY_HISTORY).apply();
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /** Called by the floating "New Prediction" button from the empty state card. */
    public void openPredictionForm(View view) {
        startActivity(new Intent(this, PredictionActivity.class));
    }
}