package cn.lunarlanding.qualia.core.parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 文件解析器：把文件内容（字节或磁盘路径）解析为文本文档列表。
 *
 * <p>解析器职责是忠实转换，不做内容清洗；类型判断只依据文件名后缀。
 * 输入支持两种形态：</p>
 * <ul>
 *   <li>{@link #parse(byte[])}：调用方已持有字节（如网络传输的文件）</li>
 *   <li>{@link #parse(Path)}：磁盘路径，默认实现读取字节后委托字节解析</li>
 * </ul>
 */
public interface DocumentParser {

    /** 判断是否支持解析该文件（按文件名后缀，大小写不敏感） */
    boolean supports(String fileName);

    /** 从文件字节解析为文档列表 */
    List<Document> parse(byte[] data);

    /** 从磁盘路径解析，默认读取全量字节后委托 {@link #parse(byte[])} */
    default List<Document> parse(Path file) throws IOException {
        return parse(Files.readAllBytes(file));
    }

    /** 按扩展名判断文件名（大小写不敏感），供 supports 实现复用 */
    static boolean hasExtension(String fileName, String... extensions) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 文本解码：优先 UTF-8 严格解码，遇到非法字节序列时回退 GBK。
     * 供文本类解析器复用。
     */
    static String decodeText(byte[] data) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data))
                    .toString();
            // 去除 UTF-8 BOM，避免零宽标记混入正文首字符
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            return text;
        } catch (CharacterCodingException e) {
            return new String(data, Charset.forName("GBK"));
        }
    }
}
