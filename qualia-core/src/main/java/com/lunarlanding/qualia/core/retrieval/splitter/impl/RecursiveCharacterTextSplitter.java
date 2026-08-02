package com.lunarlanding.qualia.core.retrieval.splitter.impl;

import com.lunarlanding.qualia.core.retrieval.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// 递归字符分割器
public class RecursiveCharacterTextSplitter implements TextSplitter {
    private int chunkSize;
    private int chunkOverlap;
    private List<Character> separators;

    public RecursiveCharacterTextSplitter(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, Arrays.asList(
            '\n',
            '.', '。',
            '!', '！',
            '?', '？',
            ';', '；',
            ':', '：',
            ',', '，', '、',
            ' ', '\t'
        ));
    }

    public RecursiveCharacterTextSplitter(int chunkSize, int chunkOverlap, List<Character> separators) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.separators = separators;
    }

    @Override
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            // 如果剩余文本小于等于 chunkSize，直接添加并结束
            if (text.length() - start <= chunkSize) {
                String lastChunk = text.substring(start).trim();
                if (!lastChunk.isEmpty()) {
                    chunks.add(lastChunk);
                }
                break;
            }

            // 寻找当前窗口内的最佳分割点
            String currentWindow = text.substring(start, Math.min(start + chunkSize, text.length()));
            int splitPointInWindow = findBestSplitPoint(currentWindow);

            // 如果没找到分割点，强行切割 chunkSize
            if (splitPointInWindow <= 0) {
                splitPointInWindow = chunkSize;
            }

            // 添加当前块
            String chunk = text.substring(start, start + splitPointInWindow).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // 计算下一个起始点，应用 overlap
            // 关键：确保 start 必须增加，防止死循环
            int nextStart = start + splitPointInWindow - chunkOverlap;
            if (nextStart <= start) {
                start = start + splitPointInWindow;
            } else {
                start = nextStart;
            }
        }
        return chunks;
    }

    private int findBestSplitPoint(String text) {
        // 从后往前寻找窗口内出现的“任何”已知分隔符
        // 这样可以确保分块尽可能接近 chunkSize，而不是一见到高优先级符号就切断
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            for (char separator : separators) {
                if (c == separator) {
                    return i + 1;
                }
            }
        }
        return -1;
    }
}
