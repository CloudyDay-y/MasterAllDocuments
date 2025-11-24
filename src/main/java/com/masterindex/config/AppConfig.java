package com.masterindex.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用配置类，包含OCR设置。
 * @author liwenchao
 */
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final String DEFAULT_CONFIG_FILE = "master_index_config.json";

    private static AppConfig instance;

    @JsonProperty("ocr")
    private OCRConfig ocrConfig;

    @JsonProperty("indexing")
    private IndexingConfig indexingConfig;

    public AppConfig() {
        this.ocrConfig = new OCRConfig();
        this.indexingConfig = new IndexingConfig();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final OCRConfig ocrConfig = new OCRConfig();
        private final IndexingConfig indexingConfig = new IndexingConfig();

        public Builder withOcr(java.util.function.Consumer<OCRConfig> consumer) {
            consumer.accept(ocrConfig);
            return this;
        }

        public Builder withIndexing(java.util.function.Consumer<IndexingConfig> consumer) {
            consumer.accept(indexingConfig);
            return this;
        }

        public AppConfig build() {
            AppConfig config = new AppConfig();
            config.ocrConfig = ocrConfig;
            config.indexingConfig = indexingConfig;
            return config;
        }
    }

    public static AppConfig getInstance() {
        if (instance != null) {
            return instance;
        }

        // If Spring is present, rely on Spring-managed bean injection instead of static instance
        try {
            Class<?> ctxClass = Class.forName("org.springframework.context.ApplicationContext");
            logger.info("Spring detected: AppConfig should be injected as a @Bean, not accessed via getInstance().");
        } catch (ClassNotFoundException e) {
            logger.info("Spring not detected: loading AppConfig from default JSON config.");
            instance = loadConfig();
        }

        return instance;
    }

    public static void init(AppConfig config) {
        logger.info("AppConfig initialized via explicit init(). (Spring mode or manual mode)");
        instance = config;
    }

    public static AppConfig loadConfig() {
        return loadConfig(DEFAULT_CONFIG_FILE);
    }

    // Deprecated: prefer using AppConfig.init(AppConfig.builder()...)
    public static AppConfig loadConfig(String configPath) {
        Path configFilePath = Paths.get(configPath);

        if (!Files.exists(configFilePath)) {
            logger.info("配置文件不存在，使用默认配置: {}", configPath);
            AppConfig defaultConfig = new AppConfig();
            defaultConfig.saveConfig(configPath);
            return defaultConfig;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            AppConfig config = mapper.readValue(configFilePath.toFile(), AppConfig.class);
            logger.info("成功加载配置文件: {}", configPath);
            return config;

        } catch (IOException e) {
            logger.error("加载配置文件失败: {}, 使用默认配置", configPath, e);
            return new AppConfig();
        }
    }

    public void saveConfig() {
        saveConfig(DEFAULT_CONFIG_FILE);
    }

    public void saveConfig(String configPath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            File configFile = new File(configPath);
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            mapper.writeValue(configFile, this);
            logger.info("配置文件已保存: {}", configPath);

        } catch (IOException e) {
            logger.error("保存配置文件失败: {}", configPath, e);
        }
    }

    public OCRConfig getOcrConfig() {
        return ocrConfig;
    }

    public void setOcrConfig(OCRConfig ocrConfig) {
        this.ocrConfig = ocrConfig;
    }

    public IndexingConfig getIndexingConfig() {
        return indexingConfig;
    }

    public void setIndexingConfig(IndexingConfig indexingConfig) {
        this.indexingConfig = indexingConfig;
    }

    /**
     * OCR配置类
     */
    public static class OCRConfig {
        private boolean enabled = true;
        private String serviceUrl = "";
        private int timeout = 30000; // 30秒
        private int maxRetries = 3;

        public OCRConfig() {
        }

        public boolean isEnabled() {
            return enabled;
        }

        public OCRConfig setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public String getServiceUrl() {
            return serviceUrl;
        }

        public OCRConfig setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
            return this;
        }

        public int getTimeout() {
            return timeout;
        }

        public OCRConfig setTimeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public OCRConfig setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
    }

    /**
     * 索引配置类
     */
    public static class IndexingConfig {
        private boolean enableOcr = true;

        @JsonProperty("supported_image_extensions")
        private String[] supportedImageExtensions = {
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".webp"
        };

        @JsonProperty("supported_document_extensions")
        private String[] supportedDocumentExtensions = {
            ".docx", ".doc", ".pdf", ".pptx", ".ppt"
        };

        @JsonProperty("max_file_size_mb")
        private int maxFileSizeMb = 50;

        @JsonProperty("pdf_rendering_dpi")
        private int pdfRenderingDpi = 300;

        public IndexingConfig() {
        }

        public boolean isEnableOcr() {
            return enableOcr;
        }

        public void setEnableOcr(boolean enableOcr) {
            this.enableOcr = enableOcr;
        }

        public String[] getSupportedImageExtensions() {
            return supportedImageExtensions;
        }

        public void setSupportedImageExtensions(String[] supportedImageExtensions) {
            this.supportedImageExtensions = supportedImageExtensions;
        }

        public String[] getSupportedDocumentExtensions() {
            return supportedDocumentExtensions;
        }

        public void setSupportedDocumentExtensions(String[] supportedDocumentExtensions) {
            this.supportedDocumentExtensions = supportedDocumentExtensions;
        }

        public int getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        public void setMaxFileSizeMb(int maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }

        public int getPdfRenderingDpi() {
            return pdfRenderingDpi;
        }

        public void setPdfRenderingDpi(int pdfRenderingDpi) {
            this.pdfRenderingDpi = pdfRenderingDpi;
        }
    }
}