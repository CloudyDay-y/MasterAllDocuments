package com.masterindex.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;

/**
 * 使用HanLP进行中文分词的Lucene分析器实现。
 * 该分析器结合了HanLP的分词功能和Lucene的过滤能力。
 */
public class HanLPAnalyzer extends Analyzer {

    /**
     * 创建一个新的HanLPAnalyzer实例。
     */
    public HanLPAnalyzer() {
        super();
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new HanLPTokenizer();
        TokenStream tokenStream = new LowerCaseFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, tokenStream);
    }
}
