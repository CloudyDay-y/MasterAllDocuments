package com.masterindex.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理Lucene索引存储并提供索引读写方法。
 * 该类处理索引管理的底层Lucene操作。
 * @author liwenchao
 */
public class LuceneIndexManager implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexManager.class);

    private final Path indexPath;
    private final Analyzer analyzer;
    private Directory directory;
    private IndexWriter indexWriter;
    private DirectoryReader indexReader;
    private IndexSearcher indexSearcher;

    /**
     * 创建一个新的LuceneIndexManager。
     *
     * @param indexPath 索引目录的路径
     * @param analyzer  用于索引和搜索的分析器
     * @throws IOException 如果初始化失败
     */
    public LuceneIndexManager(Path indexPath, Analyzer analyzer) throws IOException {
        this.indexPath = indexPath;
        this.analyzer = analyzer;
        this.directory = FSDirectory.open(indexPath);
    }

    /**
     * 打开索引进行写入。
     *
     * @param create 如果为true，创建新索引；如果为false，打开现有索引或创建新索引
     * @throws IOException 如果打开失败
     */
    public void openWriter(boolean create) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        if (create) {
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        } else {
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        }
        this.indexWriter = new IndexWriter(directory, config);
    }

    /**
     * 打开索引进行读取。
     *
     * @throws IOException 如果打开失败
     */
    public void openReader() throws IOException {
        if (DirectoryReader.indexExists(directory)) {
            this.indexReader = DirectoryReader.open(directory);
            this.indexSearcher = new IndexSearcher(indexReader);
        } else {
            throw new IOException("索引不存在: " + indexPath);
        }
    }

    /**
     * 重新打开索引读取器以获取最新更改。
     *
     * @throws IOException 如果重新打开失败
     */
    public void reopenReader() throws IOException {
        if (indexReader != null) {
            DirectoryReader newReader = DirectoryReader.openIfChanged(indexReader);
            if (newReader != null) {
                indexReader.close();
                indexReader = newReader;
                indexSearcher = new IndexSearcher(indexReader);
            }
        } else {
            openReader();
        }
    }

    /**
     * 向索引添加文档。
     *
     * @param doc 要添加的文档
     * @throws IOException 如果添加失败
     */
    public void addDocument(Document doc) throws IOException {
        if (indexWriter == null) {
            throw new IllegalStateException("IndexWriter未打开。请先调用openWriter()。");
        }
        indexWriter.addDocument(doc);
    }

    /**
     * 更新索引中的文档。
     *
     * @param term 用于标识要更新文档的词项
     * @param doc  新文档
     * @throws IOException 如果更新失败
     */
    public void updateDocument(Term term, Document doc) throws IOException {
        if (indexWriter == null) {
            throw new IllegalStateException("IndexWriter未打开。请先调用openWriter()。");
        }
        indexWriter.updateDocument(term, doc);
    }

    /**
     * 从索引中删除文档。
     *
     * @param term 用于标识要删除文档的词项
     * @throws IOException 如果删除失败
     */
    public void deleteDocuments(Term term) throws IOException {
        if (indexWriter == null) {
            throw new IllegalStateException("IndexWriter未打开。请先调用openWriter()。");
        }
        indexWriter.deleteDocuments(term);
    }

    /**
     * 提交所有待处理的更改到索引。
     *
     * @throws IOException 如果提交失败
     */
    public void commit() throws IOException {
        if (indexWriter != null) {
            indexWriter.commit();
        }
    }

    /**
     * 搜索索引。
     *
     * @param query 要执行的查询
     * @param topN  返回结果的最大数量
     * @return 搜索结果
     * @throws IOException 如果搜索失败
     */
    public TopDocs search(Query query, int topN) throws IOException {
        if (indexSearcher == null) {
            throw new IllegalStateException("IndexSearcher未打开。请先调用openReader()。");
        }
        return indexSearcher.search(query, topN);
    }

    /**
     * 通过内部Lucene文档ID获取文档。
     *
     * @param docId 文档ID
     * @return 文档
     * @throws IOException 如果检索失败
     */
    public Document getDocument(int docId) throws IOException {
        if (indexReader == null) {
            throw new IllegalStateException("IndexReader未打开。请先调用openReader()。");
        }
        return indexReader.document(docId);
    }

    /**
     * 通过路径字段搜索文档。
     *
     * @param path 要搜索的文件路径
     * @return 找到则返回文档，否则返回null
     * @throws IOException 如果搜索失败
     */
    public Document getDocumentByPath(String path) throws IOException {
        if (indexSearcher == null) {
            throw new IllegalStateException("IndexSearcher未打开。请先调用openReader()。");
        }

        Term pathTerm = new Term(LuceneIndexBuilder.FIELD_PATH, path);
        Query query = new org.apache.lucene.search.TermQuery(pathTerm);
        TopDocs topDocs = indexSearcher.search(query, 1);

        if (topDocs.totalHits.value > 0) {
            return indexReader.document(topDocs.scoreDocs[0].doc);
        }
        return null;
    }

    /**
     * 获取索引统计信息。
     *
     * @return 统计信息映射
     * @throws IOException 如果检索失败
     */
    public Map<String, Object> getStats() throws IOException {
        Map<String, Object> stats = new HashMap<>();

        if (indexReader != null) {
            stats.put("total_documents", indexReader.numDocs());
            stats.put("max_doc", indexReader.maxDoc());
            stats.put("num_deleted_docs", indexReader.numDeletedDocs());

            // 统计所有字段的唯一词项数
            long totalTerms = 0;
            for (LeafReaderContext context : indexReader.leaves()) {
                LeafReader reader = context.reader();
                FieldInfos fieldInfos = reader.getFieldInfos();
                for (FieldInfo fieldInfo : fieldInfos) {
                    Terms terms = reader.terms(fieldInfo.name);
                    if (terms != null) {
                        totalTerms += terms.size();
                    }
                }
            }
            stats.put("total_terms", totalTerms);
        } else {
            stats.put("total_documents", 0);
            stats.put("total_terms", 0);
        }

        return stats;
    }

    /**
     * 检查索引是否存在。
     *
     * @return 如果索引存在则返回true
     * @throws IOException 如果检查失败
     */
    public boolean exists() throws IOException {
        return DirectoryReader.indexExists(directory);
    }

    /**
     * 获取分析器。
     *
     * @return 分析器
     */
    public Analyzer getAnalyzer() {
        return analyzer;
    }

    /**
     * 获取索引搜索器。
     *
     * @return 索引搜索器
     */
    public IndexSearcher getIndexSearcher() {
        return indexSearcher;
    }

    /**
     * 获取索引读取器。
     *
     * @return 索引读取器
     */
    public IndexReader getIndexReader() {
        return indexReader;
    }

    @Override
    public void close() throws IOException {
        if (indexWriter != null) {
            indexWriter.close();
            indexWriter = null;
        }
        if (indexReader != null) {
            indexReader.close();
            indexReader = null;
        }
        if (directory != null) {
            directory.close();
            directory = null;
        }
    }
}
