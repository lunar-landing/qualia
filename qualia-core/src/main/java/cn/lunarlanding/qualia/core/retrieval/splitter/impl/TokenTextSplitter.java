package cn.lunarlanding.qualia.core.retrieval.splitter.impl;

import cn.lunarlanding.qualia.core.retrieval.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Token 文本分割器
 * 按照大模型 Token 数量进行分割。由于不同的模型使用不同的 Tokenizer，
 * 此实现支持传入自定义的 Token 计数逻辑。
 */
public class TokenTextSplitter implements TextSplitter {
    private final int chunkTokenSize;
    private final int chunkTokenOverlap;
    private final Function<String, Integer> tokenCounter;

    /**
     * 默认构造函数：使用简单的启发式算法估算 Token (1 Token ≈ 1.5 字符)
     */
    public TokenTextSplitter(int chunkTokenSize, int chunkTokenOverlap) {
        this(chunkTokenSize, chunkTokenOverlap, text -> (int) (text.length() / 1.5));
    }

    /**
     * 支持自定义 Token 计数器的构造函数
     * @param tokenCounter 一个函数，输入文本返回 Token 数量
     */
    public TokenTextSplitter(int chunkTokenSize, int chunkTokenOverlap, Function<String, Integer> tokenCounter) {
        this.chunkTokenSize = chunkTokenSize;
        this.chunkTokenOverlap = chunkTokenOverlap;
        this.tokenCounter = tokenCounter;
    }

    @Override
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // 简单的迭代拆分逻辑
        // 注意：这只是一个基础实现，真实的 TokenSplitter 往往会结合 Recursive 的逻辑，
        // 即先按标点拆分，再检查 Token 数。
        int start = 0;
        int textLength = text.length();

        while (start < textLength) {
            int end = findSplitEnd(text, start);
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= textLength) break;

            // 计算下一个起始点（应用 overlap）
            // 这里我们粗略地按比例倒退字符
            int currentChunkChars = end - start;
            int currentTokens = tokenCounter.apply(chunk);
            int charsPerToken = Math.max(1, currentChunkChars / Math.max(1, currentTokens));
            int overlapChars = chunkTokenOverlap * charsPerToken;

            int nextStart = end - overlapChars;
            // 确保进度向前
            start = (nextStart <= start) ? end : nextStart;
        }

        return chunks;
    }

    private int findSplitEnd(String text, int start) {
        int low = start;
        int high = text.length();
        int bestEnd = start + 1;

        // 使用二分查找寻找在 chunkTokenSize 限制下的最大字符偏移
        while (low <= high) {
            int mid = low + (high - low) / 2;
            String sub = text.substring(start, Math.min(mid, text.length()));
            int tokens = tokenCounter.apply(sub);

            if (tokens <= chunkTokenSize) {
                bestEnd = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return Math.min(bestEnd, text.length());
    }
}
