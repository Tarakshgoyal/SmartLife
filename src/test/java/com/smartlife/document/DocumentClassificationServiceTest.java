package com.smartlife.document;

import com.smartlife.document.model.DocumentType;
import com.smartlife.document.service.DocumentClassificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Document Classification Service")
class DocumentClassificationServiceTest {

    private DocumentClassificationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentClassificationService();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "Patient diagnosis prescription blood test report hospital, MEDICAL",
            "Bank account statement debit credit transaction balance, FINANCIAL",
            "Passport nationality date of birth place of birth, IDENTITY",
            "Warranty guarantee product serial number manufacturer defect repair, WARRANTY",
            "Agreement contract deed property lease rent witness clause, LEGAL",
            "Electricity bill units consumed meter reading BESCOM due date, BILL",
            "Receipt total subtotal item qty price paid thank you for shopping, RECEIPT",
            "Policy premium insured coverage claim life insurance nominee, INSURANCE",
    })
    void classifyText(String text, String expectedType) {
        var result = service.classify(text);
        assertThat(result.type()).isEqualTo(DocumentType.valueOf(expectedType));
        assertThat(result.confidence()).isGreaterThan(0.0);
    }

    @ParameterizedTest(name = "short text returns low confidence")
    @CsvSource({"hello world, UNKNOWN", "  , UNKNOWN"})
    void classifyShortOrBlankText(String text, String expectedType) {
        var result = service.classify(text);
        assertThat(result.type()).isEqualTo(DocumentType.valueOf(expectedType));
    }
}
