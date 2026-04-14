package com.smartlife.document.service;

import com.smartlife.document.model.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
            return new DocumentClassificationResult(DocumentType.UNKNOWN, confidence);
        }

        log.debug("Classified document as {} with confidence {}", best, confidence);
        return new DocumentClassificationResult(best, Math.min(confidence, 1.0));
    }

    public record DocumentClassificationResult(DocumentType type, double confidence) {}
}
