package com.masterindex.model;

import java.util.List;

/**
 * 表示搜索结果，包含文件路径、得分和匹配的词汇。
 * @author liwenchao
 */
public class SearchResult implements Comparable<SearchResult> {
    private String filePath;
    private double score;
    private DocumentMetadata metadata;
    private List<String> matchedWords;

    public SearchResult() {
    }

    public SearchResult(String filePath, double score, DocumentMetadata metadata, List<String> matchedWords) {
        this.filePath = filePath;
        this.score = score;
        this.metadata = metadata;
        this.matchedWords = matchedWords;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public DocumentMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(DocumentMetadata metadata) {
        this.metadata = metadata;
    }

    public List<String> getMatchedWords() {
        return matchedWords;
    }

    public void setMatchedWords(List<String> matchedWords) {
        this.matchedWords = matchedWords;
    }

    @Override
    public int compareTo(SearchResult other) {
        // 按得分降序排序
        return Double.compare(other.score, this.score);
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "filePath='" + filePath + '\'' +
                ", score=" + score +
                ", matchedWords=" + matchedWords +
                '}';
    }
}
