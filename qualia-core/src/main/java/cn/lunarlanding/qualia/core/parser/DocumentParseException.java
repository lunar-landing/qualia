package cn.lunarlanding.qualia.core.parser;

/**
 * 文件解析失败异常。
 *
 * <p>解析过程中的确定性失败（加密、格式不符、扫描版无文字层等）统一抛出本异常，
 * 携带面向用户的可读原因。</p>
 */
public class DocumentParseException extends RuntimeException {

    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
