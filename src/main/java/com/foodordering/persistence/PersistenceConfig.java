package com.foodordering.persistence;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration manager for data directory paths and persistence options.
 *
 * <p>Directory resolution precedence:
 * <ol>
 *   <li>Java System Property: {@code foodordering.data.dir}</li>
 *   <li>Environment Variable: {@code FOOD_ORDERING_DATA_DIR}</li>
 *   <li>Default Location: {@code data} (resolved relative to working directory)</li>
 * </ol>
 * </p>
 */
public final class PersistenceConfig {

    public static final String PROP_DATA_DIR = "foodordering.data.dir";
    public static final String ENV_DATA_DIR = "FOOD_ORDERING_DATA_DIR";
    public static final String DEFAULT_DATA_DIR = "data";
    public static final String PROP_DEMO_SEED = "foodordering.demo.seed";

    private PersistenceConfig() {
        // utility class
    }

    /**
     * Resolves the active data storage directory according to configuration precedence.
     *
     * @return the resolved {@link Path}
     */
    public static Path resolveDataDir() {
        String sysProp = System.getProperty(PROP_DATA_DIR);
        if (sysProp != null && !sysProp.isBlank()) {
            return Paths.get(sysProp.strip());
        }

        String envVal = System.getenv(ENV_DATA_DIR);
        if (envVal != null && !envVal.isBlank()) {
            return Paths.get(envVal.strip());
        }

        return Paths.get(DEFAULT_DATA_DIR);
    }

    /**
     * Returns whether demo data seeding is explicitly requested via system property
     * {@code foodordering.demo.seed=true}.
     *
     * @return {@code true} if explicitly enabled, {@code false} otherwise
     */
    public static boolean isDemoSeedEnabled() {
        String prop = System.getProperty(PROP_DEMO_SEED);
        if (prop != null) {
            return Boolean.parseBoolean(prop.strip());
        }
        return false;
    }
}
