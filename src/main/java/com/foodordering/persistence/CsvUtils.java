package com.foodordering.persistence;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared utility for RFC 4180-compliant CSV reading, writing, and safe atomic file persistence.
 *
 * <p>Handles:
 * <ul>
 *   <li>Commas, quotation marks ({@code "}), and newlines within fields.</li>
 *   <li>Escaped quotation marks (represented as {@code ""}).</li>
 *   <li>Unicode characters using {@link StandardCharsets#UTF_8}.</li>
 *   <li>Empty and null values.</li>
 *   <li>Safe write semantics: writes to a temporary file in the target directory,
 *       flushes and closes, and then performs an atomic move (with fallback).</li>
 * </ul>
 * </p>
 */
public final class CsvUtils {

    private CsvUtils() {
        // utility class
    }

    /**
     * Formats a single field according to RFC 4180:
     * Encloses in quotes if the field contains a comma, quote, or newline.
     * Escapes any inner double quote as {@code ""}.
     *
     * @param field the value to format
     * @return the properly formatted CSV field
     */
    public static String formatField(String field) {
        if (field == null) {
            return "";
        }
        boolean requiresQuotes = field.contains(",") || field.contains("\"")
                || field.contains("\n") || field.contains("\r");
        if (!requiresQuotes) {
            return field;
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }

    /**
     * Formats a collection of fields into a single CSV line.
     *
     * @param fields the list of column values
     * @return the comma-separated line
     */
    public static String formatRecord(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(formatField(fields.get(i)));
        }
        return sb.toString();
    }

    /**
     * Parses all CSV records from the supplied {@link Reader} according to RFC 4180 rules.
     * Correctly processes multiline fields, quotes, and empty fields.
     *
     * @param in the input reader
     * @return list of parsed records, each consisting of field strings
     * @throws IOException          if an I/O error occurs
     * @throws PersistenceException if a malformed CSV record (e.g. unclosed quote) is detected
     */
    public static List<List<String>> readRecords(Reader in) throws IOException, PersistenceException {
        BufferedReader reader = (in instanceof BufferedReader br) ? br : new BufferedReader(in);
        List<List<String>> records = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();

        int ch;
        boolean inQuotes = false;

        while ((ch = reader.read()) != -1) {
            char c = (char) ch;

            if (inQuotes) {
                if (c == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        // Escaped quote: "" becomes a literal "
                        currentField.append('"');
                    } else {
                        // Closing quote
                        inQuotes = false;
                        if (next != -1) {
                            reader.reset();
                        }
                    }
                } else {
                    currentField.append(c);
                }
            } else {
                if (c == '"') {
                    if (currentField.length() == 0) {
                        inQuotes = true;
                    } else {
                        throw new PersistenceException("Malformed CSV: unexpected quotation mark within field: '"
                                + currentField + "'");
                    }
                } else if (c == ',') {
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                } else if (c == '\r') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next != '\n' && next != -1) {
                        reader.reset();
                    }
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                    if (!isRecordEmpty(currentRecord)) {
                        records.add(new ArrayList<>(currentRecord));
                    }
                    currentRecord.clear();
                } else if (c == '\n') {
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                    if (!isRecordEmpty(currentRecord)) {
                        records.add(new ArrayList<>(currentRecord));
                    }
                    currentRecord.clear();
                } else {
                    currentField.append(c);
                }
            }
        }

        if (inQuotes) {
            throw new PersistenceException("Malformed CSV: unclosed quotation mark at end of stream.");
        }

        if (currentField.length() > 0 || !currentRecord.isEmpty()) {
            currentRecord.add(currentField.toString());
            if (!isRecordEmpty(currentRecord)) {
                records.add(new ArrayList<>(currentRecord));
            }
        }

        return records;
    }

    private static boolean isRecordEmpty(List<String> record) {
        if (record.isEmpty()) {
            return true;
        }
        if (record.size() == 1 && record.get(0).trim().isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * Safely and atomically writes header and data records to the target file.
     *
     * <p>Steps:
     * <ol>
     *   <li>Ensures parent directories exist.</li>
     *   <li>Writes contents to a temporary file in the target directory using UTF-8.</li>
     *   <li>Flushes and closes the stream.</li>
     *   <li>Replaces target file using {@link StandardCopyOption#ATOMIC_MOVE} with fallback to
     *       {@link StandardCopyOption#REPLACE_EXISTING}.</li>
     *   <li>Deletes temporary file if an exception occurs, preserving the original file.</li>
     * </ol>
     * </p>
     *
     * @param targetFile destination path
     * @param header     optional list of header column names (may be null)
     * @param records    data rows to write
     * @throws PersistenceException if file writing or replacement fails
     */
    public static void writeRecordsSafely(Path targetFile, List<String> header, List<List<String>> records)
            throws PersistenceException {
        if (targetFile == null) {
            throw new PersistenceException("Target file path must not be null.");
        }

        Path parentDir = targetFile.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            try {
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                throw new PersistenceException("Failed to create storage directory: " + parentDir, e, targetFile);
            }
        }

        Path tempFile;
        try {
            tempFile = Files.createTempFile(
                    parentDir != null ? parentDir : Paths.get("."),
                    targetFile.getFileName().toString(), ".tmp");
        } catch (IOException e) {
            throw new PersistenceException("Failed to create temporary file for: " + targetFile, e, targetFile);
        }

        try {
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                if (header != null && !header.isEmpty()) {
                    writer.write(formatRecord(header));
                    writer.newLine();
                }
                if (records != null) {
                    for (List<String> record : records) {
                        writer.write(formatRecord(record));
                        writer.newLine();
                    }
                }
                writer.flush();
            }

            try {
                Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException | UnsupportedOperationException ex) {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
            throw new PersistenceException("Failed to write data safely to file: " + targetFile, e, targetFile);
        }
    }
}
