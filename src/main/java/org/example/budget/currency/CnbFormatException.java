package org.example.budget.currency;

/** A ČNB response that isn't a daily rates file in the documented format. */
public class CnbFormatException extends RuntimeException {

    public CnbFormatException(String message) {
        super(message);
    }

    public CnbFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
