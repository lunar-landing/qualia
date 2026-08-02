package com.lunarlanding.qualia.core.model.chat;

/**
 * 选择项类
 */
public class ChatChoice {
    private Integer index;
    private ChatMessage message;
    private String finishReason;

    public ChatChoice() {}

    public ChatChoice(Integer index, ChatMessage message, String finishReason) {
        this.index = index;
        this.message = message;
        this.finishReason = finishReason;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public ChatMessage getMessage() {
        return message;
    }

    public void setMessage(ChatMessage message) {
        this.message = message;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public String getFinishReason() {
        return finishReason;
    }
}
