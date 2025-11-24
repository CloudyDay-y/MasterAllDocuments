package com.masterindex.cli;

import com.masterindex.lucene.LuceneIndexBuilder;
import com.masterindex.lucene.LuceneSearcher;
import com.masterindex.model.SearchResult;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

/**
 * 基于Lucene的MasterIndex命令行界面。
 *
 * 命令:
 * - build: 构建或更新索引
 * - search: 搜索索引
 * - stats: 显示索引统计信息
 */
@Command(
        name = "master-index",
        mixinStandardHelpOptions = true,
        version = "MasterIndex 2.0.0 (Lucene)",
        description = "基于Lucene的文档搜索引擎",
        subcommands = {
                MasterIndexCLI.BuildCommand.class,
                MasterIndexCLI.SearchCommand.class,
                MasterIndexCLI.StatsCommand.class
        }
)
public class MasterIndexCLI implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MasterIndexCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("MasterIndex - 文档搜索引擎");
        System.out.println("使用 --help 查看可用命令");
    }

    /**
     * 构建命令 - 索引文档。
     */
    @Command(name = "build", description = "从文档构建或更新索引")
    static class BuildCommand implements Runnable {

        @Parameters(index = "0", description = "要索引的源目录或文件")
        private String source;

        @Option(names = {"-i", "--index"}, description = "索引存储路径（默认：./master_index_data）")
        private String indexPath = "./master_index_data";

        @Option(names = {"-r", "--recursive"}, description = "递归索引子目录（默认：true）")
        private boolean recursive = true;

        @Option(names = {"-f", "--force"}, description = "强制重新索引，即使文件未更改")
        private boolean force = false;

        @Option(names = {"-e", "--extensions"}, description = "要索引的文件扩展名，逗号分隔（例如：.txt,.pdf,.docx）")
        private String extensions;

        @Override
        public void run() {
            try {
                System.out.println("=== 构建索引 (Lucene) ===");
                System.out.println("源路径: " + source);
                System.out.println("索引路径: " + indexPath);
                System.out.println("递归: " + recursive);
                System.out.println("强制重新索引: " + force);

                // 解析扩展名（如果提供）
                Set<String> allowedExtensions = null;
                if (extensions != null && !extensions.trim().isEmpty()) {
                    allowedExtensions = new HashSet<>();
                    for (String ext : extensions.split(",")) {
                        String trimmed = ext.trim();
                        if (!trimmed.startsWith(".")) {
                            trimmed = "." + trimmed;
                        }
                        allowedExtensions.add(trimmed.toLowerCase());
                    }
                    System.out.println("扩展名过滤: " + allowedExtensions);
                }

                // 使用Lucene引擎索引文档
                long startTime = System.currentTimeMillis();
                LuceneIndexBuilder builder = new LuceneIndexBuilder(indexPath);
                int count = builder.addDirectory(Paths.get(source), recursive, force, allowedExtensions);
                builder.save();
                Map<String, Object> stats = builder.getStats();
                builder.close();

                long duration = System.currentTimeMillis() - startTime;
                System.out.println("\n=== 索引完成 ===");
                System.out.println("已索引 " + count + " 个文档，耗时 " + duration + " 毫秒");
                System.out.println("文档总数: " + stats.get("total_documents"));
                System.out.println("唯一词项总数: " + stats.get("total_terms"));

            } catch (IOException e) {
                System.err.println("错误: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    /**
     * 搜索命令 - 搜索索引。
     */
    @Command(name = "search", description = "在索引中搜索文档")
    static class SearchCommand implements Runnable {

        @Parameters(index = "0", description = "搜索查询")
        private String query;

        @Option(names = {"-i", "--index"}, description = "索引存储路径（默认：./master_index_data）")
        private String indexPath = "./master_index_data";

        @Option(names = {"-k", "--top-k"}, description = "返回的最大结果数（默认：10）")
        private int topK = 10;

        @Option(names = {"-t", "--type"}, description = "按文件类型过滤（text, document）")
        private String fileType;

        @Option(names = {"-p", "--phrase"}, description = "搜索精确短语")
        private boolean phraseSearch = false;

        @Override
        public void run() {
            try {
                System.out.println("=== 搜索索引 (Lucene) ===");
                System.out.println("查询: " + query);
                System.out.println("索引路径: " + indexPath);

                // 使用Lucene引擎执行搜索
                long startTime = System.currentTimeMillis();
                LuceneSearcher searcher = new LuceneSearcher(indexPath);
                List<SearchResult> results;

                if (phraseSearch) {
                    results = searcher.searchPhrase(query, topK);
                } else {
                    results = searcher.search(query, topK, fileType);
                }
                searcher.close();

                long duration = System.currentTimeMillis() - startTime;

                // 显示结果
                System.out.println("\n=== 搜索结果 ===");
                System.out.println("找到 " + results.size() + " 个结果，耗时 " + duration + " 毫秒\n");

                for (int i = 0; i < results.size(); i++) {
                    SearchResult result = results.get(i);
                    System.out.println((i + 1) + ". " + result.getFilePath());
                    System.out.println("   得分: " + String.format("%.2f", result.getScore()));
                    if (result.getMetadata() != null) {
                        System.out.println("   类型: " + result.getMetadata().getType());
                        System.out.println("   扩展名: " + result.getMetadata().getExtension());
                    }
                    if (result.getMatchedWords() != null && !result.getMatchedWords().isEmpty()) {
                        System.out.println("   匹配词汇: " + result.getMatchedWords());
                    }
                    System.out.println();
                }

            } catch (IOException e) {
                System.err.println("错误: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    /**
     * 统计命令 - 显示索引统计信息。
     */
    @Command(name = "stats", description = "显示索引统计信息")
    static class StatsCommand implements Runnable {

        @Option(names = {"-i", "--index"}, description = "索引存储路径（默认：./master_index_data）")
        private String indexPath = "./master_index_data";

        @Override
        public void run() {
            try {
                // 使用Lucene引擎
                LuceneSearcher searcher = new LuceneSearcher(indexPath);
                Map<String, Object> stats = searcher.getStats();
                searcher.close();

                System.out.println("=== 索引统计信息 (Lucene) ===");
                System.out.println("索引路径: " + indexPath);
                System.out.println("文档总数: " + stats.get("total_documents"));
                System.out.println("唯一词项总数: " + stats.get("total_terms"));

            } catch (IOException e) {
                System.err.println("错误: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
    }
}
