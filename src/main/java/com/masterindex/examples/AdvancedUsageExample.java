package com.masterindex.examples;

import com.masterindex.config.AppConfig;
import com.masterindex.lucene.LuceneIndexBuilder;
import com.masterindex.lucene.LuceneSearcher;
import com.masterindex.model.SearchResult;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MasterIndex与Lucene的高级使用示例。
 * 演示文件类型过滤、短语搜索和增量更新。
 * @author liwenchao
 */
public class AdvancedUsageExample {

    public static void main(String[] args) {
        try {
            System.out.println("=== MasterIndex 高级使用示例 (Lucene) ===\n");

            String indexPath = "";
            String documentsPath = "";

            // 示例1：仅索引特定文件类型
            System.out.println("示例1：仅索引 .txt, .md 和 .java 文件");
            LuceneIndexBuilder builder = new LuceneIndexBuilder(indexPath);
            Set<String> allowedExtensions = new HashSet<>();
            allowedExtensions.add(".txt");
            allowedExtensions.add(".docx");

            int indexed = builder.addDirectory(documentsPath);
//            builder.addDocument("", true);
            builder.commit();
            builder.close();
//
            // 创建搜索器
            LuceneSearcher searcher = new LuceneSearcher(indexPath);

            // 示例2：短语搜索
            System.out.println("\n示例2：短语搜索");
            String phrase = "抖音";
            System.out.println("搜索短语: \"" + phrase + "\"");
            List<SearchResult> phraseResults = searcher.searchPhrase(phrase, 5);

            System.out.println("找到 " + phraseResults.size() + " 个结果:");
            for (SearchResult result : phraseResults) {
                System.out.println("  - " + result.getFilePath());
            }
//
//            // 示例3：按文件类型过滤
//            System.out.println("\n示例3：带文件类型过滤的搜索");
//            String query = "class";
//            System.out.println("搜索: " + query + " (仅在文本文件中)");
//            List<SearchResult> textResults = searcher.search(query, 5, "text");
//
//            System.out.println("在文本文件中找到 " + textResults.size() + " 个结果:");
//            for (SearchResult result : textResults) {
//                System.out.println("  - " + result.getFilePath() + " (得分: " + result.getScore() + ")");
//            }
//
//            // 示例4：按扩展名搜索
//            System.out.println("\n示例4：按扩展名搜索");
//            List<SearchResult> javaFiles = searcher.searchByExtension(".java", 10);
//
//            System.out.println("找到 " + javaFiles.size() + " 个Java文件:");
//            for (SearchResult result : javaFiles) {
//                System.out.println("  - " + result.getFilePath());
//            }
//
//            // 示例5：增量更新
//            System.out.println("\n示例5：增量更新（添加新文档）");
//            System.out.println("添加新文档的方法:");
//            System.out.println("  LuceneIndexBuilder builder = new LuceneIndexBuilder(indexPath);");
//            System.out.println("  builder.addDocument(\"./new_file.txt\", false);");
//            System.out.println("  builder.commit();");
//            System.out.println("  builder.close();");
//
//            // 示例6：强制重新索引
//            System.out.println("\n示例6：强制重新索引");
//            System.out.println("强制重新索引的方法: builder.addDirectory(path, true, true);");
//
//            // 显示统计信息
//            System.out.println("\n=== 索引统计信息 ===");
//            System.out.println(searcher.getStats());
//
//            // 关闭搜索器
//            searcher.close();

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
