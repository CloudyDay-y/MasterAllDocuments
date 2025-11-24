package com.masterindex.parsers;

import com.masterindex.config.AppConfig;
import com.masterindex.ocr.OCRServiceClient;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Microsoft PowerPoint文档解析器，支持OCR处理内嵌图片。
 *
 * @author liwenchao
 */
public class PowerPointParser implements DocumentParser {
    private static final Logger logger = LoggerFactory.getLogger(PowerPointParser.class);

    private boolean enableOcr;
    private final OCRServiceClient ocrClient;

    public PowerPointParser() {
        AppConfig config = AppConfig.getInstance();
        this.enableOcr = config.getIndexingConfig().isEnableOcr();
        this.ocrClient = new OCRServiceClient();
    }

    @Override
    public boolean supports(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".pptx") || fileName.endsWith(".ppt");
    }

    @Override
    public String extractText(Path filePath) throws IOException {
        StringBuilder content = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             XMLSlideShow ppt = new XMLSlideShow(fis)) {

            // 提取文本内容
            for (XSLFSlide slide : ppt.getSlides()) {
                // 提取幻灯片标题
                String slideTitle = slide.getTitle();
                if (slideTitle != null && !slideTitle.trim().isEmpty()) {
                    content.append("=== ").append(slideTitle).append(" ===\n");
                }

                // 提取幻灯片中的文本
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        String shapeText = textShape.getText();
                        if (shapeText != null && !shapeText.trim().isEmpty()) {
                            content.append(shapeText).append("\n");
                        }
                    }
                }
                content.append("\n");
            }

            // 如果启用OCR，提取并处理内嵌图片
            if (enableOcr) {
                String ocrText = extractImagesWithOcr(ppt, filePath);
                if (!ocrText.trim().isEmpty()) {
                    content.append(ocrText);
                }
            }

        } catch (Exception e) {
            logger.error("从PowerPoint提取文本失败: {}", filePath, e);
            throw new IOException("parsing powerpoint document failed", e);
        }

        // 格式化文本：去除多余换行和空格
        return formatExtractedText(content.toString());
    }

    /**
     * 从PowerPoint文档中提取图片并进行OCR处理
     */
    private String extractImagesWithOcr(XMLSlideShow ppt, Path filePath) {
        StringBuilder ocrResults = new StringBuilder();
        List<byte[]> images = extractImagesFromPresentation(ppt);

        if (images.isEmpty()) {
            logger.debug("PowerPoint文档中没有发现内嵌图片: {}", filePath);
            return "";
        }

        logger.info("发现 {} 个内嵌图片，开始OCR处理: {}", images.size(), filePath);

        // 创建临时目录存放提取的图片
        Path tempDir = null;
        try {
            tempDir = java.nio.file.Files.createTempDirectory("ppt_images_");

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
                    logger.error("处理PowerPoint内嵌图片时出错 {}: {}", filePath, e.getMessage(), e);
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
     * 从PowerPoint演示文稿中提取所有图片
     */
    private List<byte[]> extractImagesFromPresentation(XMLSlideShow ppt) {
        List<byte[]> images = new ArrayList<>();

        try {
            int slideNumber = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                slideNumber++;

                // 提取幻灯片背景图片
                extractBackgroundImage(slide, images, slideNumber);

                // 提取幻灯片中的图片
                for (XSLFShape shape : slide.getShapes()) {
                    extractImageFromShape(shape, images, slideNumber);
                }

                // 提取幻灯片母版和版式中的图片
                extractImagesFromSlideLayout(slide, images, slideNumber);
            }

            // 提取演示文稿母版中的图片
            extractImagesFromSlideMaster(ppt, images);

        } catch (Exception e) {
            logger.error("从PowerPoint提取图片时出错", e);
        }

        return images;
    }

    /**
     * 提取幻灯片背景图片
     */
    private void extractBackgroundImage(XSLFSlide slide, List<byte[]> images, int slideNumber) {
        try {
            XSLFBackground background = slide.getBackground();
            if (background != null) {
                // PowerPoint背景可能通过填充方式设置，不直接提供图片数据
                // 这里尝试获取背景的填充属性
                logger.debug("幻灯片 {} 有背景，但无法直接提取图片数据", slideNumber);
            }
        } catch (Exception e) {
            logger.debug("提取背景图片失败", e);
        }
    }

    /**
     * 从形状中提取图片
     */
    private void extractImageFromShape(XSLFShape shape, List<byte[]> images, int slideNumber) {
        try {
            if (shape instanceof XSLFPictureShape) {
                XSLFPictureShape pictureShape = (XSLFPictureShape) shape;
                XSLFPictureData pictureData = pictureShape.getPictureData();
                if (pictureData != null) {
                    byte[] imageBytes = pictureData.getData();
                    if (imageBytes != null && imageBytes.length > 0) {
                        images.add(imageBytes);
                        logger.debug("提取幻灯片 {} 图片形状，大小: {} bytes",
                                slideNumber, imageBytes.length);
                    }
                }
            } else if (shape instanceof XSLFGroupShape) {
                // 递归处理组形状中的图片
                XSLFGroupShape groupShape = (XSLFGroupShape) shape;
                for (XSLFShape childShape : groupShape.getShapes()) {
                    extractImageFromShape(childShape, images, slideNumber);
                }
            }
        } catch (Exception e) {
            logger.debug("从形状提取图片失败", e);
        }
    }

    /**
     * 从幻灯片版式中提取图片
     */
    private void extractImagesFromSlideLayout(XSLFSlide slide, List<byte[]> images, int slideNumber) {
        try {
            XSLFSlideLayout layout = slide.getSlideLayout();
            if (layout != null) {
                for (XSLFShape shape : layout.getShapes()) {
                    extractImageFromShape(shape, images, slideNumber);
                }
            }
        } catch (Exception e) {
            logger.debug("从幻灯片版式提取图片失败", e);
        }
    }

    /**
     * 从幻灯片母版中提取图片
     */
    private void extractImagesFromSlideMaster(XMLSlideShow ppt, List<byte[]> images) {
        try {
            // 获取所有幻灯片母版
            for (XSLFSlideMaster master : ppt.getSlideMasters()) {
                if (master != null) {
                    for (XSLFShape shape : master.getShapes()) {
                        extractImageFromShape(shape, images, 0);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("从幻灯片母版提取图片失败", e);
        }
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