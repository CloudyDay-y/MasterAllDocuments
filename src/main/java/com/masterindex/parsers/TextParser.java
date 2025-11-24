package com.masterindex.parsers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 纯文本文件解析器。
 * 支持常见的基于文本的格式，如 .txt, .md, 源代码文件等。
 * @author liwenchao
 */
public class TextParser implements DocumentParser {
    private static final Logger logger = LoggerFactory.getLogger(TextParser.class);

    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".txt", ".md", ".py", ".java", ".js", ".cpp", ".c", ".h", ".go", ".rs",
            ".rb", ".php", ".html", ".css", ".json", ".xml", ".yaml", ".yml",
            ".toml", ".sh", ".bat", ".sql", ".log", ".csv", ".ini", ".conf"
    ));

    @Override
    public boolean supports(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    @Override
    public String extractText(Path filePath) throws IOException {
        try {
            return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("读取文本文件失败: {}", filePath, e);
            throw e;
        }
    }

    @Override
    public String getType() {
        return "text";
    }
}
