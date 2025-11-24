package com.masterindex.parsers;

import com.masterindex.config.AppConfig;
import com.masterindex.ocr.OCRServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 图片解析器，支持OCR功能。
 *
 * @author liwenchao
 */
public class ImageParser implements DocumentParser {
    private static final Logger logger = LoggerFactory.getLogger(ImageParser.class);

    private boolean enableOCR;
    private String ocrServiceUrl;

    public ImageParser() {
        AppConfig config = AppConfig.getInstance();
        this.enableOCR = config.getIndexingConfig().isEnableOcr();
        this.ocrServiceUrl = config.getOcrConfig().getServiceUrl();
    }

    public ImageParser(boolean enableOCR, String ocrServiceUrl) {
        this.enableOCR = enableOCR;
        this.ocrServiceUrl = ocrServiceUrl;
    }

    @Override
    public boolean supports(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            return false;
        }

        String fileName = filePath.getFileName().toString().toLowerCase();
        AppConfig config = AppConfig.getInstance();
        String[] supportedExtensions = config.getIndexingConfig().getSupportedImageExtensions();

        for (String ext : supportedExtensions) {
            if (fileName.endsWith(ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String extractText(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("图片文件不存在: " + filePath);
        }

        // 检查文件大小
        long fileSizeMB = Files.size(filePath) / (1024 * 1024);
        AppConfig config = AppConfig.getInstance();
        int maxFileSizeMB = config.getIndexingConfig().getMaxFileSizeMb();

        if (fileSizeMB > maxFileSizeMB) {
            logger.warn("图片文件:{}过大: {} MB (最大允许: {} MB)", filePath.getFileName(), fileSizeMB, maxFileSizeMB);
            return null;
        }

        if (enableOCR) {
            return extractTextWithOCR(filePath);
        } else {
            return "";
        }
    }

    @Override
    public String getType() {
        return "image";
    }

    /**
     * 使用OCR服务提取图片中的文字
     */
    private String extractTextWithOCR(Path filePath) throws IOException {
        try {
            // 读取图片文件
            byte[] imageBytes = Files.readAllBytes(filePath);

            // 使用OCR服务客户端
            OCRServiceClient ocrClient = new OCRServiceClient();
            String ocrResult = ocrClient.recognizeText(imageBytes, filePath.getFileName().toString(), filePath);

            logger.info("OCR处理完成: {}", filePath);

            // 格式化文本：去除多余换行和空格
            return formatExtractedText(ocrResult);

        } catch (OCRServiceClient.OCRException e) {
            logger.error("OCR处理失败: {}", filePath, e);
            // 如果OCR失败，返回基本文件信息
            return extractBasicInfo(filePath);
        }
    }

    /**
     * 提取图片基本信息（不使用OCR）
     */
    private String extractBasicInfo(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        long fileSize = Files.size(filePath);

        return String.format("图片文件: %s, 大小: %d bytes", fileName, fileSize);
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

    /**
     * 设置是否启用OCR
     */
    public void setEnableOCR(boolean enableOCR) {
        this.enableOCR = enableOCR;
    }

    /**
     * 设置OCR服务URL
     */
    public void setOcrServiceUrl(String ocrServiceUrl) {
        this.ocrServiceUrl = ocrServiceUrl;
    }

    /**
     * 获取OCR启用状态
     */
    public boolean isEnableOCR() {
        return enableOCR;
    }

    /**
     * 获取OCR服务URL
     */
    public String getOcrServiceUrl() {
        return ocrServiceUrl;
    }

    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        AppConfig config = AppConfig.getInstance();
        this.enableOCR = config.getIndexingConfig().isEnableOcr();
        this.ocrServiceUrl = config.getOcrConfig().getServiceUrl();
    }
}