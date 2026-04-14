package com.smartlife.expense;

import com.smartlife.document.service.OcrService;
import com.smartlife.expense.model.ExpenseCategory;
import com.smartlife.expense.service.ExpenseCategoryClassifier;
import com.smartlife.expense.service.ReceiptScanningService;
import com.smartlife.expense.ml.TribuoExpenseClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptScanningServiceTest {

    @Mock private OcrService ocrService;
    @Mock private TribuoExpenseClassifier tribuoExpenseClassifier;

    private ReceiptScanningService service;

    private static final String SAMPLE_RECEIPT = """
            BIG BAZAAR HYPERMARKET
            Date: 12/04/2026

            Rice 5kg                   2   x  250.00    500.00
            Milk 1L                    4   x   55.00    220.00
            Bread                      1   x   40.00     40.00

            Subtotal                              760.00
            GST 5%                                 38.00
            Grand Total                           798.00

            Thank you for shopping!
            """;

    @BeforeEach
    void setUp() {
        when(tribuoExpenseClassifier.predict(null, null))
                .thenReturn(java.util.Optional.empty());
        var classifier = new ExpenseCategoryClassifier(tribuoExpenseClassifier);
        service = new ReceiptScanningService(ocrService, classifier);
    }

    @Test
    void parseTotal() {
        var result = service.parseReceiptText(SAMPLE_RECEIPT);
        assertThat(result.totalAmount()).isEqualByComparingTo(new BigDecimal("798.00"));
    }

    @Test
    void parseSubtotal() {
        var result = service.parseReceiptText(SAMPLE_RECEIPT);
        assertThat(result.subtotal()).isEqualByComparingTo(new BigDecimal("760.00"));
    }

    @Test
    void parseMerchant() {
        var result = service.parseReceiptText(SAMPLE_RECEIPT);
        assertThat(result.merchant()).containsIgnoringCase("BIG BAZAAR");
    }

    @Test
    void parseDate() {
        var result = service.parseReceiptText(SAMPLE_RECEIPT);
        assertThat(result.purchaseDate()).isNotNull();
    }

    @Test
    void classifyAsGroceries() {
        var result = service.parseReceiptText(SAMPLE_RECEIPT);
        assertThat(result.suggestedCategory()).isEqualTo(ExpenseCategory.GROCERIES);
    }

    @Test
    void lineItemsExtracted() {
        var result = service.parseReceiptText(SAMPLE_RECEIPT);
        assertThat(result.lineItems()).hasSizeGreaterThanOrEqualTo(2);
    }
}
