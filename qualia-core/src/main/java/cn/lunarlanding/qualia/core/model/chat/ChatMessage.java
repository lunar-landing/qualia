package cn.lunarlanding.qualia.core.model.chat;

/**
 * 聊天消息类，封装角色和内容信息
 */
public class ChatMessage {
    private String role;  // 角色：user, tool, assistant, system
    private String content;  // 消息内容
    private String reasoningContent;  // 深度思考内容
    private String toolCallId;  // 工具调用ID（tool角色必填）

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public ChatMessage(String role, String content, String reasoningContent) {
        this.role = role;
        this.content = content;
        this.reasoningContent = reasoningContent;
    }

    public ChatMessage(String role, String content, String reasoningContent, String toolCallId) {
        this.role = role;
        this.content = content;
        this.reasoningContent = reasoningContent;
        this.toolCallId = toolCallId;
    }

    @Override
    public String toString() {
        return "Message{role='" + role + "', content='" + content + "', reasoningContent='" + reasoningContent + "'}";
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    /**
     * 创建用户消息
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    /**
     * 创建助手消息
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    /**
     * 创建系统消息
     */
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    /**
     * 创建工具消息
     */
    public static ChatMessage tool(String content) {
        return new ChatMessage("tool", content);
    }

    /**
     * 创建带tool_call_id的工具消息
     */
    public static ChatMessage tool(String content, String toolCallId) {
        ChatMessage msg = new ChatMessage("tool", content);
        msg.setToolCallId(toolCallId);
        return msg;
    }

    /**
     * 创建带深度思考的助手消息
     */
    public static ChatMessage assistantWithThinking(String content, String reasoningContent) {
        return new ChatMessage("assistant", content, reasoningContent);
    }

    /**
     * 是否包含深度思考内容
     */
    public boolean hasReasoningContent() {
        return reasoningContent != null && !reasoningContent.isEmpty();
    }
}
