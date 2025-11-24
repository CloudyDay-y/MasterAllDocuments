package com.masterindex.lucene;

import com.masterindex.model.SearchResult;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 基于Lucene的搜索器，提供各种搜索功能。
 * 支持关键词搜索、短语搜索以及按文件类型/扩展名过滤。
 * @author liwenchao
 */
public class LuceneSearcher implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(LuceneSearcher.class);

    private final LuceneIndexManager indexManager;

    /**
     * 创建一个新的LuceneSearcher，使用默认的HanLP分析器。
     *
     * @param indexPath 索引目录的路径
     * @throws IOException 如果初始化失败
     */
    public LuceneSearcher(String indexPath) throws IOException {
        this(Paths.get(indexPath));
    }

    /**
     * 创建一个新的LuceneSearcher，使用自定义分析器。
     *
     * @param indexPath 索引目录的路径
     * @param analyzer 自定义分析器
     * @throws IOException 如果初始化失败
     */
    public LuceneSearcher(String indexPath, Analyzer analyzer) throws IOException {
        this(Paths.get(indexPath), analyzer);
    }

    /**
     * 创建一个新的LuceneSearcher，使用默认的HanLP分析器。
     *
     * @param indexPath 索引目录的路径
     * @throws IOException 如果初始化失败
     */
    public LuceneSearcher(Path indexPath) throws IOException {
        this(indexPath, new HanLPAnalyzer());
    }

    /**
     * 创建一个新的LuceneSearcher，使用自定义分析器。
     *
     * @param indexPath 索引目录的路径
     * @param analyzer 自定义分析器，用于查询解析和搜索
     * @throws IOException 如果初始化失败
     */
    public LuceneSearcher(Path indexPath, Analyzer analyzer) throws IOException {
        this.indexManager = new LuceneIndexManager(indexPath, analyzer);

        if (!indexManager.exists()) {
            throw new IOException("索引不存在: " + indexPath);
        }

        indexManager.openReader();
        logger.info("LuceneSearcher已初始化: {} 使用分析器: {}",
                    indexPath, analyzer.getClass().getSimpleName());
    }

    /**
     * 搜索匹配查询的文档。
     *
     * @param query 搜索查询
     * @param topK 返回结果的最大数量
     * @return 搜索结果列表
     * @throws IOException 如果搜索失败
     */
    public List<SearchResult> search(String query, int topK) throws IOException {
        return search(query, topK, null);
    }

    /**
     * 搜索匹配查询的文档，按文件类型过滤。
     *
     * @param query 搜索查询
     * @param topK 返回结果的最大数量
     * @param fileType 文件类型过滤器（"text", "document"或null表示所有类型）
     * @return 搜索结果列表
     * @throws IOException 如果搜索失败
     */
    public List<SearchResult> search(String query, int topK, String fileType) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // Parse query using MultiFieldQueryParser
            // 使用MultiFieldQueryParser解析查询
            MultiFieldQueryParser parser = new MultiFieldQueryParser(
                    new String[]{LuceneIndexBuilder.FIELD_CONTENT, LuceneIndexBuilder.FIELD_PATH},
                    indexManager.getAnalyzer()
            );
            parser.setDefaultOperator(QueryParser.Operator.OR);

            Query luceneQuery = parser.parse(QueryParser.escape(query));

            // Add file type filter if specified
            // 如果指定了文件类型，则添加文件类型过滤器
            if (fileType != null && !fileType.trim().isEmpty()) {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                builder.add(luceneQuery, BooleanClause.Occur.MUST);
                builder.add(new TermQuery(new Term(LuceneIndexBuilder.FIELD_FILE_TYPE, fileType)),
                           BooleanClause.Occur.MUST);
                luceneQuery = builder.build();
            }

            // Execute search
            // 执行搜索
            TopDocs topDocs = indexManager.search(luceneQuery, topK);

            // Convert to SearchResult objects
            // 转换为SearchResult对象
            List<SearchResult> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = indexManager.getDocument(scoreDoc.doc);
                String path = doc.get(LuceneIndexBuilder.FIELD_PATH);
                double score = scoreDoc.score;

                SearchResult result = new SearchResult(path, score, null, Arrays.asList(query));
                results.add(result);
            }

            logger.info("Search for '{}' returned {} results", query, results.size());
            return results;

        } catch (ParseException e) {
            logger.error("Failed to parse query: {}", query, e);
            throw new IOException("Invalid query: " + query, e);
        }
    }

    /**
     * 搜索包含精确短语的文档。
     *
     * @param phrase 要搜索的精确短语
     * @param topK 返回结果的最大数量
     * @return 搜索结果列表
     * @throws IOException 如果搜索失败
     */
    public List<SearchResult> searchPhrase(String phrase, int topK) throws IOException {
        if (phrase == null || phrase.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 使用查询解析器正确地分词短语
            QueryParser parser = new QueryParser(LuceneIndexBuilder.FIELD_CONTENT, indexManager.getAnalyzer());
            Query parsedQuery = parser.parse("\"" + QueryParser.escape(phrase) + "\"");

            // 执行搜索
            TopDocs topDocs = indexManager.search(parsedQuery, topK);

            // 转换为SearchResult对象
            List<SearchResult> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = indexManager.getDocument(scoreDoc.doc);
                String path = doc.get(LuceneIndexBuilder.FIELD_PATH);
                double score = scoreDoc.score;

                SearchResult result = new SearchResult(path, score, null, Collections.singletonList(phrase));
                results.add(result);
            }

            logger.info("Phrase search for '{}' returned {} results", phrase, results.size());
            return results;

        } catch (ParseException e) {
            logger.error("Failed to parse phrase: {}", phrase, e);
            throw new IOException("Invalid phrase: " + phrase, e);
        }
    }

    /**
     * 按文件扩展名搜索文档。
     *
     * @param extension 文件扩展名（例如".txt", ".pdf"）
     * @param topK 返回结果的最大数量
     * @return 搜索结果列表
     * @throws IOException 如果搜索失败
     */
    public List<SearchResult> searchByExtension(String extension, int topK) throws IOException {
        if (extension == null || extension.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Ensure extension starts with a dot
        // 确保扩展名以点号开头
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }

        // Create term query for extension
        // 为扩展名创建词项查询
        Query query = new TermQuery(new Term(LuceneIndexBuilder.FIELD_EXTENSION, extension));

        // Execute search
        // 执行搜索
        TopDocs topDocs = indexManager.search(query, topK);

        // Convert to SearchResult objects
        // 转换为SearchResult对象
        List<SearchResult> results = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = indexManager.getDocument(scoreDoc.doc);
            String path = doc.get(LuceneIndexBuilder.FIELD_PATH);
            double score = scoreDoc.score;

            SearchResult result = new SearchResult(path, score, null, Arrays.asList(extension));
            results.add(result);
        }

        logger.info("Search by extension '{}' returned {} results", extension, results.size());
        return results;
    }

    /**
     * 使用自定义Lucene查询进行搜索。
     *
     * @param query Lucene查询对象
     * @param topK 返回结果的最大数量
     * @return 搜索结果列表
     * @throws IOException 如果搜索失败
     */
    public List<SearchResult> searchWithQuery(Query query, int topK) throws IOException {
        TopDocs topDocs = indexManager.search(query, topK);

        List<SearchResult> results = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = indexManager.getDocument(scoreDoc.doc);
            String path = doc.get(LuceneIndexBuilder.FIELD_PATH);
            double score = scoreDoc.score;

            SearchResult result = new SearchResult(path, score, null, new ArrayList<>());
            results.add(result);
        }

        return results;
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
     * 重新打开索引读取器以获取最新更改。
     *
     * @throws IOException 如果重新打开失败
     */
    public void refresh() throws IOException {
        indexManager.reopenReader();
    }

    /**
     * 关闭搜索器并释放资源。
     *
     * @throws IOException 如果关闭失败
     */
    public void close() throws IOException {
        indexManager.close();
    }
}
