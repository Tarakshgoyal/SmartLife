package com.smartlife.document.service;

import com.smartlife.document.dto.MedicalReportDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses medical/lab report text to extract key diagnostic values.
 *
 * Supports: blood tests (CBC, lipid, diabetes markers), BP readings, BMI, thyroid.
 *
 * Phase 2: Replace regex with DL4J NER model trained on medical documents.
 */
@Service
@Slf4j
public class MedicalDocumentParser {

    // ── Patterns ─────────────────────────────────────────────────────────────

    private static final Map<String, Pattern> VALUE_PATTERNS = new HashMap<>();
    private static final Map<String, double[]> NORMAL_RANGES = new HashMap<>();

    static {
        // Haematology
        register("hemoglobin",   "(?:hemoglobin|haemoglobin|hb|hgb)[:\\s]*(\\d+\\.?\\d*)",   12.0, 17.5);
        register("rbc",          "(?:rbc|red\\s*blood\\s*cells?)[:\\s]*(\\d+\\.?\\d*)",        4.2,  5.9);
        register("wbc",          "(?:wbc|white\\s*blood\\s*cells?|tlc)[:\\s]*(\\d+\\.?\\d*)", 4.0, 11.0);
        register("platelets",    "(?:platelets?|plt)[:\\s]*(\\d+)",                            150000, 400000);
        register("hematocrit",   "(?:hematocrit|haematocrit|pcv)[:\\s]*(\\d+\\.?\\d*)",       36.0, 52.0);

        // Glucose / Diabetes
        register("fasting_glucose", "(?:fasting\\s*(?:blood\\s*)?glucose|fbs)[:\\s]*(\\d+\\.?\\d*)",  70.0, 100.0);
        register("pp_glucose",      "(?:post\\s*prandial|pp\\s*glucose|ppbs)[:\\s]*(\\d+\\.?\\d*)",  70.0, 140.0);
        register("hba1c",           "(?:hba1c|glycated\\s*hemoglobin|glycosylated)[:\\s]*(\\d+\\.?\\d*)", 4.0, 5.7);
        register("insulin_fasting", "(?:fasting\\s*insulin)[:\\s]*(\\d+\\.?\\d*)",             2.6, 24.9);

        // Lipid profile
        register("total_cholesterol", "(?:total\\s*cholesterol)[:\\s]*(\\d+\\.?\\d*)",          0.0, 200.0);
        register("ldl",               "(?:ldl|low\\s*density)[:\\s]*(\\d+\\.?\\d*)",            0.0, 100.0);
        register("hdl",               "(?:hdl|high\\s*density)[:\\s]*(\\d+\\.?\\d*)",           40.0, 9999.0);
        register("triglycerides",     "(?:triglycerides?|tg)[:\\s]*(\\d+\\.?\\d*)",             0.0, 150.0);
        register("vldl",              "(?:vldl)[:\\s]*(\\d+\\.?\\d*)",                          0.0, 30.0);

        // Thyroid
        register("tsh",  "(?:tsh|thyroid\\s*stimulating)[:\\s]*(\\d+\\.?\\d*)", 0.4, 4.0);
        register("t3",   "(?:\\bt3\\b|triiodothyronine)[:\\s]*(\\d+\\.?\\d*)",  80.0, 200.0);
        register("t4",   "(?:\\bt4\\b|thyroxine)[:\\s]*(\\d+\\.?\\d*)",         5.1, 14.1);

        // Kidney function
        register("creatinine",     "(?:creatinine|creat)[:\\s]*(\\d+\\.?\\d*)",     0.7, 1.3);
        register("urea",           "(?:blood\\s*urea|bun)[:\\s]*(\\d+\\.?\\d*)",    7.0, 20.0);
        register("uric_acid",      "(?:uric\\s*acid)[:\\s]*(\\d+\\.?\\d*)",         3.5, 7.2);

        // Liver function
        register("sgot",  "(?:sgot|ast)[:\\s]*(\\d+\\.?\\d*)", 0.0, 40.0);
        register("sgpt",  "(?:sgpt|alt)[:\\s]*(\\d+\\.?\\d*)", 0.0, 40.0);
        register("bilirubin_total", "(?:total\\s*bilirubin)[:\\s]*(\\d+\\.?\\d*)", 0.2, 1.2);

        // Vitals in text
        register("systolic_bp",  "(?:bp|blood\\s*pressure)[:\\s]*(\\d{2,3})/\\d{2,3}", 90.0, 120.0);
        register("diastolic_bp", "(?:bp|blood\\s*pressure)[:\\s]*\\d{2,3}/(\\d{2,3})", 60.0, 80.0);
        register("bmi",          "(?:bmi|body\\s*mass\\s*index)[:\\s]*(\\d+\\.?\\d*)", 18.5, 24.9);
    }

    private static void register(String key, String regex, double low, double high) {
        VALUE_PATTERNS.put(key, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        NORMAL_RANGES.put(key, new double[]{low, high});
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public MedicalReportDto parse(String text) {
        Map<String, MedicalReportDto.LabValue> extracted = new HashMap<>();

        for (Map.Entry<String, Pattern> entry : VALUE_PATTERNS.entrySet()) {
            String key = entry.getKey();
            Matcher m = entry.getValue().matcher(text);
            if (!m.find()) continue;

            try {
                double value = Double.parseDouble(m.group(1).trim());
                double[] range = NORMAL_RANGES.get(key);
                MedicalReportDto.Status status = classifyStatus(value, range);
                extracted.put(key, new MedicalReportDto.LabValue(value, deriveUnit(key), range[0], range[1], status));
            } catch (NumberFormatException ignored) {}
        }

        String patientName = extractPatientName(text);
        String labName     = extractLabName(text);
        String reportDate  = extractReportDate(text);
        boolean hasCritical = extracted.values().stream()
                .anyMatch(v -> v.status() == MedicalReportDto.Status.CRITICAL);

        return new MedicalReportDto(patientName, labName, reportDate, extracted, hasCritical);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MedicalReportDto.Status classifyStatus(double value, double[] range) {
        if (value < range[0] * 0.8 || value > range[1] * 1.3) return MedicalReportDto.Status.CRITICAL;
        if (value < range[0] || value > range[1]) return MedicalReportDto.Status.ABNORMAL;
        return MedicalReportDto.Status.NORMAL;
    }

    private String deriveUnit(String key) {
        return switch (key) {
            case "hemoglobin", "hba1c" -> "g/dL";
            case "platelets" -> "per µL";
            case "total_cholesterol", "ldl", "hdl", "triglycerides", "vldl",
                 "fasting_glucose", "pp_glucose", "urea", "uric_acid" -> "mg/dL";
            case "tsh", "t3", "t4" -> "µIU/mL";
            case "creatinine", "bilirubin_total", "sgot", "sgpt" -> "mg/dL";
            case "bmi" -> "kg/m²";
            case "systolic_bp", "diastolic_bp" -> "mmHg";
            default -> "";
        };
    }

    private String extractPatientName(String text) {
        Matcher m = Pattern.compile("(?:patient\\s*name|name)[:\\s]*([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)",
                Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractLabName(String text) {
        return text.lines().map(String::trim)
                .filter(l -> l.toLowerCase().contains("lab") || l.toLowerCase().contains("diagnostic"))
                .findFirst().orElse(null);
    }

    private String extractReportDate(String text) {
        Matcher m = Pattern.compile("(?:date|collected|reported)[:\\s]*(\\d{1,2}[/\\-.\\s]\\d{1,2}[/\\-.\\s]\\d{2,4})",
                Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }
}
