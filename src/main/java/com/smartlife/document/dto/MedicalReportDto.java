package com.smartlife.document.dto;

import java.util.Map;

public record MedicalReportDto(
        String patientName,
        String labName,
        String reportDate,
        Map<String, LabValue> values,
        boolean hasCriticalValues
) {
    public enum Status { NORMAL, ABNORMAL, CRITICAL }

    public record LabValue(
            double value,
            String unit,
            double normalLow,
            double normalHigh,
            Status status
    ) {}
}
