package com.masterindex.parsers;

import com.masterindex.config.AppConfig;
import com.masterindex.ocr.OCRServiceClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF文档解析器，支持OCR处理内嵌图片和扫描页面。
 *
 * @author liwenchao
 */
public class PdfParser implements DocumentParser {
    private static final Logger logger = LoggerFactory.getLogger(PdfParser.class);

    private boolean enableOcr;
    private final OCRServiceClient ocrClient;

    public PdfParser() {
        AppConfig config = AppConfig.getInstance();
        this.enableOcr = config.getIndexingConfig().isEnableOcr();
        this.ocrClient = new OCRServiceClient();
    }

    @Override
    public boolean supports(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".pdf");
    }

    @Override
    public String extractText(Path filePath) throws IOException {
        StringBuilder content = new StringBuilder();

        try (PDDocument document = PDDocument.load(new File(filePath.toString()))) {
            // 首先尝试提取文本内容
            String textContent = extractTextFromPdf(document);
            if (textContent != null && !textContent.trim().isEmpty()) {
                content.append(textContent);
            }

            // 如果启用OCR，只提取并处理内嵌图片，不渲染整个页面
            if (enableOcr) {
                String ocrText = extractEmbeddedImagesWithOcr(document, filePath);
                if (!ocrText.trim().isEmpty()) {
                    if (content.length() > 0) {
                        content.append("\n\n");
                    }
                    content.append("[OCR识别的图片内容]\n");
                    content.append(ocrText);
                }
            }

            // 格式化文本：去除多余换行和空格
            return formatExtractedText(content.toString());

        } catch (Exception e) {
            logger.error("从PDF提取文本失败: {}", filePath, e);
            throw new IOException("parsing pdf documents failed", e);
        }
    }

    /**
     * 从PDF文档中提取文本内容
     */
    private String extractTextFromPdf(PDDocument document) {
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // 如果提取到的文本很少，可能是扫描件
            if (text != null && text.trim().length() < 50) {
                logger.debug("PDF文本内容很少，可能是扫描件");
            }

            return text;
        } catch (Exception e) {
            logger.warn("PDF文本提取失败", e);
            return "";
        }
    }

    /**
     * 从PDF文档中提取内嵌图片并进行OCR处理
     */
    private String extractEmbeddedImagesWithOcr(PDDocument document, Path filePath) {
        StringBuilder ocrResults = new StringBuilder();
        List<byte[]> images = extractEmbeddedImages(document);

        if (images.isEmpty()) {
            logger.debug("PDF文档中没有发现内嵌图片: {}", filePath);
            return "";
        }

        logger.info("发现 {} 个内嵌图片，开始OCR处理: {}", images.size(), filePath);

        // 创建临时目录存放提取的图片
        Path tempDir = null;
        try {
            tempDir = java.nio.file.Files.createTempDirectory("pdf_images_");

            for (int i = 0; i < images.size(); i++) {
                Path imagePath = null;
                try {
                    byte[] imageBytes = images.get(i);

                    // 保存图片到临时文件
                    String imageFileName = filePath.getFileName().toString()
                            + "_image_" + (i + 1) + ".png";
                    imagePath = tempDir.resolve(imageFileName);
                    java.nio.file.Files.write(imagePath, imageBytes);

                    // 使用图片的实际路径进行OCR
                    String ocrText = ocrClient.recognizeText(imageBytes, imageFileName, imagePath);

                    if (!ocrText.trim().isEmpty()) {
                        if (ocrResults.length() > 0) {
                            ocrResults.append("\n");
                        }
                        ocrResults.append(ocrText);
                    }

                } catch (OCRServiceClient.OCRException e) {
                    logger.warn("OCR处理失败，跳过图片 {} in {}: {}", i + 1, filePath, e.getMessage());
                } catch (Exception e) {
                    logger.error("处理PDF图片时出错 {}: {}", filePath, e.getMessage(), e);
                } finally {
                    // 清理单个临时图片文件
                    if (imagePath != null) {
                        try {
                            java.nio.file.Files.deleteIfExists(imagePath);
                        } catch (IOException e) {
                            logger.warn("删除临时图片文件失败: {}", imagePath, e);
                        }
                    }
                }
            }

        } catch (IOException e) {
            logger.error("创建临时目录失败", e);
        } finally {
            // 清理临时目录
            if (tempDir != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempDir);
                } catch (IOException e) {
                    logger.warn("删除临时目录失败: {}", tempDir, e);
                }
            }
        }

        return ocrResults.toString();
    }

    /**
     * 从PDF文档中提取内嵌图片（不渲染页面）
     */
    private List<byte[]> extractEmbeddedImages(PDDocument document) {
        List<byte[]> images = new ArrayList<>();

        try {
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                PDPage page = document.getPage(pageIndex);
                PDResources resources = page.getResources();

                if (resources != null) {
                    // 遍历资源中的所有对象
                    for (COSName cosName : resources.getXObjectNames()) {
                        PDXObject xobject = resources.getXObject(cosName);
                        if (xobject instanceof PDImageXObject) {
                            try {
                                PDImageXObject imageXobject = (PDImageXObject) xobject;
                                BufferedImage img = imageXobject.getImage();

                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ImageIO.write(img, "PNG", baos);
                                byte[] imageBytes = baos.toByteArray();

                                if (imageBytes.length > 0) {
                                    images.add(imageBytes);
                                    logger.debug("提取内嵌图片数据，大小: {} bytes", imageBytes.length);
                                }
                            } catch (Exception e) {
                                logger.debug("提取内嵌图片失败", e);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("提取PDF内嵌图片时出错", e);
        }

        return images;
    }

    /**
     * 格式化提取的文本：去除多余换行和空格
     */
    private String formatExtractedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // 替换多个连续空格为单个空格
        text = text.replaceAll("\\s+", "");

        // 去除首尾空格
        text = text.trim();

        return text;
    }

    @Override
    public String getType() {
        return "document";
    }

    /**
     * 设置是否启用OCR
     */
    public void setEnableOcr(boolean enableOcr) {
        this.enableOcr = enableOcr;
    }

    /**
     * 获取OCR启用状态
     */
    public boolean isEnableOcr() {
        return enableOcr;
    }

    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        AppConfig config = AppConfig.getInstance();
        this.enableOcr = config.getIndexingConfig().isEnableOcr();
    }
}