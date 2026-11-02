package com.foodordering.persistence;

import com.foodordering.model.Admin;
import com.foodordering.model.Customer;
import com.foodordering.model.User;

import java.io.BufferedReader;
import java.io.IOException;
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
 * Handles CSV serialization and deserialization for {@link User} domain objects
 * (both {@link Customer} and {@link Admin}).
 *
 * <p>CSV Header Format:
 * <pre>
 * user_id,role,name,email,password_hash,delivery_address,admin_code
 * </pre>
 * </p>
 */
public class CsvUserStorage {

    public static final List<String> HEADER = List.of(
            "user_id", "role", "name", "email", "password_hash", "delivery_address", "admin_code"
    );

    private final Path filePath;

    /**
     * Constructs a CsvUserStorage targeting the specified CSV file path.
     *
     * @param filePath destination path for users CSV file
     */
    public CsvUserStorage(Path filePath) {
        if (filePath == null) {
            throw new PersistenceException("User CSV file path must not be null.");
        }
        this.filePath = filePath;
    }

    public Path getFilePath() {
        return filePath;
    }

    /**
     * Loads and deserializes all users (customers and administrators) from the CSV file.
     *
     * @return unmodifiable list of loaded {@link User} objects (empty if file does not exist)
     * @throws PersistenceException if the file is malformed, has invalid values, or contains duplicates
     */
    public List<User> load() throws PersistenceException {
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }

        List<List<String>> records;
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            records = CsvUtils.readRecords(reader);
        } catch (IOException e) {
            throw new PersistenceException("Failed to read users file: " + filePath, e, filePath);
        }

        if (records.isEmpty() || records.size() == 1) {
            // Empty file or header-only file
            return Collections.emptyList();
        }

        List<String> fileHeader = records.get(0);
        validateHeader(fileHeader);

        List<User> users = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (int i = 1; i < records.size(); i++) {
            List<String> row = records.get(i);
            int rowNumber = i + 1;

            if (row.size() != HEADER.size()) {
                throw new PersistenceException(String.format(
                        "Malformed record in %s at row %d: expected %d columns but found %d.",
                        filePath.getFileName(), rowNumber, HEADER.size(), row.size()), filePath);
            }

            String userId = row.get(0).strip();
            String role = row.get(1).strip().toUpperCase();
            String name = row.get(2);
            String email = row.get(3).strip();
            String passwordHash = row.get(4);
            String deliveryAddress = row.get(5);
            String adminCode = row.get(6).strip();

            if (userId.isBlank()) {
                throw new PersistenceException("User ID cannot be blank at row " + rowNumber, filePath);
            }
            if (!seenIds.add(userId)) {
                throw new PersistenceException("Duplicate user ID '" + userId + "' found at row " + rowNumber, filePath);
            }

            try {
                if ("CUSTOMER".equals(role)) {
                    String addr = (deliveryAddress == null || deliveryAddress.isBlank()) ? null : deliveryAddress;
                    users.add(new Customer(userId, name, email, passwordHash, addr));
                } else if ("ADMIN".equals(role)) {
                    users.add(new Admin(userId, name, email, passwordHash, adminCode));
                } else {
                    throw new PersistenceException("Unknown user role '" + role + "' at row " + rowNumber, filePath);
                }
            } catch (IllegalArgumentException ex) {
                throw new PersistenceException("Domain validation failed at row " + rowNumber + ": " + ex.getMessage(),
                        ex, filePath);
            }
        }

        return Collections.unmodifiableList(users);
    }

    /**
     * Serializes and writes all supplied users to the target CSV file atomically.
     *
     * @param users collection of {@link User} objects to write
     * @throws PersistenceException if writing fails
     */
    public void saveAll(Collection<User> users) throws PersistenceException {
        List<List<String>> rows = new ArrayList<>();
        if (users != null) {
            for (User user : users) {
                String role = user.getRole();
                String deliveryAddress = "";
                String adminCode = "";

                if (user instanceof Customer customer) {
                    deliveryAddress = customer.getDeliveryAddress() != null ? customer.getDeliveryAddress() : "";
                } else if (user instanceof Admin admin) {
                    adminCode = admin.getAdminCode() != null ? admin.getAdminCode() : "";
                }

                rows.add(List.of(
                        user.getUserId(),
                        role,
                        user.getName(),
                        user.getEmail(),
                        user.getPasswordHash() != null ? user.getPasswordHash() : "",
                        deliveryAddress,
                        adminCode
                ));
            }
        }

        CsvUtils.writeRecordsSafely(filePath, HEADER, rows);
    }

    private void validateHeader(List<String> header) {
        if (header.size() != HEADER.size()) {
            throw new PersistenceException("Invalid header length in users file: " + header, filePath);
        }
        for (int i = 0; i < HEADER.size(); i++) {
            if (!HEADER.get(i).equalsIgnoreCase(header.get(i).strip())) {
                throw new PersistenceException(String.format(
                        "Header mismatch in users file at column %d: expected '%s' but found '%s'.",
                        i, HEADER.get(i), header.get(i)), filePath);
            }
        }
    }
}
