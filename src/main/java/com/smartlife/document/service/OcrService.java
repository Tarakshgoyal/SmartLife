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

    public OcrService(@Value("${smartlife.ocr.tessdata-path}") String tessdataPath,
                      @Value("${smartlife.ocr.language}") String language) {
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath(tessdataPath);
        this.tesseract.setLanguage(language);
        this.tesseract.setOcrEngineMode(1);  // LSTM neural net mode
        this.tesseract.setPageSegMode(3);    // Fully automatic page segmentation
    }

    public String extractText(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".pdf")) {
            return extractFromPdf(filePath.toFile());
        } else {
            return extractFromImage(filePath.toFile());
        }
    }

    private String extractFromImage(File imageFile) {
        try {
            log.debug("Running OCR on image: {}", imageFile.getName());
            return tesseract.doOCR(imageFile).trim();
        } catch (TesseractException e) {
            log.error("OCR failed for image: {}", imageFile.getName(), e);
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
                // Save temp image and OCR it
                File tempImg = File.createTempFile("smartlife_ocr_", ".png");
                try {
                    ImageIO.write(image, "PNG", tempImg);
                    String text = tesseract.doOCR(tempImg);
                    pageTexts.add(text);
                } catch (TesseractException e) {
                    log.warn("OCR failed for page {} of {}", page, pdfFile.getName());
                } finally {
                    tempImg.delete();
                }
            }
        } catch (IOException e) {
            log.error("Failed to process PDF: {}", pdfFile.getName(), e);
        }

        return String.join("\n\n--- Page Break ---\n\n", pageTexts).trim();
    }
}
