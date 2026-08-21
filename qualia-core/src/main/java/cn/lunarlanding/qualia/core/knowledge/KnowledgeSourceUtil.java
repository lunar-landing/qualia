package cn.lunarlanding.qualia.core.knowledge;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.lunarlanding.qualia.core.agent.spec.AgentStep;
import cn.lunarlanding.qualia.core.agent.spec.KnowledgeSource;
import cn.lunarlanding.qualia.core.retrieval.RetrievalResult;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Knowledge provenance utilities: extracts and deduplicates knowledge sources from
 * ReAct observation steps, resolves human-readable chapter information from
 * retrieval metadata and chunk content, and formats retrieval results with
 * consistent provenance fields for downstream clients.
 */
public final class KnowledgeSourceUtil {

    private static final int MAX_SOURCES = 8;

    private static final Pattern BLOCK_HEADING_PATTERN = Pattern.compile(
            "^\\s*\\[block=\\d+(?:\\s+page=\\d+)?\\s+type=heading\\]\\s*(.+)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^\\s*#{1,6}\\s+(.+)$");
    private static final Pattern NUMBERED_HEADING_PATTERN = Pattern.compile(
            "^(?:第.{1,24}[章節节條条款]|(?:\\d+(?:\\.\\d+){0,4}|[一二三四五六七八九十百]+)[、.．]\\s*).+"
    );

    private KnowledgeSourceUtil() {
    }

    // ------------------------------------------------------------------
    // Knowledge source extraction from agent steps
    // ------------------------------------------------------------------

    public static List<KnowledgeSource> fromSteps(List<AgentStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }

        Map<String, KnowledgeSource> sources = new LinkedHashMap<>();
        for (AgentStep step : steps) {
            if (step == null
                    || step.getStepType() != AgentStep.StepType.OBSERVATION
                    || step.getContent() == null
                    || step.getContent().isBlank()) {
                continue;
            }
            collectObservation(step.getContent(), sources);
        }

