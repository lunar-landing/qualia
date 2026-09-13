package cn.lunarlanding.qualia.core.memory;

/**
 * 消息附件引用元数据（随记忆持久化）
 * 仅保存名称与类型；附件实际内容（图片字节、文档解析文本）存于附件仓库，不入记忆
 */
public class AttachmentRef {

    /** IMAGE / DOCUMENT（对应 Attachment.Type 的枚举名） */
    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_DOCUMENT = "DOCUMENT";

    private String name;   // 附件名称（含扩展名）
    private String type;   // IMAGE / DOCUMENT

    public AttachmentRef() {
    }

    public AttachmentRef(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
