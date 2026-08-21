package cn.lunarlanding.qualia.core.model.chat;

import java.util.List;

/**
 * 聊天模型响应数据结构
 */
public class ChatResponse {
    private ChatUsage usage;
    private List<ChatChoice> choices;
    private String model;
    private String id;
    public ChatResponse() {}
    public ChatResponse(List<ChatChoice> choices) {
        this.choices = choices;
    }
    public List<ChatChoice> getChoices() {
        return choices;
    }
    public void setChoices(List<ChatChoice> choices) {
        this.choices = choices;
    }
    public ChatUsage getUsage() {
        return usage;
    }
    public void setUsage(ChatUsage usage) {
        this.usage = usage;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