        return sources.values().stream()
                .sorted(Comparator.comparingDouble(KnowledgeSource::getScore).reversed())
                .limit(MAX_SOURCES)
                .toList();
    }

    private static void collectObservation(String content, Map<String, KnowledgeSource> sources) {
        try {
            JSONObject data = JSON.parseObject(content);
            String sourceType = data.getString("source");
            if (!isKnowledgeSource(sourceType)) {
                return;
            }
            String defaultSource = firstNonBlank(data.getString("knowledgeBase"), sourceLabel(sourceType));
            JSONArray items = data.getJSONArray("items");
            if (items == null) {
                return;
            }

            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String fileName = firstNonBlank(
                        item.getString("fileName"),
                        item.getString("documentName"),
                        item.getString("title")
                );
                String excerpt = firstNonBlank(item.getString("content"), item.getString("content_slice"));
                String chapter = firstNonBlank(
                        item.getString("chapter"),
                        resolveChapter(item, excerpt)
                );
                String sourceName = firstNonBlank(item.getString("knowledgeBase"), defaultSource);
                double score = item.getDoubleValue("score");
                String key = String.join(
                        "\u0000",
                        nullToEmpty(sourceName),
                        nullToEmpty(fileName),
                        nullToEmpty(chapter)
                );

                KnowledgeSource existing = sources.get(key);
                if (existing == null) {
                    KnowledgeSource source = new KnowledgeSource();
                    source.setSource(nullToEmpty(sourceName));
                    source.setFileName(nullToEmpty(fileName));
                    source.setChapter(nullToEmpty(chapter));
                    source.setExcerpt(nullToEmpty(excerpt));
                    source.setScore(score);
                    sources.put(key, source);
                } else {
                    existing.setScore(Math.max(existing.getScore(), score));
                    if (existing.getExcerpt().isBlank() && excerpt != null) {
                        existing.setExcerpt(excerpt);
                    }
                }
            }
        } catch (Exception ignored) {
            // Non-knowledge observations may be plain text or arbitrary tool JSON.
        }
    }

    private static boolean isKnowledgeSource(String sourceType) {
        if (sourceType == null) {
            return false;
        }
        return sourceType.equalsIgnoreCase("ragflow")
                || sourceType.equalsIgnoreCase("knowbase")
                || sourceType.equalsIgnoreCase("ragflow-api")
                || sourceType.equalsIgnoreCase("ragflow-es");
    }

    private static String sourceLabel(String sourceType) {
        return "knowbase".equalsIgnoreCase(sourceType) ? "Knowledge Base" : "RAGFlow";
    }

    // ------------------------------------------------------------------
    // Chapter resolution from retrieval metadata and chunk content
    // ------------------------------------------------------------------

    public static String resolveChapter(Map<String, Object> metadata, String content) {
        String direct = firstNonBlank(
                value(metadata, "chapter"),
                value(metadata, "chapter_name"),
                value(metadata, "section"),
                value(metadata, "section_name"),
                value(metadata, "section_title"),
                value(metadata, "heading")
        );
        if (direct != null) {
            return clean(direct);
        }

        String contentHeading = headingFromContent(content);
        if (contentHeading != null) {
            return contentHeading;
        }

        String keywordHeading = headingFromKeywords(
                metadata == null ? null : firstPresent(
                        metadata.get("important_kwd"),
                        metadata.get("important_keywords")
                )
        );
        return keywordHeading == null ? "" : keywordHeading;
    }

    private static String headingFromContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String[] lines = content.split("\\R", 12);
        for (String line : lines) {
            String normalized = line == null ? "" : line.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            Matcher blockMatcher = BLOCK_HEADING_PATTERN.matcher(normalized);
            if (blockMatcher.matches()) {
                return clean(blockMatcher.group(1));
            }
            Matcher markdownMatcher = MARKDOWN_HEADING_PATTERN.matcher(normalized);
            if (markdownMatcher.matches()) {
                return clean(markdownMatcher.group(1));
            }
            String withoutMarker = normalized.replaceFirst(
                    "^\\[block=\\d+(?:\\s+page=\\d+)?\\s+type=[^\\]]+\\]\\s*",
                    ""
            );
            if (NUMBERED_HEADING_PATTERN.matcher(withoutMarker).matches()) {
                return clean(withoutMarker);
            }
        }
        return null;
    }

    private static String headingFromKeywords(Object rawKeywords) {
        List<String> candidates = textValues(rawKeywords);
        String longest = null;
        for (String candidate : candidates) {
            String cleaned = clean(candidate);
            if (cleaned == null || cleaned.length() > 120) {
                continue;
            }
            if (NUMBERED_HEADING_PATTERN.matcher(cleaned).matches()) {
                return cleaned;
            }
            if (longest == null || cleaned.length() > longest.length()) {
                longest = cleaned;
            }
        }
        return longest;
    }

    private static List<String> textValues(Object value) {
        List<String> values = new ArrayList<>();
        if (value == null) {
            return values;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    values.add(item.toString());
                }
            }
            return values;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
                if (item != null) {
                    values.add(item.toString());
                }
            }
            return values;
        }
        String text = value.toString().trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        for (String item : text.split("[,，;；|]")) {
            if (!item.isBlank()) {
                values.add(item.replace("\"", "").trim());
            }
        }
        return values;
    }

    // ------------------------------------------------------------------
    // Retrieval result formatting
    // ------------------------------------------------------------------

    public static JSONObject formatItem(RetrievalResult result) {
        JSONObject item = new JSONObject();
        Map<String, Object> metadata = result.getMetadata();
        String fileName = stringValue(metadata, "document_name");
        if (fileName != null) {
            item.put("title", fileName);
            item.put("fileName", fileName);
        }

        String chapter = resolveChapter(metadata, result.getContent());
        if (!chapter.isBlank()) {
            item.put("chapter", chapter);
        }

        Object pageNumber = firstMetadataValue(metadata, "page_num", "page_num_int", "page_number");
        if (pageNumber != null) {
            item.put("pageNumber", pageNumber);
        }

        item.put("score", Math.round(result.getScore() * 100.0) / 100.0);
        item.put("content", result.getContent());
        return item;
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private static String value(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : value.toString();
    }

    private static String stringValue(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }

    private static Object firstMetadataValue(Map<String, Object> metadata, String... keys) {
        if (metadata == null) {
            return null;
        }
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Object firstPresent(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replaceFirst("^\\s*#{1,6}\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
