package org.velja.app.sokker;

public class SokkerApiException extends RuntimeException {
    public SokkerApiException(String message) {
        super(message);
    }

    public SokkerApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
