package com.testehan.deepresearch.pipeline;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessingService.class);

    public List<byte[]> convertPdfToImages(MultipartFile pdfFile) throws IOException {
        List<byte[]> images = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfFile.getBytes())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(bim, "png", baos);
                    images.add(baos.toByteArray());
                }
            }
        }
        logger.info("Converted PDF with {} pages to images.", images.size());
        return images;
    }
}
