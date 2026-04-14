package com.smartlife.expense.ml;

import com.smartlife.expense.model.ExpenseCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tribuo.*;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.classification.evaluation.LabelEvaluation;
import org.tribuo.classification.evaluation.LabelEvaluator;
import org.tribuo.classification.sgd.linear.LinearSGDTrainer;
import org.tribuo.classification.sgd.objectives.LogMulticlass;
import org.tribuo.impl.ArrayExample;
import org.tribuo.math.optimisers.AdaGrad;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Tribuo-based classifier for expense categorization.
 *
 * Uses LinearSGD with a manual bag-of-bigrams feature extractor — no
 * tribuo-data-text dependency required; relies only on tribuo-classification-sgd
 * which is already on the classpath.
 *
 * Lifecycle:
 *  1. At startup: tries to load serialized model from disk.
 *  2. If not found: returns empty (caller falls back to keyword heuristics).
 *  3. When ≥50 labeled expenses exist: retrain() produces and saves a real model.
 */
@Component
@Slf4j
public class TribuoExpenseClassifier {

    @Value("${smartlife.ml.models-dir}")
    private String modelsDir;

    private static final String MODEL_FILE = "expense-classifier.ser";
    private static final int MIN_TRAINING_EXAMPLES = 50;

    private Model<Label> model;
    private final LabelFactory labelFactory = new LabelFactory();

    @PostConstruct
    public void init() {
        tryLoadModel();
        if (model == null) {
            log.info("Expense classifier model not found — using keyword fallback until retrain() is called");
        } else {
            log.info("Expense classifier model loaded from disk");
        }
    }

    /**
     * Predict category for given text.
     * Returns empty if model is not trained yet (caller uses keyword fallback).
     */
    public Optional<ExpenseCategory> predict(String description, String merchant) {
        if (model == null) return Optional.empty();

        String text = buildText(description, merchant);
        ArrayExample<Label> example = toFeatures(text, new Label("?"));

        Prediction<Label> prediction = model.predict(example);
        String label = prediction.getOutput().getLabel();
        try {
            return Optional.of(ExpenseCategory.valueOf(label));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Train a new model from a list of labeled examples (description + category).
     * Call this when you have ≥50 labeled expenses available.
     */
    public synchronized void retrain(List<TrainingExample> examples) {
        if (examples.size() < MIN_TRAINING_EXAMPLES) {
            log.warn("Not enough training examples: {} (need {})", examples.size(), MIN_TRAINING_EXAMPLES);
            return;
        }

        log.info("Training Tribuo expense classifier with {} examples", examples.size());
        MutableDataset<Label> dataset = new MutableDataset<>(
                new SimpleDataSourceProvenance("expenses", labelFactory), labelFactory);

        for (TrainingExample ex : examples) {
            String text = buildText(ex.description(), ex.merchant());
            Label label = new Label(ex.category().name());
            dataset.add(toFeatures(text, label));
        }

        LinearSGDTrainer trainer = new LinearSGDTrainer(
                new LogMulticlass(),
                new AdaGrad(0.1, 0.1),
                15,    // epochs
                1000,  // logging interval
                42L    // seed
        );

        model = trainer.train(dataset);

        LabelEvaluation eval = new LabelEvaluator().evaluate(model, dataset);
        log.info("Expense classifier trained — training accuracy: {}", String.format("%.2f", eval.accuracy()));

        saveModel();
    }

    /**
     * Evaluate model against a held-out test set.
     */
    public Optional<Double> evaluate(List<TrainingExample> testExamples) {
        if (model == null || testExamples.isEmpty()) return Optional.empty();

        MutableDataset<Label> testSet = new MutableDataset<>(
                new SimpleDataSourceProvenance("test", labelFactory), labelFactory);

        for (TrainingExample ex : testExamples) {
            String text = buildText(ex.description(), ex.merchant());
            testSet.add(toFeatures(text, new Label(ex.category().name())));
        }

        LabelEvaluation eval = new LabelEvaluator().evaluate(model, testSet);
        return Optional.of(eval.accuracy());
    }

    // ── Feature extraction ────────────────────────────────────────────────────

    /**
     * Manual bag-of-bigrams feature extraction.
     * Produces unigram and bigram features from whitespace-tokenized text.
     */
    private ArrayExample<Label> toFeatures(String text, Label label) {
        String[] tokens = text.split("\\s+");
        Map<String, Double> featureMap = new LinkedHashMap<>();

        // Unigrams
        for (String token : tokens) {
            if (!token.isBlank()) {
                featureMap.merge("w:" + token, 1.0, Double::sum);
            }
        }
        // Bigrams
        for (int i = 0; i < tokens.length - 1; i++) {
            if (!tokens[i].isBlank() && !tokens[i + 1].isBlank()) {
                String bigram = "b:" + tokens[i] + "_" + tokens[i + 1];
                featureMap.merge(bigram, 1.0, Double::sum);
            }
        }

        String[] names = featureMap.keySet().toArray(new String[0]);
        double[] values = featureMap.values().stream().mapToDouble(Double::doubleValue).toArray();

        return new ArrayExample<>(label, names, values);
    }

    // ── Model persistence ─────────────────────────────────────────────────────

    private void saveModel() {
        if (model == null) return;
        try {
            Path modelPath = Paths.get(modelsDir, MODEL_FILE);
            Files.createDirectories(modelPath.getParent());
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(new FileOutputStream(modelPath.toFile())))) {
                oos.writeObject(model);
            }
            log.info("Expense classifier model saved to {}", modelPath);
        } catch (IOException e) {
            log.error("Failed to save expense classifier model", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void tryLoadModel() {
        Path modelPath = Paths.get(modelsDir, MODEL_FILE);
        if (!Files.exists(modelPath)) return;
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(modelPath.toFile())))) {
            model = (Model<Label>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.warn("Could not load expense classifier model: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildText(String description, String merchant) {
        return ((description != null ? description : "") + " " +
                (merchant != null ? merchant : "")).toLowerCase().trim();
    }

    public boolean isModelReady() {
        return model != null;
    }

    public record TrainingExample(String description, String merchant, ExpenseCategory category) {}
}
