package com.foodordering.persistence;

import com.foodordering.model.Admin;
import com.foodordering.model.Customer;
import com.foodordering.model.User;
import com.foodordering.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CSV User Storage Tests")
class CsvUserStorageTest {

    @Test
    @DisplayName("Should return empty list when users.csv does not exist")
    void shouldReturnEmptyListWhenFileDoesNotExist(@TempDir Path tempDir) {
        Path userFile = tempDir.resolve("users.csv");
        CsvUserStorage storage = new CsvUserStorage(userFile);
        assertTrue(storage.load().isEmpty());
    }

    @Test
    @DisplayName("Should round-trip save and reload Customer and Admin entities correctly")
    void shouldRoundTripSaveAndReloadUsersAccurately(@TempDir Path tempDir) {
        Path userFile = tempDir.resolve("users.csv");
        CsvUserStorage storage = new CsvUserStorage(userFile);

        Customer customer = new Customer("C001", "John Customer", "john@example.com", "hash_c1", "123 Elm St, City");
        Admin admin = new Admin("admin", "Admin User", "admin@food.com", "hash_adm", "ADM-SEC");

        storage.saveAll(List.of(customer, admin));

        assertTrue(Files.exists(userFile));

        List<User> loaded = storage.load();
        assertEquals(2, loaded.size());

        // Verify Customer
        assertTrue(loaded.get(0) instanceof Customer);
        Customer loadedCustomer = (Customer) loaded.get(0);
        assertEquals("C001", loadedCustomer.getUserId());
        assertEquals("CUSTOMER", loadedCustomer.getRole());
        assertEquals("John Customer", loadedCustomer.getName());
        assertEquals("john@example.com", loadedCustomer.getEmail());
        assertEquals("hash_c1", loadedCustomer.getPasswordHash());
        assertEquals("123 Elm St, City", loadedCustomer.getDeliveryAddress());

        // Verify Admin
        assertTrue(loaded.get(1) instanceof Admin);
        Admin loadedAdmin = (Admin) loaded.get(1);
        assertEquals("admin", loadedAdmin.getUserId());
        assertEquals("ADMIN", loadedAdmin.getRole());
        assertEquals("Admin User", loadedAdmin.getName());
        assertEquals("admin@food.com", loadedAdmin.getEmail());
        assertEquals("hash_adm", loadedAdmin.getPasswordHash());
        assertEquals("ADM-SEC", loadedAdmin.getAdminCode());
    }

    @Test
    @DisplayName("Should throw PersistenceException on duplicate user IDs in CSV")
    void shouldRejectDuplicateUserIds(@TempDir Path tempDir) throws IOException {
        Path userFile = tempDir.resolve("users.csv");
        String duplicateCsv = """
                user_id,role,name,email,password_hash,delivery_address,admin_code
                user1,CUSTOMER,Alice,alice@example.com,hash1,Street,
                user1,ADMIN,Bob,bob@example.com,hash2,,ADM-01
                """;
        Files.writeString(userFile, duplicateCsv);

        CsvUserStorage storage = new CsvUserStorage(userFile);
        assertThrows(PersistenceException.class, storage::load);
    }

    @Test
    @DisplayName("Should throw PersistenceException on unknown role")
    void shouldRejectUnknownRole(@TempDir Path tempDir) throws IOException {
        Path userFile = tempDir.resolve("users.csv");
        String badRoleCsv = """
                user_id,role,name,email,password_hash,delivery_address,admin_code
                user1,SUPER_USER,Alice,alice@example.com,hash1,,
                """;
        Files.writeString(userFile, badRoleCsv);

        CsvUserStorage storage = new CsvUserStorage(userFile);
        assertThrows(PersistenceException.class, storage::load);
    }

    @Test
    @DisplayName("Should roll back in-memory change when UserRepository fails to persist")
    void shouldRollbackInMemoryOnPersistenceFailure(@TempDir Path tempDir) {
        Path readOnlyDir = tempDir.resolve("protected");
        Path invalidTarget = readOnlyDir.resolve("users.csv");

        // Point to a path that will fail if directory cannot be written or invalid
        CsvUserStorage storage = new CsvUserStorage(tempDir.resolve("nonexistent_sub").resolve("deep").resolve("users.csv")) {
            @Override
            public void saveAll(java.util.Collection<User> users) throws PersistenceException {
                throw new PersistenceException("Simulated I/O disk failure");
            }
        };

        UserRepository repo = new UserRepository(storage);
        Customer customer = new Customer("C001", "John", "john@test.com", "h", "Addr");

        assertThrows(PersistenceException.class, () -> repo.save(customer));

        // In-memory store should have rolled back
        assertFalse(repo.existsById("C001"));
        assertEquals(0, repo.count());
    }
}
