package cn.lunarlanding.qualia.core.model.chat;

/**
 * 助手消息携带的单次工具调用（OpenAI function calling 协议载荷）
 *
 * <p>流式响应中以分片（delta）形态到达：同一调用的 arguments 按
 * {@code index} 聚合拼接；{@code index} 仅用于流式聚合，请求序列化时忽略。</p>
 */
public class ToolCallPayload {

    /** 工具调用 ID（tool 角色消息通过 tool_call_id 关联） */
    private String id;

    /** 分片聚合序号（流式响应中标识属于哪个调用） */
    private Integer index;

    /** 工具名称 */
    private String name;

    /** 参数 JSON 字符串（原始形态，流式分片拼接后由调用方解析） */
    private String arguments;

    public ToolCallPayload() {}

    public ToolCallPayload(String id, String name, String arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    /** 追加流式分片的参数片段 */
    public void appendArguments(String fragment) {
        this.arguments = (this.arguments == null ? "" : this.arguments) + fragment;
    }
}
