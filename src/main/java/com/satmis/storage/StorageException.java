package com.satmis.storage;

// Storage is unreachable or refused a call; the message names the operation and key, never credentials
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
