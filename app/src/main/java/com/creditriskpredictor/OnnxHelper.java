package com.creditriskpredictor;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * OnnxHelper
 * ----------
 * Loads credit_risk_model.onnx and label_encoder_mappings.json from assets,
 * then exposes a single predict() method.
 */
public class OnnxHelper {

    private static final String TAG = "OnnxHelper";
    private static final String MODEL_FILE   = "credit_risk_model.onnx";
    private static final String ENCODER_FILE = "label_encoder_mappings.json";

    private final OrtEnvironment env;
    private final OrtSession     session;

    // encoder_map[column][label] = integer code
    private final Map<String, Map<String, Integer>> encoderMap = new HashMap<>();

    public OnnxHelper(Context context) throws OrtException, IOException, JSONException {
        env = OrtEnvironment.getEnvironment();

        // Load ONNX model bytes from assets
        byte[] modelBytes = readAssetBytes(context, MODEL_FILE);
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        session = env.createSession(modelBytes, opts);

        Log.d(TAG, "ONNX session created. Inputs: "  + session.getInputNames());
        Log.d(TAG, "ONNX session created. Outputs: " + session.getOutputNames());

        // Load label encoder mappings JSON from assets
        String json = readAssetString(context, ENCODER_FILE);
        JSONObject root = new JSONObject(json);
        Iterator<String> cols = root.keys();
        while (cols.hasNext()) {
            String col = cols.next();
            JSONObject mapping = root.getJSONObject(col);
            Map<String, Integer> colMap = new HashMap<>();
            Iterator<String> labels = mapping.keys();
            while (labels.hasNext()) {
                String label = labels.next();
                colMap.put(label, mapping.getInt(label));
            }
            encoderMap.put(col, colMap);
        }
        Log.d(TAG, "Encoders loaded for columns: " + encoderMap.keySet());
    }

    /**
     * Runs the ONNX model and returns a PredictionResult.
     */
    public PredictionResult predict(
            float age, float income, String homeOwnership, float empLength,
            String loanIntent, String loanGrade, float loanAmnt, float intRate,
            float percentIncome, String defaultOnFile, float credHistLength
    ) throws OrtException {

        // Encode categorical features
        float encHomeOwnership = encode("person_home_ownership", homeOwnership);
        float encLoanIntent    = encode("loan_intent",           loanIntent);
        float encLoanGrade     = encode("loan_grade",            loanGrade);
        float encDefaultOnFile = encode("cb_person_default_on_file", defaultOnFile);

        // Build float32 feature row [1 x 11]
        float[] features = {
            age, income, encHomeOwnership, empLength,
            encLoanIntent, encLoanGrade, loanAmnt, intRate,
            percentIncome, encDefaultOnFile, credHistLength
        };

        // Create a 2D array [1][11]. ONNX Runtime Java API infers shape and type from this.
        float[][] inputData = { features };
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputData);

        String inputName  = session.getInputNames().iterator().next();
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(inputName, inputTensor);

        try (OrtSession.Result result = session.run(inputs)) {
            // output[0] = label (int64 [1])
            long[] labels = (long[]) result.get(0).getValue();
            int label = (int) labels[0];

            // output[1] = probabilities (float32 [1][2])
            float[][] probs = (float[][]) result.get(1).getValue();
            float probNoDefault = probs[0][0];
            float probDefault   = probs[0][1];

            Log.d(TAG, "Prediction: label=" + label +
                    "  P(no_default)=" + probNoDefault +
                    "  P(default)=" + probDefault);

            return new PredictionResult(label, probNoDefault, probDefault);
        } finally {
            inputTensor.close();
        }
    }

    private float encode(String column, String value) {
        Map<String, Integer> colMap = encoderMap.get(column);
        if (colMap == null) return 0f;
        Integer code = colMap.get(value);
        return code != null ? code.floatValue() : 0f;
    }

    public void close() {
        try {
            if (session != null) session.close();
            if (env     != null) env.close();
        } catch (OrtException e) {
            Log.e(TAG, "Error closing ORT session", e);
        }
    }

    private byte[] readAssetBytes(Context ctx, String fileName) throws IOException {
        AssetManager am = ctx.getAssets();
        try (InputStream is = am.open(fileName)) {
            byte[] buf = new byte[is.available()];
            is.read(buf);
            return buf;
        }
    }

    private String readAssetString(Context ctx, String fileName) throws IOException {
        AssetManager am = ctx.getAssets();
        try (InputStream is = am.open(fileName);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    public static class PredictionResult {
        public final int   label;
        public final float probNoDefault;
        public final float probDefault;

        public PredictionResult(int label, float probNoDefault, float probDefault) {
            this.label         = label;
            this.probNoDefault = probNoDefault;
            this.probDefault   = probDefault;
        }

        public boolean isHighRisk()        { return label == 1; }
        public int     defaultProbPct()    { return Math.round(probDefault   * 100); }
        public int     noDefaultProbPct()  { return Math.round(probNoDefault * 100); }
    }
}
