package com.creditriskpredictor;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.onnxruntime.OrtException;

/**
 * PredictionActivity - Credit Risk Prediction Form
 *
 * Collects 11 applicant features, runs on-device inference using the
 * ONNX Random Forest model, and shows the result inline.
 */
public class PredictionActivity extends AppCompatActivity {

    // Input fields
    private TextInputEditText etAge, etIncome, etEmpLength;
    private TextInputEditText etLoanAmnt, etIntRate, etPercentIncome, etCredHist;
    private AutoCompleteTextView acvHomeOwnership, acvLoanIntent, acvLoanGrade, acvDefaultOnFile;

    // Result views
    private CardView     cardResult;
    private LinearLayout llResultBadge;
    private TextView     tvResultEmoji, tvResultLabel, tvResultSubtitle;
    private TextView     tvProbNoDefault, tvProbDefault;
    private ProgressBar  pbRisk;
    private ExtendedFloatingActionButton fabSave;

    private OnnxHelper onnxHelper;
    private OnnxHelper.PredictionResult lastResult;

    // Background executor for model operations
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Current form values (used for saving)
    private float   formAge, formIncome;
    private String  formIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prediction);

        // Back button
        TextView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Wire input fields
        etAge            = findViewById(R.id.et_age);
        etIncome         = findViewById(R.id.et_income);
        etEmpLength      = findViewById(R.id.et_emp_length);
        etLoanAmnt       = findViewById(R.id.et_loan_amnt);
        etIntRate        = findViewById(R.id.et_int_rate);
        etPercentIncome  = findViewById(R.id.et_percent_income);
        etCredHist       = findViewById(R.id.et_cred_hist);

        acvHomeOwnership = findViewById(R.id.acv_home_ownership);
        acvLoanIntent    = findViewById(R.id.acv_loan_intent);
        acvLoanGrade     = findViewById(R.id.acv_loan_grade);
        acvDefaultOnFile = findViewById(R.id.acv_default_on_file);

        // Result views
        cardResult        = findViewById(R.id.card_result);
        llResultBadge     = findViewById(R.id.ll_result_badge);
        tvResultEmoji     = findViewById(R.id.tv_result_emoji);
        tvResultLabel     = findViewById(R.id.tv_result_label);
        tvResultSubtitle  = findViewById(R.id.tv_result_subtitle);
        tvProbNoDefault   = findViewById(R.id.tv_prob_no_default);
        tvProbDefault     = findViewById(R.id.tv_prob_default);
        pbRisk            = findViewById(R.id.pb_risk);
        fabSave           = findViewById(R.id.fab_save);

        // Populate dropdowns
        setupDropdowns();

        // Button listeners
        MaterialButton btnAssess = findViewById(R.id.btn_assess);
        MaterialButton btnClear  = findViewById(R.id.btn_clear);

        btnAssess.setOnClickListener(v -> runPrediction());
        btnClear.setOnClickListener(v  -> clearForm());

        fabSave.setOnClickListener(v -> saveResult());

        // Load ONNX model in background
        loadModel();
    }

    // ── Model loading ────────────────────────────────────────────────────────

    private void loadModel() {
        executor.execute(() -> {
            try {
                onnxHelper = new OnnxHelper(this);
            } catch (OrtException | IOException | JSONException e) {
                runOnUiThread(() -> {
                    Snackbar.make(
                            findViewById(android.R.id.content),
                            "Error loading model: " + e.getMessage(),
                            Snackbar.LENGTH_LONG
                    ).show();
                });
                e.printStackTrace();
            }
        });
    }

    // ── Dropdowns ────────────────────────────────────────────────────────────

    private void setupDropdowns() {
        setAdapter(acvHomeOwnership, new String[]{"MORTGAGE", "OTHER", "OWN", "RENT"});
        setAdapter(acvLoanIntent, new String[]{
                "DEBTCONSOLIDATION", "EDUCATION", "HOMEIMPROVEMENT",
                "MEDICAL", "PERSONAL", "VENTURE"});
        setAdapter(acvLoanGrade, new String[]{"A", "B", "C", "D", "E", "F", "G"});
        setAdapter(acvDefaultOnFile, new String[]{"N", "Y"});
    }

    private void setAdapter(AutoCompleteTextView acv, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, items);
        acv.setAdapter(adapter);
    }

    // ── Prediction ───────────────────────────────────────────────────────────

    private void runPrediction() {
        if (onnxHelper == null) {
            Snackbar.make(findViewById(android.R.id.content),
                    "Model not loaded yet. Please wait.", Snackbar.LENGTH_SHORT).show();
            return;
        }

        // Validate & parse inputs (Main Thread)
        try {
            formAge    = parseFloat(etAge,     "Age");
            formIncome = parseFloat(etIncome,  "Income");
            final float empLen       = parseFloat(etEmpLength,     "Employment Length");
            final float loanAmnt     = parseFloat(etLoanAmnt,      "Loan Amount");
            final float intRate      = parseFloat(etIntRate,       "Interest Rate");
            final float pctIncome    = parseFloat(etPercentIncome, "% of Income");
            final float credHist     = parseFloat(etCredHist,      "Credit History Length");

            final String homeOwnership = requireDropdown(acvHomeOwnership, "Home Ownership");
            formIntent           = requireDropdown(acvLoanIntent,    "Loan Intent");
            final String loanGrade     = requireDropdown(acvLoanGrade,     "Loan Grade");
            final String defaultOnFile = requireDropdown(acvDefaultOnFile, "Default on File");

            // Show loading state if needed
            findViewById(R.id.btn_assess).setEnabled(false);

            // Run ONNX inference in background
            executor.execute(() -> {
                try {
                    lastResult = onnxHelper.predict(
                            formAge, formIncome, homeOwnership, empLen,
                            formIntent, loanGrade, loanAmnt, intRate,
                            pctIncome, defaultOnFile, credHist
                    );
                    runOnUiThread(() -> {
                        findViewById(R.id.btn_assess).setEnabled(true);
                        showResult(lastResult);
                    });
                } catch (OrtException e) {
                    runOnUiThread(() -> {
                        findViewById(R.id.btn_assess).setEnabled(true);
                        Snackbar.make(findViewById(android.R.id.content),
                                "Inference error: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                    });
                    e.printStackTrace();
                }
            });

        } catch (IllegalArgumentException e) {
            Snackbar.make(findViewById(android.R.id.content),
                    e.getMessage(), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void showResult(OnnxHelper.PredictionResult result) {
        cardResult.setVisibility(View.VISIBLE);

        if (result.isHighRisk()) {
            llResultBadge.setBackgroundResource(R.drawable.bg_high_risk);
            tvResultEmoji.setText("⚠️");
            tvResultLabel.setText("High Risk");
            tvResultLabel.setTextColor(getColor(R.color.high_risk));
            tvResultSubtitle.setText("Applicant is likely to default on this loan");
        } else {
            llResultBadge.setBackgroundResource(R.drawable.bg_low_risk);
            tvResultEmoji.setText("✅");
            tvResultLabel.setText("Low Risk");
            tvResultLabel.setTextColor(getColor(R.color.low_risk));
            tvResultSubtitle.setText("Applicant is unlikely to default on this loan");
        }

        tvProbNoDefault.setText(result.noDefaultProbPct() + "%");
        tvProbDefault.setText(result.defaultProbPct() + "%");
        pbRisk.setProgress(result.defaultProbPct());

        fabSave.setVisibility(View.VISIBLE);

        // Scroll to result card
        NestedScrollView sv = (NestedScrollView) cardResult.getParent().getParent().getParent();
        sv.post(() -> sv.smoothScrollTo(0, cardResult.getBottom()));
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    private void saveResult() {
        if (lastResult == null) return;
        MainActivity.saveResult(
                this,
                lastResult.isHighRisk(),
                lastResult.defaultProbPct(),
                formAge, formIncome, formIntent
        );
        fabSave.setVisibility(View.GONE);
        Snackbar.make(findViewById(android.R.id.content),
                "Result saved to dashboard", Snackbar.LENGTH_SHORT).show();
    }

    // ── Clear ────────────────────────────────────────────────────────────────

    private void clearForm() {
        etAge.setText("");
        etIncome.setText("");
        etEmpLength.setText("");
        etLoanAmnt.setText("");
        etIntRate.setText("");
        etPercentIncome.setText("");
        etCredHist.setText("");
        acvHomeOwnership.setText("", false);
        acvLoanIntent.setText("", false);
        acvLoanGrade.setText("", false);
        acvDefaultOnFile.setText("", false);
        cardResult.setVisibility(View.GONE);
        fabSave.setVisibility(View.GONE);
        lastResult = null;
    }

    // ── Validation helpers ───────────────────────────────────────────────────

    private float parseFloat(TextInputEditText et, String fieldName) {
        String txt = et.getText() == null ? "" : et.getText().toString().trim();
        if (TextUtils.isEmpty(txt)) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            return Float.parseFloat(txt);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a number.");
        }
    }

    private String requireDropdown(AutoCompleteTextView acv, String fieldName) {
        String val = acv.getText() == null ? "" : acv.getText().toString().trim();
        if (TextUtils.isEmpty(val)) {
            throw new IllegalArgumentException("Please select " + fieldName + ".");
        }
        return val;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown(); // Stop background tasks
        if (onnxHelper != null) onnxHelper.close();
    }
}
