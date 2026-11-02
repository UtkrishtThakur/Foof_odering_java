package com.foodordering.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CSV Utility Tests")
class CsvUtilsTest {

    @Test
    @DisplayName("Should format plain fields without quotes")
    void shouldFormatPlainFields() {
        assertEquals("Pizza", CsvUtils.formatField("Pizza"));
        assertEquals("10.50", CsvUtils.formatField("10.50"));
        assertEquals("", CsvUtils.formatField(""));
        assertEquals("", CsvUtils.formatField(null));
    }

    @Test
    @DisplayName("Should quote fields containing commas")
    void shouldQuoteFieldsWithCommas() {
        assertEquals("\"Pizza, Pasta\"", CsvUtils.formatField("Pizza, Pasta"));
    }

    @Test
    @DisplayName("Should escape double quotes by doubling them")
    void shouldEscapeDoubleQuotes() {
        assertEquals("\"Chef's \"\"Special\"\"\"", CsvUtils.formatField("Chef's \"Special\""));
    }

    @Test
    @DisplayName("Should format fields with commas and quotes combined")
    void shouldFormatCombinedCommasAndQuotes() {
        String input = "Chef's \"Special\", Large Burger";
        String expected = "\"Chef's \"\"Special\"\", Large Burger\"";
        assertEquals(expected, CsvUtils.formatField(input));
    }

    @Test
    @DisplayName("Should format a full record line correctly")
    void shouldFormatFullRecord() {
        List<String> record = List.of("M001", "Margherita, Cheese", "Pizza", "8.99");
        assertEquals("M001,\"Margherita, Cheese\",Pizza,8.99", CsvUtils.formatRecord(record));
    }

    @Test
    @DisplayName("Should parse standard records")
    void shouldParseStandardRecords() throws IOException {
        String csv = "id,name,price\nM001,Burger,5.99\nM002,Fries,2.49\n";
        List<List<String>> records = CsvUtils.readRecords(new StringReader(csv));

        assertEquals(3, records.size());
        assertEquals(List.of("id", "name", "price"), records.get(0));
        assertEquals(List.of("M001", "Burger", "5.99"), records.get(1));
        assertEquals(List.of("M002", "Fries", "2.49"), records.get(2));
    }

    @Test
    @DisplayName("Should parse quoted fields with commas and escaped quotes")
    void shouldParseQuotedFields() throws IOException {
        String csv = "M001,\"Chef's \"\"Special\"\", Large Burger\",12.50\n";
        List<List<String>> records = CsvUtils.readRecords(new StringReader(csv));

        assertEquals(1, records.size());
        List<String> row = records.get(0);
        assertEquals("M001", row.get(0));
        assertEquals("Chef's \"Special\", Large Burger", row.get(1));
        assertEquals("12.50", row.get(2));
    }

    @Test
    @DisplayName("Should parse empty and null fields")
    void shouldParseEmptyFields() throws IOException {
        String csv = "A,,C\n,B,\n";
        List<List<String>> records = CsvUtils.readRecords(new StringReader(csv));

        assertEquals(2, records.size());
        assertEquals(List.of("A", "", "C"), records.get(0));
        assertEquals(List.of("", "B", ""), records.get(1));
    }

    @Test
    @DisplayName("Should handle Unicode characters")
    void shouldHandleUnicode() throws IOException {
        String csv = "M001,₹100 Spicy Tacos 🌮,100.00\n";
        List<List<String>> records = CsvUtils.readRecords(new StringReader(csv));

        assertEquals(1, records.size());
        assertEquals("₹100 Spicy Tacos 🌮", records.get(0).get(1));
    }

    @Test
    @DisplayName("Should handle embedded newlines in quoted fields")
    void shouldHandleEmbeddedNewlines() throws IOException {
        String csv = "M001,\"Line 1\nLine 2\",10.00\n";
        List<List<String>> records = CsvUtils.readRecords(new StringReader(csv));

        assertEquals(1, records.size());
        assertEquals("Line 1\nLine 2", records.get(0).get(1));
    }

    @Test
    @DisplayName("Should throw PersistenceException on unclosed quote")
    void shouldThrowOnUnclosedQuote() {
        String csv = "M001,\"Unclosed quote,10.00\n";
        assertThrows(PersistenceException.class,
                () -> CsvUtils.readRecords(new StringReader(csv)));
    }

    @Test
    @DisplayName("Should safely and atomically write to file")
    void shouldSafelyAndAtomicallyWriteToFile(@TempDir Path tempDir) {
        Path targetFile = tempDir.resolve("sub").resolve("test.csv");
        List<String> header = List.of("id", "name");
        List<List<String>> rows = List.of(
                List.of("1", "Alpha"),
                List.of("2", "Beta, Gamma")
        );

        CsvUtils.writeRecordsSafely(targetFile, header, rows);

        assertTrue(Files.exists(targetFile));
        assertDoesNotThrow(() -> {
            List<List<String>> read = CsvUtils.readRecords(Files.newBufferedReader(targetFile));
            assertEquals(3, read.size());
            assertEquals(List.of("id", "name"), read.get(0));
            assertEquals(List.of("1", "Alpha"), read.get(1));
            assertEquals(List.of("2", "Beta, Gamma"), read.get(2));
        });
    }
}
