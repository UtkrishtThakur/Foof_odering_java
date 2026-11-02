package com.foodordering.console;

import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Robust console input reader that safely handles line parsing, numeric formatting,
 * range validation, and input stream termination.
 */
public class ConsoleInput {

    private final Scanner scanner;
    private final PrintStream out;

    public ConsoleInput() {
        this(System.in, System.out);
    }

    public ConsoleInput(InputStream in, PrintStream out) {
        this.scanner = new Scanner(in);
        this.out = (out != null) ? out : System.out;
    }

    /**
     * Reads a line of text, trimmed.
     *
     * @param prompt prompt message to display
     * @return entered line or empty string
     */
    public String readLine(String prompt) {
        out.print(prompt);
        try {
            if (!scanner.hasNextLine()) {
                throw new IllegalStateException("End of console input stream reached.");
            }
            return scanner.nextLine().trim();
        } catch (NoSuchElementException e) {
            throw new IllegalStateException("End of console input stream reached.", e);
        }
    }

    /**
     * Reads a mandatory non-blank line of text.
     *
     * @param prompt prompt message
     * @return non-blank trimmed text
     */
    public String readRequiredLine(String prompt) {
        while (true) {
            String line = readLine(prompt);
            if (!line.isBlank()) {
                return line;
            }
            out.println("  [Error] Value cannot be blank. Please try again.");
        }
    }

    /**
     * Reads a valid integer.
     *
     * @param prompt prompt message
     * @return parsed integer
     */
    public int readInt(String prompt) {
        while (true) {
            String line = readRequiredLine(prompt);
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                out.println("  [Error] Please enter a valid integer number.");
            }
        }
    }

    /**
     * Reads a strictly positive integer (> 0).
     *
     * @param prompt prompt message
     * @return positive integer
     */
    public int readPositiveInt(String prompt) {
        while (true) {
            int value = readInt(prompt);
            if (value > 0) {
                return value;
            }
            out.println("  [Error] Value must be greater than zero.");
        }
    }

    /**
     * Reads an integer within the specified inclusive range.
     *
     * @param prompt prompt message
     * @param min    minimum permitted value
     * @param max    maximum permitted value
     * @return selected integer in range [min, max]
     */
    public int readIntInRange(String prompt, int min, int max) {
        while (true) {
            int val = readInt(prompt);
            if (val >= min && val <= max) {
                return val;
            }
            out.printf("  [Error] Please enter an option between %d and %d.%n", min, max);
        }
    }

    /**
     * Reads a monetary BigDecimal value.
     *
     * @param prompt prompt message
     * @return parsed BigDecimal
     */
    public BigDecimal readBigDecimal(String prompt) {
        while (true) {
            String line = readRequiredLine(prompt);
            try {
                return new BigDecimal(line);
            } catch (NumberFormatException e) {
                out.println("  [Error] Please enter a valid decimal number (e.g. 12.50).");
            }
        }
    }

    /**
     * Reads a non-negative BigDecimal value (>= 0).
     *
     * @param prompt prompt message
     * @return non-negative BigDecimal
     */
    public BigDecimal readNonNegativeBigDecimal(String prompt) {
        while (true) {
            BigDecimal val = readBigDecimal(prompt);
            if (val.compareTo(BigDecimal.ZERO) >= 0) {
                return val;
            }
            out.println("  [Error] Amount cannot be negative.");
        }
    }

    /**
     * Reads a boolean response (y/n, yes/no, true/false).
     *
     * @param prompt prompt message
     * @return boolean value
     */
    public boolean readBoolean(String prompt) {
        while (true) {
            String line = readRequiredLine(prompt).toLowerCase();
            if (line.equals("y") || line.equals("yes") || line.equals("true") || line.equals("1")) {
                return true;
            }
            if (line.equals("n") || line.equals("no") || line.equals("false") || line.equals("0")) {
                return false;
            }
            out.println("  [Error] Please enter 'y' for yes or 'n' for no.");
        }
    }
}
