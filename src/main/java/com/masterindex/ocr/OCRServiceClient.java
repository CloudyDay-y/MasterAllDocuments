package com.masterindex.ocr;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.masterindex.config.AppConfig;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

/**
 * OCR服务客户端，用于调用OCR API
 * @author liwenchao
 */
public class OCRServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(OCRServiceClient.class);

    /**
     * 调用OCR服务识别图片中的文字
     *
     * @param imageBytes 图片字节数组
     * @param fileName   文件名
     * @param filePath
     * @return 识别出的文字内容
     * @throws OCRException OCR处理异常
     */
    public String recognizeText(byte[] imageBytes, String fileName, Path filePath) throws OCRException {
        AppConfig config = AppConfig.getInstance();

        if (!config.getOcrConfig().isEnabled()) {
            throw new OCRException("OCR服务未启用");
        }

        String serviceUrl = config.getOcrConfig().getServiceUrl();
        if (serviceUrl == null || serviceUrl.trim().isEmpty()) {
            logger.debug("未配置OCR服务URL，使用默认OCR服务处理");
            InferenceEngine engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V3);
            OcrResult ocrResult = engine.runOcr(filePath.toString());
            return ocrResult.getStrRes().trim();
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        return callOCRService(serviceUrl, base64Image, fileName);
    }

    /**
     * 调用OCR服务API
     */
    private String callOCRService(String serviceUrl, String base64Image, String fileName) throws OCRException {
        AppConfig config = AppConfig.getInstance();
        int timeout = config.getOcrConfig().getTimeout();
        int maxRetries = config.getOcrConfig().getMaxRetries();

        OCRRequest request = new OCRRequest(base64Image, fileName);
        String requestBody = request.toJson();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("尝试调用OCR服务 (尝试 {}/{}): {}", attempt, maxRetries, serviceUrl);

                URL url = new URL(serviceUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // 设置请求属性
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "MasterIndex-OCR/1.0");
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);
                connection.setDoOutput(true);

                // 发送请求
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // 获取响应
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }

                        OCRResponse ocrResponse = OCRResponse.fromJson(response.toString());
                        if (ocrResponse.isSuccess()) {
                            logger.info("OCR识别成功: {}", fileName);
                            return ocrResponse.getText();
                        } else {
                            throw new OCRException("OCR服务返回错误: " + ocrResponse.getError());
                        }
                    }
                } else {
                    // 读取错误响应
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {

                        StringBuilder errorResponse = new StringBuilder();
                        String errorLine;
                        while ((errorLine = br.readLine()) != null) {
                            errorResponse.append(errorLine.trim());
                        }

                        logger.error("OCR服务HTTP错误: {} - {}", responseCode, errorResponse.toString());
                        throw new OCRException("OCR服务HTTP错误: " + responseCode);
                    }
                }

            } catch (Exception e) {
                logger.warn("OCR服务调用失败 (尝试 {}/{}): {}", attempt, maxRetries, e.getMessage());

                if (attempt == maxRetries) {
                    throw new OCRException("OCR服务调用失败，已达到最大重试次数: " + e.getMessage(), e);
                }

                // 等待一段时间后重试
                try {
                    Thread.sleep(1000 * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new OCRException("OCR服务调用被中断", ie);
                }
            }
        }

        throw new OCRException("OCR服务调用失败");
    }

    /**
     * OCR请求对象
     */
    public static class OCRRequest {
        private final String image;
        private final String filename;

        public OCRRequest(String image, String filename) {
            this.image = image;
            this.filename = filename;
        }

        public String toJson() {
            return String.format("{\"image\":\"%s\",\"filename\":\"%s\"}", image, filename);
        }
    }

    /**
     * OCR响应对象
     */
    public static class OCRResponse {
        private final boolean success;
        private final String text;
        private final String error;

        public OCRResponse(boolean success, String text, String error) {
            this.success = success;
            this.text = text;
            this.error = error;
        }

        public static OCRResponse fromJson(String json) {
            // 简单的JSON解析，实际项目中可以使用Jackson等库
            if (json.contains("\"success\":true")) {
                String text = extractField(json, "text");
                return new OCRResponse(true, text, null);
            } else {
                String error = extractField(json, "error");
                return new OCRResponse(false, null, error);
            }
        }

        private static String extractField(String json, String fieldName) {
            String pattern = "\"" + fieldName + "\":\"([^\"]*)\"";
            java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = r.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            return "";
        }

        public boolean isSuccess() {
            return success;
        }

        public String getText() {
            return text;
        }

        public String getError() {
            return error;
        }
    }

    /**
     * OCR异常类
     */
    public static class OCRException extends Exception {
        public OCRException(String message) {
            super(message);
        }

        public OCRException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}