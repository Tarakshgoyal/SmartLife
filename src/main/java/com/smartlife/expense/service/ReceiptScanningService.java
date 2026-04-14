package com.smartlife.expense.service;

import com.smartlife.document.service.OcrService;
import com.smartlife.expense.dto.ReceiptScanResultDto;
import com.smartlife.expense.model.ExpenseCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans receipt images/PDFs and extracts structured expense data.
 *
 * Pipeline: Image → Tess4J OCR → Regex NLP extraction → Structured ReceiptScanResult
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptScanningService {

    private final OcrService ocrService;
    private final ExpenseCategoryClassifier categoryClassifier;

    // ── Regex patterns ────────────────────────────────────────────────────────

    private static final Pattern TOTAL_PATTERN = Pattern.compile(
            "(?:grand\\s*total|total\\s*amount|net\\s*amount|amount\\s*due|total)[\\s:₹Rs.]*([\\d,]+\\.?\\d{0,2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SUBTOTAL_PATTERN = Pattern.compile(
            "(?:subtotal|sub-total|sub\\s*total)[\\s:₹Rs.]*([\\d,]+\\.?\\d{0,2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TAX_PATTERN = Pattern.compile(
            "(?:gst|tax|cgst|sgst|igst|service\\s*charge)[\\s@%\\d]*[:\\s₹Rs.]*([\\d,]+\\.?\\d{0,2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4})\\b");

    // Line item: name ... quantity ... price pattern
    private static final Pattern LINE_ITEM_PATTERN = Pattern.compile(
            "^(.{3,40})\\s+(\\d+)\\s+[x×]?\\s*([\\d,.]+)\\s+([\\d,.]+)\\s*$",
            Pattern.MULTILINE);

    private static final Pattern SIMPLE_LINE_ITEM_PATTERN = Pattern.compile(
            "^(.{3,50})\\s{2,}([\\d,]+\\.?\\d{0,2})\\s*$",
            Pattern.MULTILINE);

    private static final List<DateTimeFormatter> DATE_FMTS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );

    // ── Public API ────────────────────────────────────────────────────────────

    public ReceiptScanResultDto scanReceipt(MultipartFile file) throws IOException {
        Path tempFile = saveTemp(file);
        try {
            String text = ocrService.extractText(tempFile);
            log.debug("OCR extracted {} chars from receipt", text.length());
            return parseReceiptText(text);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    public ReceiptScanResultDto parseReceiptText(String text) {
        String merchant = extractMerchant(text);
        BigDecimal total = extractAmount(TOTAL_PATTERN, text);
        BigDecimal subtotal = extractAmount(SUBTOTAL_PATTERN, text);
        BigDecimal tax = extractAmount(TAX_PATTERN, text);
        LocalDate date = extractDate(text);
        List<ReceiptScanResultDto.LineItem> items = extractLineItems(text);
        ExpenseCategory category = categoryClassifier.classify(text, merchant);

        // Fallback: if no explicit total, sum line items
        if (total == null && !items.isEmpty()) {
            total = items.stream()
                    .map(ReceiptScanResultDto.LineItem::totalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        return new ReceiptScanResultDto(
                merchant,
                total != null ? total : BigDecimal.ZERO,
                subtotal,
                tax,
                date,
                category,
                items,
                text
        );
    }

    private String extractMerchant(String text) {
        // First non-empty line is typically the merchant/store name
        return text.lines()
                .map(String::trim)
                .filter(l -> l.length() > 3 && !l.matches(".*\\d{6,}.*")) // skip lines with long numbers
                .findFirst()
                .orElse("Unknown Merchant");
    }

    private BigDecimal extractAmount(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        // Use the LAST match (totals appear at the bottom of receipts)
        BigDecimal last = null;
        while (m.find()) {
            try {
                last = new BigDecimal(m.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {}
        }
        return last;
    }

    private LocalDate extractDate(String text) {
        Matcher m = DATE_PATTERN.matcher(text);
        while (m.find()) {
            for (DateTimeFormatter fmt : DATE_FMTS) {
                try {
                    return LocalDate.parse(m.group(1).replace(".", "/").replace("-", "/"), fmt);
                } catch (DateTimeParseException ignored) {}
            }
        }
        return LocalDate.now();
    }

    private List<ReceiptScanResultDto.LineItem> extractLineItems(String text) {
        List<ReceiptScanResultDto.LineItem> items = new ArrayList<>();

        // Try structured pattern: Name  qty  unit_price  total
        Matcher m = LINE_ITEM_PATTERN.matcher(text);
        while (m.find()) {
            try {
                String name = m.group(1).trim();
                int qty = Integer.parseInt(m.group(2).trim());
                BigDecimal unitPrice = new BigDecimal(m.group(3).replace(",", ""));
                BigDecimal totalPrice = new BigDecimal(m.group(4).replace(",", ""));
                items.add(new ReceiptScanResultDto.LineItem(name, qty, unitPrice, totalPrice));
            } catch (NumberFormatException ignored) {}
        }

        // Fallback: simple "Name ... Amount"
        if (items.isEmpty()) {
            Matcher sm = SIMPLE_LINE_ITEM_PATTERN.matcher(text);
            while (sm.find()) {
                String name = sm.group(1).trim();
                // Skip totals/subtotals in line items
                if (name.toLowerCase().matches(".*(total|tax|gst|subtotal|change|cash).*")) continue;
                try {
                    BigDecimal price = new BigDecimal(sm.group(2).replace(",", ""));
                    items.add(new ReceiptScanResultDto.LineItem(name, 1, price, price));
                } catch (NumberFormatException ignored) {}
            }
        }

        return items.stream().limit(30).toList(); // cap at 30 line items
    }

    private Path saveTemp(MultipartFile file) throws IOException {
        String suffix = file.getOriginalFilename() != null
                ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'))
                : ".tmp";
        Path tmp = Files.createTempFile("sl_receipt_", suffix);
        file.transferTo(tmp);
        return tmp;
    }
}
