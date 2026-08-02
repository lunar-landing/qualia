package com.lunarlanding.qualia.core.other.ragflow;

public class RagflowException extends RuntimeException {

    public RagflowException(String message) {
        super(message);
    }

    public RagflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
