package com.smartlife.document.service;

import com.smartlife.config.OllamaService;
import com.smartlife.document.model.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * ML-based document classification using keyword scoring and Tribuo classifiers.
 *
 * Phase 1: Keyword-based classification (production-ready out of the box).
 * Phase 2: Tribuo LinearSGDClassifier trained on labelled data (see TrainingDataLoader).
 */
@Service
@Slf4j
public class DocumentClassificationService {

    @Lazy
    @Autowired
    private OllamaService ollamaService;

    private static final Map<DocumentType, String[]> KEYWORD_MAP = new HashMap<>();

    static {
        KEYWORD_MAP.put(DocumentType.MEDICAL, new String[]{
                "patient", "diagnosis", "prescription", "hospital", "clinic", "dr.", "doctor",
                "blood", "test", "report", "medication", "dosage", "treatment", "symptoms",
                "laboratory", "pathology", "radiology", "biopsy", "glucose", "hemoglobin"
        });
        KEYWORD_MAP.put(DocumentType.FINANCIAL, new String[]{
                "bank", "account", "balance", "transaction", "statement", "debit", "credit",
                "invoice", "payment", "amount", "gst", "tax", "receipt", "purchase order",
                "cheque", "draft", "fund", "interest", "loan", "emi"
        });
        KEYWORD_MAP.put(DocumentType.IDENTITY, new String[]{
                "passport", "visa", "aadhaar", "pan card", "driving licence", "voter id",
                "date of birth", "nationality", "citizen", "photo id", "identity", "dob",
                "surname", "given name", "place of birth"
        });
        KEYWORD_MAP.put(DocumentType.WARRANTY, new String[]{
                "warranty", "guarantee", "product", "serial number", "model", "manufacturer",
                "defect", "repair", "replacement", "service centre", "purchase date", "valid till"
        });
        KEYWORD_MAP.put(DocumentType.LEGAL, new String[]{
                "agreement", "contract", "deed", "notary", "affidavit", "court", "judgement",
                "property", "lease", "rent", "party", "clause", "witness", "stamp duty",
                "arbitration", "terms and conditions"
        });
        KEYWORD_MAP.put(DocumentType.RECEIPT, new String[]{
                "receipt", "total", "subtotal", "item", "qty", "quantity", "price",
                "discount", "tax", "cash", "card", "upi", "paid", "thank you for shopping"
        });
        KEYWORD_MAP.put(DocumentType.BILL, new String[]{
                "electricity", "water", "gas", "broadband", "mobile", "bill", "due date",
                "units consumed", "tariff", "meter reading", "connection", "utility"
        });
        KEYWORD_MAP.put(DocumentType.INSURANCE, new String[]{
                "policy", "premium", "insured", "insurer", "coverage", "claim", "beneficiary",
                "maturity", "sum assured", "nominee", "life insurance", "health insurance"
        });
    }

    public DocumentClassificationResult classify(String text) {
        if (text == null || text.isBlank()) {
            return new DocumentClassificationResult(DocumentType.UNKNOWN, 0.0);
        }

        String lowerText = text.toLowerCase();
        Map<DocumentType, Integer> scores = new HashMap<>();

        for (Map.Entry<DocumentType, String[]> entry : KEYWORD_MAP.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (lowerText.contains(keyword)) score++;
            }
            scores.put(entry.getKey(), score);
        }

        DocumentType best = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(DocumentType.UNKNOWN);

        int maxScore = scores.getOrDefault(best, 0);
        int totalKeywords = KEYWORD_MAP.getOrDefault(best, new String[0]).length;
        double confidence = totalKeywords > 0 ? (double) maxScore / totalKeywords : 0.0;

        // Require at least 2 keyword matches to confidently classify
        if (maxScore < 2) {
            // ── Llama 3.2 fallback for ambiguous documents ─────────────────
            DocumentClassificationResult aiResult = classifyWithAi(text);
            if (aiResult != null) return aiResult;
            return new DocumentClassificationResult(DocumentType.UNKNOWN, confidence);
        }

        log.debug("Classified document as {} with confidence {}", best, confidence);
        return new DocumentClassificationResult(best, Math.min(confidence, 1.0));
    }

    /**
     * Uses Llama 3.2 to classify documents that keyword scoring couldn't identify.
     */
    private DocumentClassificationResult classifyWithAi(String text) {
        try {
            String snippet = text.length() > 800 ? text.substring(0, 800) : text;
            String validTypes = Arrays.stream(DocumentType.values())
                    .map(Enum::name)
                    .reduce((a, b) -> a + ", " + b).orElse("");

            String aiResponse = ollamaService.generate(
                "You are a document classifier. Reply with ONLY the document type from this list: " + validTypes +
                ". Do not explain — just output one word from the list.",
                "Classify this document text:\n\n" + snippet
            );

            if (aiResponse != null) {
                String normalized = aiResponse.trim().toUpperCase().replaceAll("[^A-Z_]", "");
                try {
                    DocumentType aiType = DocumentType.valueOf(normalized);
                    log.debug("AI classified document as {}", aiType);
                    return new DocumentClassificationResult(aiType, 0.7);
                } catch (IllegalArgumentException ignored) {
                    // AI returned something invalid — fall through to UNKNOWN
                }
            }
        } catch (Exception e) {
            log.debug("AI document classification skipped: {}", e.getMessage());
        }
        return null;
    }

    public record DocumentClassificationResult(DocumentType type, double confidence) {}
}
