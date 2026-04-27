package com.smartlife.document.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OcrService {

    private final Tesseract tesseract;

    private boolean tesseractAvailable = false;

    public OcrService(@Value("${smartlife.ocr.tessdata-path}") String tessdataPath,
                      @Value("${smartlife.ocr.language}") String language) {
        this.tesseract = new Tesseract();
        try {
            // Verify tessdata file actually exists before marking available
            java.io.File trainedData = new java.io.File(tessdataPath, language + ".traineddata");
            if (!trainedData.exists()) {
                log.warn("Tesseract OCR unavailable — tessdata not found at '{}'. Documents will be saved without OCR.", trainedData.getAbsolutePath());
                return;
            }
            this.tesseract.setDatapath(tessdataPath);
            this.tesseract.setLanguage(language);
            this.tesseract.setOcrEngineMode(1);
            this.tesseract.setPageSegMode(3);
            tesseractAvailable = true;
            log.info("Tesseract OCR initialised from: {}", tessdataPath);
        } catch (Throwable e) {
            log.warn("Tesseract OCR unavailable: {}. Documents will be saved without OCR text.", e.getMessage());
        }
    }

    public String extractText(Path filePath) {
        if (!tesseractAvailable) {
            log.debug("OCR skipped — Tesseract not configured.");
            return "";
        }
        String fileName = filePath.getFileName().toString().toLowerCase();
        // Only run OCR on image and PDF files; silently skip other types (docx, etc.)
        if (fileName.endsWith(".pdf")) {
            return extractFromPdf(filePath.toFile());
        } else if (fileName.matches(".*\\.(png|jpg|jpeg|gif|bmp|tiff|tif|webp)$")) {
            return extractFromImage(filePath.toFile());
        } else {
            log.debug("OCR skipped for non-image/PDF file: {}", fileName);
            return "";
        }
    }

    private String extractFromImage(File imageFile) {
        try {
            log.debug("Running OCR on image: {}", imageFile.getName());
            return tesseract.doOCR(imageFile).trim();
        } catch (Throwable e) {
            log.warn("OCR failed for image '{}': {}", imageFile.getName(), e.getMessage());
            return "";
        }
    }

    private String extractFromPdf(File pdfFile) {
        List<String> pageTexts = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int page = 0; page < Math.min(pageCount, 10); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 300);
                File tempImg = File.createTempFile("smartlife_ocr_", ".png");
                try {
                    ImageIO.write(image, "PNG", tempImg);
                    String text = tesseract.doOCR(tempImg);
                    pageTexts.add(text);
                } catch (Throwable e) {
                    log.warn("OCR failed for page {} of '{}': {}", page, pdfFile.getName(), e.getMessage());
                } finally {
                    tempImg.delete();
                }
            }
        } catch (IOException e) {
            log.error("Failed to load PDF: {}", pdfFile.getName(), e);
        }

        return String.join("\n\n--- Page Break ---\n\n", pageTexts).trim();
    }
}
