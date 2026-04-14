package com.smartlife.expense.service;

import com.smartlife.expense.ml.TribuoExpenseClassifier;
import com.smartlife.expense.model.ExpenseCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Classifies expenses into categories.
 * Routes to Tribuo ML model when trained; falls back to keyword heuristics otherwise.
 *
 * Phase 1: Keyword-based classification.
 * Phase 2: Tribuo RandomForest classifier trained on labelled merchant data.
 */
@Service
@RequiredArgsConstructor
public class ExpenseCategoryClassifier {

    private final TribuoExpenseClassifier tribuoClassifier;

    private static final Map<ExpenseCategory, String[]> KEYWORD_MAP = new HashMap<>();

    static {
        KEYWORD_MAP.put(ExpenseCategory.GROCERIES, new String[]{
                "reliance fresh", "dmart", "big bazaar", "more", "spencer", "grocery",
                "supermarket", "vegetables", "fruits", "kirana", "nature's basket"
        });
        KEYWORD_MAP.put(ExpenseCategory.DINING, new String[]{
                "zomato", "swiggy", "restaurant", "cafe", "hotel", "food", "dhaba",
                "pizza", "burger", "mcdonald", "kfc", "dominos", "biryani", "haldiram"
        });
        KEYWORD_MAP.put(ExpenseCategory.TRANSPORT, new String[]{
                "ola", "uber", "rapido", "metro", "irctc", "railway", "bus", "fuel",
                "petrol", "diesel", "hp", "iocl", "bharat petroleum", "auto", "cab"
        });
        KEYWORD_MAP.put(ExpenseCategory.UTILITIES, new String[]{
                "electricity", "bescom", "tpddl", "mseb", "water", "gas", "piped",
                "airtel", "jio", "vi", "bsnl", "broadband", "internet", "wifi"
        });
        KEYWORD_MAP.put(ExpenseCategory.HEALTHCARE, new String[]{
                "pharmacy", "medplus", "apollo", "hospital", "clinic", "doctor",
                "medicine", "diagnostic", "lab", "pathology", "netmeds", "pharmeasy"
        });
        KEYWORD_MAP.put(ExpenseCategory.ENTERTAINMENT, new String[]{
                "netflix", "amazon prime", "hotstar", "sony liv", "zee5", "youtube",
                "spotify", "gaana", "cinema", "pvr", "inox", "movie", "game"
        });
        KEYWORD_MAP.put(ExpenseCategory.SHOPPING, new String[]{
                "amazon", "flipkart", "myntra", "ajio", "meesho", "snapdeal", "nykaa",
                "mall", "store", "shop", "clothing", "fashion", "electronics"
        });
        KEYWORD_MAP.put(ExpenseCategory.EDUCATION, new String[]{
                "udemy", "coursera", "byju", "unacademy", "school", "college", "tuition",
                "books", "stationery", "fees", "university"
        });
        KEYWORD_MAP.put(ExpenseCategory.SUBSCRIPTION, new String[]{
                "subscription", "renewal", "annual", "membership", "plan"
        });
        KEYWORD_MAP.put(ExpenseCategory.TRAVEL, new String[]{
                "makemytrip", "goibibo", "cleartrip", "yatra", "hotel booking",
                "flight", "airline", "airport", "passport", "visa"
        });
    }

    public ExpenseCategory classify(String description, String merchant) {
        // Prefer ML model if available
        var mlResult = tribuoClassifier.predict(description, merchant);
        if (mlResult.isPresent()) return mlResult.get();

        // Keyword fallback
        String text = ((description != null ? description : "") + " " +
                       (merchant != null ? merchant : "")).toLowerCase().trim();

        if (text.isBlank()) return ExpenseCategory.OTHER;

        Map<ExpenseCategory, Integer> scores = new HashMap<>();
        for (Map.Entry<ExpenseCategory, String[]> entry : KEYWORD_MAP.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) score++;
            }
            scores.put(entry.getKey(), score);
        }

        return scores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(ExpenseCategory.OTHER);
    }
}
