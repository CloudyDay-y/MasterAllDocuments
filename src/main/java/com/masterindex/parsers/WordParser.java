package com.masterindex.parsers;

import com.masterindex.config.AppConfig;
import com.masterindex.ocr.OCRServiceClient;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Microsoft Word文档解析器，支持OCR处理内嵌图片。
 * @author liwenchao
 */
public class WordParser implements DocumentParser {
    private static final Logger logger = LoggerFactory.getLogger(WordParser.class);

    private boolean enableOcr;
    private final OCRServiceClient ocrClient;

    public WordParser() {
        AppConfig config = AppConfig.getInstance();
        this.enableOcr = config.getIndexingConfig().isEnableOcr();
        this.ocrClient = new OCRServiceClient();
    }

    @Override
    public boolean supports(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".docx") || fileName.endsWith(".doc");
    }

    @Override
    public String extractText(Path filePath) throws IOException {
        StringBuilder content = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            String fileName = filePath.getFileName().toString().toLowerCase();

            if (fileName.endsWith(".docx")) {
                // 处理 .docx 格式
                try (XWPFDocument document = new XWPFDocument(fis)) {

                    // 如果启用OCR，提取并处理内嵌图片
                    if (enableOcr) {
                        String ocrText = extractImagesWithOcr(document, filePath);
                        if (!ocrText.trim().isEmpty()) {
                            if (content.length() > 0) {
                                content.append("\n");
                            }
                            content.append(ocrText);
                        }
                    }

                    // 提取文本内容
                    try (XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                        String textContent = extractor.getText();
                        if (textContent != null && !textContent.trim().isEmpty()) {
                            content.append(textContent);
                        }
                    }
                }
            } else if (fileName.endsWith(".doc")) {
                // 处理 .doc 格式
                try (HWPFDocument document = new HWPFDocument(fis)) {
                    // 提取文本内容
                    try (WordExtractor extractor = new WordExtractor(document)) {
                        String textContent = extractor.getText();
                        if (textContent != null && !textContent.trim().isEmpty()) {
                            content.append(textContent);
                        }
                    }
                    // .doc 格式的图片提取较复杂，这里暂不实现OCR
                }
            }

            // 格式化文本：去除多余换行和空格
            return formatExtractedText(content.toString());

        } catch (Exception e) {
            logger.error("从Word文档提取文本失败: {}", filePath, e);
            throw new IOException("parsing word document failed", e);
        }
    }

    /**
     * 从Word文档中提取图片并进行OCR处理
     */
    private String extractImagesWithOcr(XWPFDocument document, Path filePath) {
        StringBuilder ocrResults = new StringBuilder();
        List<byte[]> images = extractImagesFromDocument(document);

        if (images.isEmpty()) {
            logger.debug("Word文档中没有发现内嵌图片: {}", filePath);
            return "";
        }

        logger.info("发现 {} 个内嵌图片，开始OCR处理: {}", images.size(), filePath);

        // 创建临时目录存放提取的图片
        Path tempDir = null;
        try {
            tempDir = java.nio.file.Files.createTempDirectory("word_images_");

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
                    logger.error("处理Word文档内嵌图片时出错 {}: {}", filePath, e.getMessage(), e);
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
     * 从Word文档中提取所有图片
     */
    private List<byte[]> extractImagesFromDocument(XWPFDocument document) {
        List<byte[]> images = new ArrayList<>();

        try {
            // 获取文档中的所有图片数据
            List<XWPFPictureData> pictures = document.getAllPictures();

            for (XWPFPictureData picture : pictures) {
                try {
                    byte[] pictureData = picture.getData();
                    if (pictureData != null && pictureData.length > 0) {
                        images.add(pictureData);
                        logger.debug("提取图片数据，大小: {} bytes, 格式: {}",
                                   pictureData.length, picture.getPictureType());
                    }
                } catch (Exception e) {
                    logger.error("提取图片数据失败", e);
                }
            }

        } catch (Exception e) {
            logger.error("从Word文档提取图片时出错", e);
        }

        return images;
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

    /**
     * 格式化提取的文本：去除多余换行和空格
     */
    private String formatExtractedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        // 替换多个连续空格为单个空格
        text = text.replaceAll("\\s+", " ");

        // 去除首尾空格
        text = text.trim();

        return text;
    }
}