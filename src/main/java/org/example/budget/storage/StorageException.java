package org.example.budget.storage;

/** A database failure behind a domain repository interface, which doesn't declare {@code SQLException}. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
