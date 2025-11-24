package com.masterindex.parsers;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 用于解析不同文档类型的接口。
 * @author liwenchao
 */
public interface DocumentParser {

    /**
     * 检查此解析器是否可以处理给定文件。
     *
     * @param filePath 文件路径
     * @return 如果此解析器支持该文件类型则返回true
     */
    boolean supports(Path filePath);

    /**
     * 从文件中提取文本内容。
     *
     * @param filePath 文件路径
     * @return 提取的文本内容
     * @throws IOException 如果读取失败
     */
    String extractText(Path filePath) throws IOException;

    /**
     * 获取此解析器处理的文档类型。
     *
     * @return 文档类型（例如："text", "document", "image"）
     */
    String getType();
}
