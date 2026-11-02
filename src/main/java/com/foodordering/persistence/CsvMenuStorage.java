package com.foodordering.persistence;

import com.foodordering.model.MenuItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles CSV serialization and deserialization for {@link MenuItem} domain objects.
 *
 * <p>CSV Header Format:
 * <pre>
 * id,name,category,price,stock_quantity,available
 * </pre>
 * </p>
 */
public class CsvMenuStorage {

    public static final List<String> HEADER = List.of(
            "id", "name", "category", "price", "stock_quantity", "available");

    private final Path filePath;

    /**
     * Constructs a CsvMenuStorage targeting the given CSV file path.
     *
     * @param filePath destination path for menu items CSV
     */
    public CsvMenuStorage(Path filePath) {
        if (filePath == null) {
            throw new PersistenceException("Menu CSV file path must not be null.");
        }
        this.filePath = filePath;
    }

    public Path getFilePath() {
        return filePath;
    }

    /**
     * Loads and deserializes all menu items from the CSV file.
     *
     * @return unmodifiable list of loaded {@link MenuItem} objects (empty if file does not exist)
     * @throws PersistenceException if the file is malformed, has invalid values, or contains duplicates
     */
    public List<MenuItem> load() throws PersistenceException {
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }

        List<List<String>> records;
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            records = CsvUtils.readRecords(reader);
        } catch (IOException e) {
            throw new PersistenceException("Failed to read menu items file: " + filePath, e, filePath);
        }

        if (records.isEmpty() || records.size() == 1) {
            // Empty file or header-only file
            return Collections.emptyList();
        }

        // Validate header
        List<String> fileHeader = records.get(0);
        validateHeader(fileHeader);

        List<MenuItem> items = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (int i = 1; i < records.size(); i++) {
            List<String> row = records.get(i);
            int rowNumber = i + 1; // 1-based line number for error reporting

            if (row.size() != HEADER.size()) {
                throw new PersistenceException(String.format(
                        "Malformed record in %s at row %d: expected %d columns but found %d.",
                        filePath.getFileName(), rowNumber, HEADER.size(), row.size()), filePath);
            }

            String id = row.get(0).strip();
            String name = row.get(1);
            String category = row.get(2);
            String priceStr = row.get(3).strip();
            String stockStr = row.get(4).strip();
            String availStr = row.get(5).strip();

            if (id.isBlank()) {
                throw new PersistenceException("Menu item ID cannot be blank at row " + rowNumber, filePath);
            }
            if (!seenIds.add(id)) {
                throw new PersistenceException("Duplicate menu item ID '" + id + "' found at row " + rowNumber, filePath);
            }

            BigDecimal price;
            try {
                price = new BigDecimal(priceStr);
            } catch (NumberFormatException e) {
                throw new PersistenceException("Invalid price format '" + priceStr + "' at row " + rowNumber, e, filePath);
            }

            int stock;
            try {
                stock = Integer.parseInt(stockStr);
            } catch (NumberFormatException e) {
                throw new PersistenceException("Invalid stock format '" + stockStr + "' at row " + rowNumber, e, filePath);
            }

            boolean available = Boolean.parseBoolean(availStr);

            try {
                MenuItem item = new MenuItem(id, name, category, price, stock, available);
                items.add(item);
            } catch (IllegalArgumentException ex) {
                throw new PersistenceException("Domain validation failed at row " + rowNumber + ": " + ex.getMessage(),
                        ex, filePath);
            }
        }

        return Collections.unmodifiableList(items);
    }

    /**
     * Serializes and writes all supplied menu items to the target CSV file atomically.
     *
     * @param items collection of {@link MenuItem} objects to write
     * @throws PersistenceException if writing fails
     */
    public void saveAll(Collection<MenuItem> items) throws PersistenceException {
        List<List<String>> rows = new ArrayList<>();
        if (items != null) {
            for (MenuItem item : items) {
                rows.add(List.of(
                        item.getId(),
                        item.getName(),
                        item.getCategory(),
                        item.getPrice().toPlainString(),
                        String.valueOf(item.getStockQuantity()),
                        String.valueOf(item.isAvailable())
                ));
            }
        }

        CsvUtils.writeRecordsSafely(filePath, HEADER, rows);
    }

    private void validateHeader(List<String> header) {
        if (header.size() != HEADER.size()) {
            throw new PersistenceException("Invalid header length in menu file: " + header, filePath);
        }
        for (int i = 0; i < HEADER.size(); i++) {
            if (!HEADER.get(i).equalsIgnoreCase(header.get(i).strip())) {
                throw new PersistenceException(String.format(
                        "Header mismatch in menu file at column %d: expected '%s' but found '%s'.",
                        i, HEADER.get(i), header.get(i)), filePath);
            }
        }
    }
}
