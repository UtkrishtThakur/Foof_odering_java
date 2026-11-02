package com.foodordering.persistence;

import java.nio.file.Path;

/**
 * Thrown when an error occurs during persistence operations (CSV serialization,
 * deserialization, file I/O, or parsing validation).
 */
public class PersistenceException extends RuntimeException {

    private final Path affectedFile;

    /**
     * Constructs a PersistenceException with a message.
     *
     * @param message the detail message
     */
    public PersistenceException(String message) {
        super(message);
        this.affectedFile = null;
    }

    /**
     * Constructs a PersistenceException with a message and cause.
     *
     * @param message the detail message
     * @param cause   the root cause
     */
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
        this.affectedFile = null;
    }

    /**
     * Constructs a PersistenceException with a message and the path to the affected file.
     *
     * @param message      the detail message
     * @param affectedFile the file involved in the failed persistence operation
     */
    public PersistenceException(String message, Path affectedFile) {
        super(message);
        this.affectedFile = affectedFile;
    }

    /**
     * Constructs a PersistenceException with a message, cause, and the path to the affected file.
     *
     * @param message      the detail message
     * @param cause        the root cause
     * @param affectedFile the file involved in the failed persistence operation
     */
    public PersistenceException(String message, Throwable cause, Path affectedFile) {
        super(message, cause);
        this.affectedFile = affectedFile;
    }

    /**
     * Returns the path of the file affected by the failure, if known.
     *
     * @return the affected file path, or {@code null}
     */
    public Path getAffectedFile() {
        return affectedFile;
    }

    @Override
    public String getMessage() {
        if (affectedFile != null) {
            return super.getMessage() + " [File: " + affectedFile.toAbsolutePath() + "]";
        }
        return super.getMessage();
    }
}
