package com.foodordering.console;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Console Input Parsing & Validation Tests")
class ConsoleInputTest {

    private ConsoleInput createInput(String simulatedInput, ByteArrayOutputStream outCapture) {
        InputStream in = new ByteArrayInputStream(simulatedInput.getBytes(StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(outCapture);
        return new ConsoleInput(in, out);
    }

    @Test
    @DisplayName("Should read standard lines and trim whitespace")
    void shouldReadLine() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleInput input = createInput("   Hello World   \n", out);

        String result = input.readLine("Prompt: ");
        assertEquals("Hello World", result);
        assertTrue(out.toString().contains("Prompt: "));
    }

    @Test
    @DisplayName("Should loop until non-blank input is supplied for readRequiredLine")
    void shouldReadRequiredLine() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleInput input = createInput("\n   \nActual Value\n", out);

        String result = input.readRequiredLine("Prompt: ");
        assertEquals("Actual Value", result);
        assertTrue(out.toString().contains("[Error] Value cannot be blank."));
    }

    @Test
    @DisplayName("Should recover from malformed numbers and parse valid integer")
    void shouldRecoverFromMalformedInt() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleInput input = createInput("invalid_num\n42\n", out);

        int result = input.readInt("Enter number: ");
        assertEquals(42, result);
        assertTrue(out.toString().contains("[Error] Please enter a valid integer number."));
    }

    @Test
    @DisplayName("Should enforce positive integer rule (> 0)")
    void shouldEnforcePositiveInt() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleInput input = createInput("-5\n0\n10\n", out);

        int result = input.readPositiveInt("Enter positive int: ");
        assertEquals(10, result);
        assertTrue(out.toString().contains("[Error] Value must be greater than zero."));
    }

    @Test
    @DisplayName("Should enforce integer range bounds [min, max]")
    void shouldEnforceRange() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleInput input = createInput("0\n15\n3\n", out);

        int result = input.readIntInRange("Select [1-5]: ", 1, 5);
        assertEquals(3, result);
        assertTrue(out.toString().contains("[Error] Please enter an option between 1 and 5."));
    }

    @Test
    @DisplayName("Should parse valid BigDecimal and enforce non-negative constraint")
    void shouldParseBigDecimal() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleInput input = createInput("not_a_money\n-12.50\n25.75\n", out);

        BigDecimal result = input.readNonNegativeBigDecimal("Enter price: ");
        assertEquals(new BigDecimal("25.75"), result);
        assertTrue(out.toString().contains("[Error] Please enter a valid decimal number"));
        assertTrue(out.toString().contains("[Error] Amount cannot be negative."));
    }

    @Test
    @DisplayName("Should parse boolean inputs (y/n)")
    void shouldParseBoolean() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleInput input = createInput("bad\nyes\nno\n", out);

        assertTrue(input.readBoolean("Confirm? "));
        assertFalse(input.readBoolean("Confirm? "));
        assertTrue(out.toString().contains("[Error] Please enter 'y' for yes or 'n' for no."));
    }

    @Test
    @DisplayName("Should throw IllegalStateException on stream termination (EOF)")
    void shouldHandleEofGracefully() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleInput input = createInput("", out); // Empty stream

        assertThrows(IllegalStateException.class, () -> input.readLine("Prompt: "));
    }
}
