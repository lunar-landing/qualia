package cn.lunarlanding.qualia.core.model.chat;

/**
 * 统一附件模型（图片与文档）
 *
 * <p>图片走视觉通道（dataUrl 直传视觉模型），文档走文字注入（解析正文），
 * 两个内容字段按类型互斥填充。</p>
 */
public record Attachment(String name, Type type, String dataUrl, String parsedContent) {

    public enum Type { IMAGE, DOCUMENT }

    public static Attachment image(String name, String dataUrl) {
        return new Attachment(name, Type.IMAGE, dataUrl, null);
    }

    public static Attachment document(String name, String parsedContent) {
        return new Attachment(name, Type.DOCUMENT, null, parsedContent);
    }
}
