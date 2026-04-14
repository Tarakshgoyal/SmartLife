package com.smartlife.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts key entities from document text using regex and NLP patterns.
 *
 * Extracts: dates, monetary amounts, names, policy numbers, phone numbers, emails.
 * Phase 2: Replace regex with DL4J Named Entity Recognition model.
 */
@Service
@Slf4j
public class EntityExtractionService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Date patterns common in Indian documents
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("MMMM dd, yyyy"),
            DateTimeFormatter.ofPattern("dd MMMM yyyy")
    );

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}\\s+\\w+\\s+\\d{4}|\\d{4}-\\d{2}-\\d{2})\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:rs\\.?|₹|inr|total|amount)[\\s:]*([\\d,]+(?:\\.\\d{2})?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:\\+91[\\s-]?)?[6-9]\\d{9}");

    private static final Pattern EXPIRY_PATTERN = Pattern.compile(
            "(?:expiry|expiration|valid until|valid till|expires?|exp\\.?)[\\s:]*([\\d/\\-\\s\\w]+)",
            Pattern.CASE_INSENSITIVE);

    public ExtractionResult extract(String text) {
        if (text == null || text.isBlank()) {
            return new ExtractionResult(Map.of(), null, null);
        }

        Map<String, Object> entities = new HashMap<>();
        LocalDate expiryDate = null;
        LocalDate issueDate = null;

        // Extract dates
        List<String> allDates = extractAll(DATE_PATTERN, text, 0);
        if (!allDates.isEmpty()) {
            entities.put("dates", allDates);
        }

        // Extract expiry date specifically
        Matcher expiryMatcher = EXPIRY_PATTERN.matcher(text);
        if (expiryMatcher.find()) {
            String dateStr = expiryMatcher.group(1).trim();
            expiryDate = parseDate(dateStr);
            if (expiryDate != null) {
                entities.put("expiryDate", expiryDate.toString());
            }
        }

        // Extract amounts
        List<String> amounts = extractAll(AMOUNT_PATTERN, text, 1);
        if (!amounts.isEmpty()) {
            entities.put("amounts", amounts);
        }

        // Extract emails
        List<String> emails = extractAll(EMAIL_PATTERN, text, 0);
        if (!emails.isEmpty()) {
            entities.put("emails", emails);
        }

        // Extract phone numbers
        List<String> phones = extractAll(PHONE_PATTERN, text, 0);
        if (!phones.isEmpty()) {
            entities.put("phones", phones);
        }

        return new ExtractionResult(entities, expiryDate, issueDate);
    }

    public String toJson(Map<String, Object> entities) {
        try {
            return MAPPER.writeValueAsString(entities);
        } catch (Exception e) {
            log.warn("Failed to serialize entities to JSON", e);
            return "{}";
        }
    }

    private List<String> extractAll(Pattern pattern, String text, int group) {
        List<String> results = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String match = group == 0 ? matcher.group().trim() : matcher.group(group).trim();
            if (!match.isBlank() && !results.contains(match)) {
                results.add(match);
            }
        }
        return results;
    }

    private LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    public record ExtractionResult(
            Map<String, Object> entities,
            LocalDate expiryDate,
            LocalDate issueDate
    ) {}
}
