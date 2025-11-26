package com.masterindex.lucene;

import com.masterindex.parsers.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/**
 * 支持多种文档格式的基于Lucene的索引构建器。
 * 该类处理文档解析、索引和增量更新。
 *
 * <p>该类支持自定义分析器注入。用户可以传递任何
 * org.apache.lucene.analysis.Analyzer的实现来自定义文本处理行为。</p>
 *
 * <p>使用自定义分析器的示例：</p>
 * <pre>
 * Analyzer customAnalyzer = new MyCustomAnalyzer();
 * try (LuceneIndexBuilder builder = new LuceneIndexBuilder(indexPath, customAnalyzer)) {
 *     builder.addDocument("path/to/document.txt", false);
 *     builder.commit();
 * }
 * </pre>
 *
 * @author liwenchao
 */
public class LuceneIndexBuilder implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexBuilder.class);

    /**
     * Lucene字段名称
     */
    public static final String FIELD_PATH = "path";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_FILE_TYPE = "file_type";
    public static final String FIELD_EXTENSION = "extension";
    public static final String FIELD_SIZE = "size";
    public static final String FIELD_MODIFIED_TIME = "modified_time";
    public static final String FIELD_HASH = "hash";

    private final LuceneIndexManager indexManager;
    private final List<DocumentParser> parsers;

    /**
     * 使用默认HanLP分析器创建一个新的LuceneIndexBuilder。
     *
     * @param indexPath 索引目录的路径
     * @throws IOException 如果初始化失败
     */
    public LuceneIndexBuilder(String indexPath) throws IOException {
        this(Paths.get(indexPath));
    }

    /**
     * 使用自定义分析器创建一个新的LuceneIndexBuilder。
     *
     * @param indexPath 索引目录的路径
     * @param analyzer 用于文本处理的自定义分析器
     * @throws IOException 如果初始化失败
     */
    public LuceneIndexBuilder(String indexPath, Analyzer analyzer) throws IOException {
        this(Paths.get(indexPath), analyzer);
    }

    /**
     * 使用默认HanLP分析器创建一个新的LuceneIndexBuilder。
     *
     * @param indexPath 索引目录的路径
     * @throws IOException 如果初始化失败
     */
    public LuceneIndexBuilder(Path indexPath) throws IOException {
        this(indexPath, new HanLPAnalyzer());
    }

    /**
     * 使用自定义分析器创建一个新的LuceneIndexBuilder。
     *
     * @param indexPath 索引目录的路径
     * @param analyzer 用于文本处理的自定义分析器
     * @throws IOException 如果初始化失败
     */
    public LuceneIndexBuilder(Path indexPath, Analyzer analyzer) throws IOException {
        // 如果索引目录不存在则创建
        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath);
        }

        this.indexManager = new LuceneIndexManager(indexPath, analyzer);
        this.parsers = initializeParsers();

        // 打开索引进行写入
        indexManager.openWriter(false);
        logger.info("LuceneIndexBuilder已初始化: {} 使用分析器: {}",
                    indexPath, analyzer.getClass().getSimpleName());
    }

    /**
     * 初始化文档解析器。
     */
    private List<DocumentParser> initializeParsers() {
        return getDocumentParsers();
    }

    public static List<DocumentParser> getDocumentParsers() {
        List<DocumentParser> parserList = new ArrayList<>();
        parserList.add(new TextParser());
        parserList.add(new WordParser());
        parserList.add(new PdfParser());
        parserList.add(new ExcelParser());
        parserList.add(new PowerPointParser());
        parserList.add(new ImageParser());
        return parserList;
    }

    /**
     * 向索引添加文档。
     *
     * @param filePath 文档的路径
     * @return 如果文档被索引返回true，如果跳过返回false
     * @throws IOException 如果索引失败
     */
    public boolean addDocument(String filePath) throws IOException {
        return addDocument(Paths.get(filePath), false);
    }

    /**
     * 向索引添加文档。
     *
     * @param filePath 文档的路径
     * @param force 强制重新索引，即使文件未更改
     * @return 如果文档被索引返回true，如果跳过返回false
     * @throws IOException 如果索引失败
     */
    public boolean addDocument(String filePath, boolean force) throws IOException {
        return addDocument(Paths.get(filePath), force);
    }

    /**
     * 向索引添加文档。
     *
     * @param filePath 文档的路径
     * @param force 强制重新索引，即使文件未更改
     * @return 如果文档被索引返回true，如果跳过返回false
     * @throws IOException 如果索引失败
     */
    public boolean addDocument(Path filePath, boolean force) throws IOException {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            logger.warn("文件不存在或不是普通文件: {}", filePath);
            return false;
        }

        String path = filePath.toAbsolutePath().toString();

        // 检查是否应跳过此文件（增量索引）
        if (!force && !shouldReindex(filePath, path)) {
            logger.debug("跳过未更改的文件: {}", path);
            return false;
        }

        // 查找合适的解析器
        DocumentParser parser = findParser(filePath);
        if (parser == null) {
            logger.warn("未找到文件的解析器: {}", path);
            return false;
        }

        // 解析文档
        String content;
        try {
            content = parser.extractText(filePath);
        } catch (Exception e) {
            logger.error("解析文件失败: {}", path, e);
            return false;
        }

        if (content == null || content.trim().isEmpty()) {
            logger.warn("文件内容为空: {}", path);
            return false;
        }

        // 创建Lucene文档
        Document doc = createDocument(filePath, content);

        // 更新或添加文档
        Term pathTerm = new Term(FIELD_PATH, path);
        indexManager.updateDocument(pathTerm, doc);

        logger.info("已索引文档: {}", path);
        return true;
    }

    /**
     * 使用默认设置将目录中的所有文档添加到索引。
     * 使用递归索引，不强制重新索引，所有文件类型。
     *
     * @param dirPath 目录路径
     * @return 已索引的文档数量
     * @throws IOException 如果索引失败
     */
    public int addDirectory(String dirPath) throws IOException {
        return addDirectory(Paths.get(dirPath), true, false, null);
    }

    public int addDirectory(String dirPath, Set<String> allowedExtensions) throws IOException {
        return addDirectory(Paths.get(dirPath), true, false, allowedExtensions);
    }

    /**
     * 使用默认设置将目录中的所有文档添加到索引。
     * 不强制重新索引，所有文件类型。
     *
     * @param dirPath 目录路径
     * @param recursive 如果为true，递归索引子目录
     * @return 已索引的文档数量
     * @throws IOException 如果索引失败
     */
    public int addDirectory(String dirPath, boolean recursive) throws IOException {
        return addDirectory(Paths.get(dirPath), recursive, false, null);
    }

    /**
     * 使用默认设置将目录中的所有文档添加到索引。
     * 所有文件类型。
     *
     * @param dirPath 目录路径
     * @param recursive 如果为true，递归索引子目录
     * @param force 强制重新索引
     * @return 已索引的文档数量
     * @throws IOException 如果索引失败
     */
    public int addDirectory(String dirPath, boolean recursive, boolean force) throws IOException {
        return addDirectory(Paths.get(dirPath), recursive, force, null);
    }

    /**
     * 使用默认设置将目录中的所有文档添加到索引。
     * 使用递归索引，不强制重新索引，所有文件类型。
     *
     * @param dirPath 目录路径
     * @return 已索引的文档数量
     * @throws IOException 如果索引失败
     */
    public int addDirectory(Path dirPath) throws IOException {
        return addDirectory(dirPath, true, false, null);
    }

    /**
     * 使用默认设置将目录中的所有文档添加到索引。
     * 不强制重新索引，所有文件类型。
     *
     * @param dirPath 目录路径
     * @param recursive 如果为true，递归索引子目录
     * @return 已索引的文档数量
     * @throws IOException 如果索引失败
     */
    public int addDirectory(Path dirPath, boolean recursive) throws IOException {
        return addDirectory(dirPath, recursive, false, null);
    }

    /**
     * 使用默认设置将目录中的所有文档添加到索引。
     * 所有文件类型。
     *
     * @param dirPath 目录路径
     * @param recursive 如果为true，递归索引子目录
     * @param force 强制重新索引
     * @return 已索引的文档数量
     * @throws IOException 如果索引失败
     */
    public int addDirectory(Path dirPath, boolean recursive, boolean force) throws IOException {
        return addDirectory(dirPath, recursive, force, null);
    }

    /**
     * 将目录中的所有文档添加到索引。
     *
     * @param dirPath 目录路径
     * @param recursive 如果为true，递归索引子目录
     * @param force 强制重新索引
     * @param allowedExtensions 允许的文件扩展名集合（例如".txt", ".pdf"），null表示所有
     * @return 已索引的文档数量
     * @throws IOException 如果索引失败
     */
    public int addDirectory(Path dirPath, boolean recursive, boolean force, Set<String> allowedExtensions) throws IOException {
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            logger.warn("目录不存在: {}", dirPath);
            return 0;
        }

        final int[] count = {0};

        FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    if (allowedExtensions != null) {
                        String fileName = file.getFileName().toString();
                        boolean hasAllowedExtension = allowedExtensions.stream()
                                .anyMatch(ext -> fileName.toLowerCase().endsWith(ext.toLowerCase()));
                        if (!hasAllowedExtension) {
                            return FileVisitResult.CONTINUE;
                        }
                    }

                    if (addDocument(file, force)) {
                        count[0]++;
                    }
                } catch (IOException e) {
                    logger.error("索引文件出错: {}", file, e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!recursive && !dir.equals(dirPath)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(dirPath, visitor);
        logger.info("从目录 {} 索引了 {} 个文档", dirPath, count[0]);

        return count[0];
    }

    /**
     * 从索引中删除文档。
     *
     * @param filePath 文档的路径
     * @throws IOException 如果删除失败
     */
    public void removeDocument(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        Term pathTerm = new Term(FIELD_PATH, path.toAbsolutePath().toString());
        indexManager.deleteDocuments(pathTerm);
        logger.info("已删除文档: {}", filePath);
    }

    /**
     * 提交所有待处理的更改到索引。
     *
     * @throws IOException 如果提交失败
     */
    public void commit() throws IOException {
        indexManager.commit();
        logger.info("索引已提交");
    }

    /**
     * 保存索引（提交更改）。
     *
     * @throws IOException 如果保存失败
     */
    public void save() throws IOException {
        commit();
        close();
    }

    /**
     * 获取索引统计信息。
     *
     * @return 统计信息映射
     * @throws IOException 如果检索失败
     */
    public Map<String, Object> getStats() throws IOException {
        return indexManager.getStats();
    }

    /**
     * 关闭索引构建器并释放资源。
     *
     * @throws IOException 如果关闭失败
     */
    @Override
    public void close() throws IOException {
        indexManager.close();
    }

    /**
     * 从文件路径和内容创建Lucene文档。
     */
    private Document createDocument(Path filePath, String content) throws IOException {
        Document doc = new Document();

        String path = filePath.toAbsolutePath().toString();
        String fileName = filePath.getFileName().toString();
        String extension = getFileExtension(fileName);
        String fileType = getFileType(extension);

        // 添加字段
        doc.add(new StringField(FIELD_PATH, path, Field.Store.YES));
        doc.add(new TextField(FIELD_CONTENT, content, Field.Store.NO));
        doc.add(new StringField(FIELD_FILE_TYPE, fileType, Field.Store.YES));
        doc.add(new StringField(FIELD_EXTENSION, extension, Field.Store.YES));

        // 添加元数据
        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        doc.add(new LongPoint(FIELD_SIZE, attrs.size()));
        doc.add(new StoredField(FIELD_SIZE, attrs.size()));
        doc.add(new LongPoint(FIELD_MODIFIED_TIME, attrs.lastModifiedTime().toMillis()));
        doc.add(new StoredField(FIELD_MODIFIED_TIME, attrs.lastModifiedTime().toMillis()));

        // 添加文件哈希
        String hash = calculateFileHash(filePath);
        doc.add(new StringField(FIELD_HASH, hash, Field.Store.YES));

        return doc;
    }

    /**
     * 根据元数据检查文件是否应重新索引。
     * 比较文件修改时间、大小和哈希值与现有索引文档。
     *
     * @param filePath 文件的路径
     * @param path 文件路径的字符串表示
     * @return 如果文件应重新索引返回true，如果未更改返回false
     * @throws IOException 如果检查失败
     */
    private boolean shouldReindex(Path filePath, String path) throws IOException {
        try {
            // 检查索引是否存在
            if (!indexManager.exists()) {
                // 索引不存在，应进行索引
                logger.debug("索引不存在，将索引文件: {}", path);
                return true;
            }

            // 尝试打开读取器以搜索现有文档
            indexManager.openReader();

            // 搜索具有相同路径的现有文档
            Document existingDoc = indexManager.getDocumentByPath(path);
            if (existingDoc == null) {
                // 文件不在索引中，应进行索引
                return true;
            }

            // 获取当前文件元数据
            BasicFileAttributes currentAttrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            long currentModifiedTime = currentAttrs.lastModifiedTime().toMillis();
            long currentSize = currentAttrs.size();
            String currentHash = calculateFileHash(filePath);

            // 从现有文档获取存储的元数据
            String storedModifiedTimeStr = existingDoc.get(FIELD_MODIFIED_TIME);
            String storedSizeStr = existingDoc.get(FIELD_SIZE);
            String storedHash = existingDoc.get(FIELD_HASH);

            if (storedModifiedTimeStr == null || storedSizeStr == null || storedHash == null) {
                // 缺少元数据，应重新索引
                return true;
            }

            try {
                long storedModifiedTime = Long.parseLong(storedModifiedTimeStr);
                long storedSize = Long.parseLong(storedSizeStr);

                // 比较元数据
                boolean modifiedTimeChanged = currentModifiedTime != storedModifiedTime;
                boolean sizeChanged = currentSize != storedSize;
                boolean hashChanged = !currentHash.equals(storedHash);

                // 如果任何元数据不同则重新索引
                return modifiedTimeChanged || sizeChanged || hashChanged;

            } catch (NumberFormatException e) {
                logger.warn("解析文件存储的元数据失败: {}", path, e);
                return true;
            }

        } catch (IllegalStateException e) {
            // 索引读取器不可用
            logger.debug("索引读取器不可用: {}，将进行索引", path);
            return true;
        } catch (IOException e) {
            // IO异常，可能是索引不存在或损坏
            logger.debug("访问索引时出现IO错误: {}，将进行索引", path);
            return true;
        } catch (Exception e) {
            logger.error("检查文件重新索引状态时出错: {}", path, e);
            return true;
        }
    }

    /**
     * 为文件查找合适的解析器。
     */
    private DocumentParser findParser(Path filePath) {
        return parsers.stream()
                .filter(parser -> parser.supports(filePath))
                .findFirst()
                .orElse(null);
    }

    /**
     * 从文件名获取文件扩展名。
     */
    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex) : "";
    }

    /**
     * 根据扩展名确定文件类型。
     */
    private String getFileType(String extension) {
        switch (extension.toLowerCase()) {
            case ".docx":
            case ".pdf":
            case ".xlsx":
            case ".pptx":
                return "document";
            case ".jpg":
            case ".jpeg":
            case ".png":
            case ".gif":
            case ".bmp":
            case ".tiff":
            case ".webp":
                return "image";
            default:
                return "text";
        }
    }

    /**
     * 计算文件内容的SHA-256哈希值。
     */
    private String calculateFileHash(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = digest.digest(fileBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            logger.warn("计算 {} 的哈希值失败: {}", filePath, e.getMessage());
            return "";
        }
    }
}
