package cn.lunarlanding.qualia.core.model.chat;

import java.util.Map;

/**
 * 多模态消息内容块，符合 OpenAI Chat Completions 规范的 content 数组项。
 *
 * <p>两种类型：</p>
 * <ul>
 *   <li>{@code text}：文字块，携带 {@code text} 字段</li>
 *   <li>{@code image_url}：图片块，携带 {@code image_url.url} 字段，
 *       支持网络地址或 {@code data:image/png;base64,...} 内嵌数据</li>
 * </ul>
 *
 * <p>序列化示例：</p>
 * <pre>{@code
 * {"type":"text","text":"描述这张图片"}
 * {"type":"image_url","image_url":{"url":"data:image/png;base64,iVBOR..."}}
 * }</pre>
 */
public class ContentPart {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE_URL = "image_url";

    /** 内容块类型：text / image_url */
    private String type;

    /** text 类型的内容 */
    private String text;

    /** image_url 类型的图片描述对象（含 url 字段） */
    private Map<String, String> imageUrl;

    private ContentPart() {}

    /** 创建文字内容块 */
    public static ContentPart text(String text) {
        ContentPart part = new ContentPart();
        part.type = TYPE_TEXT;
        part.text = text;
        return part;
    }

    /** 创建图片内容块（url 支持网络地址或 data:image/xxx;base64,... 内嵌数据） */
    public static ContentPart imageUrl(String url) {
        ContentPart part = new ContentPart();
        part.type = TYPE_IMAGE_URL;
        part.imageUrl = Map.of("url", url);
        return part;
    }

    public String getType() { return type; }

    public String getText() { return text; }

    public Map<String, String> getImageUrl() { return imageUrl; }
}
