package com.smartlife.expense.ml;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * DL4J LSTM model for time-series monthly spending prediction.
 *
 * Architecture:
 *   Input  → LSTM(64) → LSTM(32) → Dense(1) → predicted spend
 *
 * Input:  Last N months of [totalSpend, groceries, dining, transport, ...] (15 features)
 * Output: Predicted total spend for the next month
 *
 * Training triggers automatically when ≥6 months of data is available.
 */
@Component
@Slf4j
public class SpendingPredictionModel {

    @Value("${smartlife.ml.models-dir}")
    private String modelsDir;

    private static final String MODEL_FILE = "spending-lstm.zip";
    private static final int SEQUENCE_LENGTH = 6;   // 6 months of history
    private static final int FEATURES = 15;          // number of input features
    private static final int LSTM_HIDDEN_1 = 64;
    private static final int LSTM_HIDDEN_2 = 32;
    private static final int OUTPUT_SIZE = 1;
    private static final int MIN_MONTHS_TO_TRAIN = 6;

    private MultiLayerNetwork model;
    private double[] featureMeans;
    private double[] featureStds;

    @PostConstruct
    public void init() {
        tryLoadModel();
        if (model == null) {
            log.info("Spending prediction model not found — will train when enough data is available");
        } else {
            log.info("Spending prediction LSTM model loaded");
        }
    }

    /**
     * Train (or retrain) the LSTM on the user's historical monthly spending.
     *
     * @param monthlySeries list of monthly feature vectors (at least 6 entries)
     */
    public synchronized void train(List<double[]> monthlySeries) {
        if (monthlySeries.size() < MIN_MONTHS_TO_TRAIN + 1) {
            log.warn("Not enough months to train ({} available, need {}+1)",
                    monthlySeries.size(), MIN_MONTHS_TO_TRAIN);
            return;
        }

        log.info("Training spending LSTM on {} months of data", monthlySeries.size());

        normalizeInPlace(monthlySeries);
        buildModelIfNeeded();

        // Build training sequences: windows of SEQUENCE_LENGTH → next month total
        int numExamples = monthlySeries.size() - SEQUENCE_LENGTH;
        INDArray input  = Nd4j.zeros(numExamples, FEATURES, SEQUENCE_LENGTH);
        INDArray labels = Nd4j.zeros(numExamples, OUTPUT_SIZE, SEQUENCE_LENGTH);

        for (int i = 0; i < numExamples; i++) {
            for (int t = 0; t < SEQUENCE_LENGTH; t++) {
                double[] feats = monthlySeries.get(i + t);
                for (int f = 0; f < FEATURES; f++) {
                    input.putScalar(new int[]{i, f, t}, feats[f]);
                }
                // Label = total spend of the NEXT month (feature 0), aligned to last timestep
                if (t == SEQUENCE_LENGTH - 1) {
                    labels.putScalar(new int[]{i, 0, t}, monthlySeries.get(i + t + 1)[0]);
                }
            }
        }

        DataSet ds = new DataSet(input, labels);
        for (int epoch = 0; epoch < 50; epoch++) {
            model.fit(ds);
        }

        double loss = model.score();
        log.info("LSTM training complete. Final loss: {}", loss);
        saveModel();
    }

    /**
     * Predict next month's total spending given the last SEQUENCE_LENGTH months.
     *
     * @param recentMonths list of feature vectors (last 6 months, most recent last)
     * @return predicted spend (denormalized), or empty if model not ready
     */
    public java.util.OptionalDouble predict(List<double[]> recentMonths) {
        if (model == null || recentMonths.size() < SEQUENCE_LENGTH || featureMeans == null) {
            return java.util.OptionalDouble.empty();
        }

        INDArray input = Nd4j.zeros(1, FEATURES, SEQUENCE_LENGTH);
        for (int t = 0; t < SEQUENCE_LENGTH; t++) {
            double[] feats = recentMonths.get(recentMonths.size() - SEQUENCE_LENGTH + t);
            for (int f = 0; f < FEATURES; f++) {
                double normalized = featureStds[f] > 0
                        ? (feats[f] - featureMeans[f]) / featureStds[f]
                        : 0.0;
                input.putScalar(new int[]{0, f, t}, normalized);
            }
        }

        model.rnnClearPreviousState();
        INDArray output = model.output(input);
        // Last timestep output, feature 0
        double normalizedPred = output.getDouble(0, 0, SEQUENCE_LENGTH - 1);
        double denormalized = normalizedPred * featureStds[0] + featureMeans[0];
        return java.util.OptionalDouble.of(Math.max(0, denormalized));
    }

    public boolean isModelReady() {
        return model != null;
    }

    // ── Model architecture ────────────────────────────────────────────────────

    private void buildModelIfNeeded() {
        if (model != null) return;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(42)
                .weightInit(WeightInit.XAVIER)
                .updater(new Adam(0.001))
                .list()
                .layer(new LSTM.Builder()
                        .nIn(FEATURES).nOut(LSTM_HIDDEN_1)
                        .activation(Activation.TANH)
                        .build())
                .layer(new LSTM.Builder()
                        .nIn(LSTM_HIDDEN_1).nOut(LSTM_HIDDEN_2)
                        .activation(Activation.TANH)
                        .build())
                .layer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nIn(LSTM_HIDDEN_2).nOut(OUTPUT_SIZE)
                        .activation(Activation.IDENTITY)
                        .build())
                .build();

        model = new MultiLayerNetwork(conf);
        model.init();
    }

    // ── Normalization ─────────────────────────────────────────────────────────

    private void normalizeInPlace(List<double[]> data) {
        featureMeans = new double[FEATURES];
        featureStds  = new double[FEATURES];

        for (int f = 0; f < FEATURES; f++) {
            double sum = 0, sumSq = 0;
            for (double[] row : data) sum += row[f];
            featureMeans[f] = sum / data.size();
            for (double[] row : data) sumSq += Math.pow(row[f] - featureMeans[f], 2);
            featureStds[f] = Math.sqrt(sumSq / data.size());
            if (featureStds[f] == 0) featureStds[f] = 1.0;
        }

        for (double[] row : data) {
            for (int f = 0; f < FEATURES; f++) {
                row[f] = (row[f] - featureMeans[f]) / featureStds[f];
            }
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void saveModel() {
        try {
            Files.createDirectories(Paths.get(modelsDir));
            File f = Paths.get(modelsDir, MODEL_FILE).toFile();
            ModelSerializer.writeModel(model, f, true);
            log.info("Spending LSTM saved to {}", f.getPath());
        } catch (IOException e) {
            log.error("Failed to save spending LSTM model", e);
        }
    }

    private void tryLoadModel() {
        File f = Paths.get(modelsDir, MODEL_FILE).toFile();
        if (!f.exists()) return;
        try {
            model = ModelSerializer.restoreMultiLayerNetwork(f);
        } catch (IOException e) {
            log.warn("Could not load spending LSTM: {}", e.getMessage());
        }
    }
}
