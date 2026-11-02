package com.foodordering.persistence;

import com.foodordering.model.MenuItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CSV Menu Storage Tests")
class CsvMenuStorageTest {

    @Test
    @DisplayName("Should return empty list when file does not exist")
    void shouldReturnEmptyListWhenFileDoesNotExist(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("missing_menu.csv");
        CsvMenuStorage storage = new CsvMenuStorage(nonExistent);

        List<MenuItem> items = storage.load();
        assertTrue(items.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for empty or header-only file")
    void shouldReturnEmptyListForEmptyOrHeaderOnlyFile(@TempDir Path tempDir) throws IOException {
        Path emptyFile = tempDir.resolve("empty.csv");
        Files.createFile(emptyFile);
        CsvMenuStorage storage1 = new CsvMenuStorage(emptyFile);
        assertTrue(storage1.load().isEmpty());

        Path headerOnly = tempDir.resolve("header_only.csv");
        Files.writeString(headerOnly, "id,name,category,price,stock_quantity,available\n");
        CsvMenuStorage storage2 = new CsvMenuStorage(headerOnly);
        assertTrue(storage2.load().isEmpty());
    }

    @Test
    @DisplayName("Should save and reload menu items accurately")
    void shouldSaveAndReloadMenuItemsAccurately(@TempDir Path tempDir) {
        Path menuFile = tempDir.resolve("menu_items.csv");
        CsvMenuStorage storage = new CsvMenuStorage(menuFile);

        MenuItem item1 = new MenuItem("M001", "Margherita Pizza", "Pizza", new BigDecimal("8.99"), 20, true);
        MenuItem item2 = new MenuItem("M002", "Chef's \"Special\", Burger", "Burgers", new BigDecimal("12.50"), 5, false);

        storage.saveAll(List.of(item1, item2));
        assertTrue(Files.exists(menuFile));

        List<MenuItem> loaded = storage.load();
        assertEquals(2, loaded.size());

        MenuItem loaded1 = loaded.get(0);
        assertEquals("M001", loaded1.getId());
        assertEquals("Margherita Pizza", loaded1.getName());
        assertEquals("Pizza", loaded1.getCategory());
        assertEquals(new BigDecimal("8.99"), loaded1.getPrice());
        assertEquals(20, loaded1.getStockQuantity());
        assertTrue(loaded1.isAvailable());

        MenuItem loaded2 = loaded.get(1);
        assertEquals("M002", loaded2.getId());
        assertEquals("Chef's \"Special\", Burger", loaded2.getName());
        assertEquals(new BigDecimal("12.50"), loaded2.getPrice());
        assertEquals(5, loaded2.getStockQuantity());
        assertFalse(loaded2.isAvailable());
    }

    @Test
    @DisplayName("Should reject duplicate IDs in CSV during load")
    void shouldRejectDuplicateIdsInCsvDuringLoad(@TempDir Path tempDir) throws IOException {
        Path menuFile = tempDir.resolve("duplicate_menu.csv");
        String content = """
                id,name,category,price,stock_quantity,available
                M001,Pizza,Pizza,8.99,10,true
                M001,Duplicate Pizza,Pizza,9.99,5,true
                """;
        Files.writeString(menuFile, content);

        CsvMenuStorage storage = new CsvMenuStorage(menuFile);
        assertThrows(PersistenceException.class, storage::load);
    }

    @Test
    @DisplayName("Should throw PersistenceException on malformed columns")
    void shouldThrowPersistenceExceptionOnMalformedColumns(@TempDir Path tempDir) throws IOException {
        Path menuFile = tempDir.resolve("malformed.csv");
        String content = """
                id,name,category,price,stock_quantity,available
                M001,Pizza,Pizza,8.99
                """;
        Files.writeString(menuFile, content);

        CsvMenuStorage storage = new CsvMenuStorage(menuFile);
        assertThrows(PersistenceException.class, storage::load);
    }

    @Test
    @DisplayName("Should throw PersistenceException on invalid price format")
    void shouldThrowPersistenceExceptionOnInvalidPriceFormat(@TempDir Path tempDir) throws IOException {
        Path menuFile = tempDir.resolve("invalid_price.csv");
        String content = """
                id,name,category,price,stock_quantity,available
                M001,Pizza,Pizza,NOT_A_PRICE,10,true
                """;
        Files.writeString(menuFile, content);

        CsvMenuStorage storage = new CsvMenuStorage(menuFile);
        assertThrows(PersistenceException.class, storage::load);
    }
}
