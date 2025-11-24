package com.masterindex.lucene;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * 使用HanLP进行中文分词的Lucene分词器实现。
 * 该分词器将HanLP强大的中文NLP能力集成到Lucene的分析框架中。
 *
 * <p>内存高效的实现，通过分块处理文本以避免处理大文档时出现OutOfMemoryError。</p>
 */
public class HanLPTokenizer extends Tokenizer {
    private static final Logger logger = LoggerFactory.getLogger(HanLPTokenizer.class);

    // 最大块大小以防止内存问题
    private static final int MAX_CHUNK_SIZE = 100000; // 100KB块

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);

    private List<Term> currentTerms;
    private int termIndex;
    private int currentOffset;
    private StringBuilder chunkBuffer;
    private boolean isEndOfInput;

    /**
     * 创建一个新的HanLPTokenizer实例。
     */
    public HanLPTokenizer() {
        super();
        this.chunkBuffer = new StringBuilder(MAX_CHUNK_SIZE);
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        this.currentTerms = null;
        this.termIndex = 0;
        this.currentOffset = 0;
        this.chunkBuffer.setLength(0);
        this.isEndOfInput = false;
    }

    @Override
    public boolean incrementToken() throws IOException {
        clearAttributes();

        // 如果已处理完当前块的所有词项，读取下一块
        if (currentTerms == null || termIndex >= currentTerms.size()) {
            if (!readNextChunk()) {
                return false; // 没有更多可用块
            }
        }

        // 从当前块返回下一个词项
        if (termIndex < currentTerms.size()) {
            Term term = currentTerms.get(termIndex);
            String word = term.word;

            // 设置词项文本
            termAtt.setEmpty().append(word);

            // 设置偏移量
            offsetAtt.setOffset(currentOffset, currentOffset + word.length());
            currentOffset += word.length();

            // 设置类型（词性）
            typeAtt.setType(term.nature != null ? term.nature.toString() : "word");

            // 设置位置增量
            posIncrAtt.setPositionIncrement(1);

            termIndex++;
            return true;
        }

        return false;
    }

    /**
     * 读取下一块文本进行处理。
     * @return 如果读取到块则返回true，如果到达输入末尾则返回false
     * @throws IOException 如果读取失败
     */
    private boolean readNextChunk() throws IOException {
        chunkBuffer.setLength(0);
        termIndex = 0;

        char[] buffer = new char[8192];
        int totalRead = 0;

        while (totalRead < MAX_CHUNK_SIZE) {
            int length = input.read(buffer);
            if (length == -1) {
                isEndOfInput = true;
                break;
            }
            chunkBuffer.append(buffer, 0, length);
            totalRead += length;
        }

        String chunkText = chunkBuffer.toString();
        if (chunkText.isEmpty()) {
            return false;
        }

        try {
            currentTerms = HanLP.segment(chunkText);
            return !currentTerms.isEmpty();
        } catch (Exception e) {
            logger.error("HanLP分词失败，块大小 {}", chunkText.length(), e);
            return false;
        }
    }

    @Override
    public void end() throws IOException {
        super.end();
        // 设置最终偏移量
        offsetAtt.setOffset(currentOffset, currentOffset);
    }
}
