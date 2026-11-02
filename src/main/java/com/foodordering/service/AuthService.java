package com.foodordering.service;

import com.foodordering.exception.AuthenticationException;
import com.foodordering.exception.DuplicateItemException;
import com.foodordering.exception.InvalidInputException;
import com.foodordering.model.Admin;
import com.foodordering.model.Customer;
import com.foodordering.model.User;
import com.foodordering.repository.UserRepository;
import com.foodordering.util.PasswordHasher;

import java.util.Optional;

/**
 * Service orchestrating user registration, authentication, password verification,
 * and secure first-run administrator provisioning.
 *
 * <p>Security &amp; Invariants:
 * <ul>
 *   <li>Passwords are hashed using {@link PasswordHasher} (PBKDF2WithHmacSHA256 with 65,536 iterations).</li>
 *   <li>No hardcoded default passwords or access codes are stored in the application.</li>
 *   <li>Public registration is strictly restricted to Customers; Administrator provisioning requires
 *       interactive first-run setup or explicit environment/system-property configuration.</li>
 *   <li>Authentication failures emit generic, non-sensitive messages to prevent username enumeration.</li>
 * </ul>
 * </p>
 */
public class AuthService {

    // Environment and System Property keys for optional non-interactive admin provisioning
    public static final String ENV_ADMIN_ID = "FOOD_ORDERING_ADMIN_ID";
    public static final String ENV_ADMIN_NAME = "FOOD_ORDERING_ADMIN_NAME";
    public static final String ENV_ADMIN_EMAIL = "FOOD_ORDERING_ADMIN_EMAIL";
    public static final String ENV_ADMIN_PASSWORD = "FOOD_ORDERING_ADMIN_PASSWORD";
    public static final String ENV_ADMIN_CODE = "FOOD_ORDERING_ADMIN_CODE";

    public static final String PROP_ADMIN_ID = "foodordering.admin.id";
    public static final String PROP_ADMIN_NAME = "foodordering.admin.name";
    public static final String PROP_ADMIN_EMAIL = "foodordering.admin.email";
    public static final String PROP_ADMIN_PASSWORD = "foodordering.admin.password";
    public static final String PROP_ADMIN_CODE = "foodordering.admin.code";

    private final UserRepository userRepository;

    /**
     * Constructs an AuthService backed by the specified repository.
     *
     * @param userRepository user repository; must not be null
     */
    public AuthService(UserRepository userRepository) {
        if (userRepository == null) {
            throw new InvalidInputException("UserRepository must not be null.");
        }
        this.userRepository = userRepository;
    }

    /**
     * Returns whether at least one administrator account exists in persistent storage.
     *
     * @return {@code true} if an administrator is registered, {@code false} otherwise
     */
    public boolean hasAdministrator() {
        return !userRepository.findAdmins().isEmpty();
    }

    /**
     * Provisions a new administrator account with full input validation and secure PBKDF2 hashing.
     *
     * @param userId        unique admin ID (no whitespace)
     * @param name          admin display name
     * @param email         valid email address
     * @param plainPassword plain text password (minimum 8 characters)
     * @param adminCode     internal admin security access code (minimum 4 characters)
     * @return the created and persisted {@link Admin}
     * @throws InvalidInputException  if fields fail validation
     * @throws DuplicateItemException if user ID or email is already taken
     */
    public Admin registerAdmin(String userId, String name, String email,
                              String plainPassword, String adminCode) {
        validateAdminFields(userId, name, email, plainPassword, adminCode);

        String trimmedId = userId.strip();
        String trimmedName = name.strip();
        String trimmedEmail = email.strip().toLowerCase();
        String trimmedCode = adminCode.strip();

        if (userRepository.existsById(trimmedId)) {
            throw new DuplicateItemException("User ID '" + trimmedId + "' is already registered.");
        }
        if (userRepository.existsByEmail(trimmedEmail)) {
            throw new DuplicateItemException("Email address '" + trimmedEmail + "' is already registered.");
        }

        String passwordHash = PasswordHasher.hash(plainPassword);
        Admin admin = new Admin(trimmedId, trimmedName, trimmedEmail, passwordHash, trimmedCode);
        userRepository.save(admin);
        return admin;
    }

    /**
     * Attempts to bootstrap an administrator from environment variables or system properties.
     * Useful for containerized, CI/CD, or automated deployment scenarios without embedding secrets.
     *
     * @return optional containing the provisioned {@link Admin} if configuration was present and applied
     */
    public Optional<Admin> bootstrapAdminFromConfig() {
        if (hasAdministrator()) {
            return Optional.empty(); // Existing admin accounts must never be overwritten
        }

        String id = resolveConfig(PROP_ADMIN_ID, ENV_ADMIN_ID);
        String name = resolveConfig(PROP_ADMIN_NAME, ENV_ADMIN_NAME);
        String email = resolveConfig(PROP_ADMIN_EMAIL, ENV_ADMIN_EMAIL);
        String password = resolveConfig(PROP_ADMIN_PASSWORD, ENV_ADMIN_PASSWORD);
        String code = resolveConfig(PROP_ADMIN_CODE, ENV_ADMIN_CODE);

        if (id != null && name != null && email != null && password != null && code != null) {
            Admin admin = registerAdmin(id, name, email, password, code);
            return Optional.of(admin);
        }

        return Optional.empty();
    }

    /**
     * Registers a new customer account.
     *
     * @param userId          unique user ID (alphanumeric, no whitespace)
     * @param name            customer display name
     * @param email           valid email address
     * @param plainPassword   plain text password (minimum 6 characters)
     * @param deliveryAddress customer physical delivery address (optional)
     * @return the registered {@link Customer}
     * @throws InvalidInputException  if fields fail validation
     * @throws DuplicateItemException if user ID or email is already registered
     */
    public Customer registerCustomer(String userId, String name, String email,
                                     String plainPassword, String deliveryAddress) {
        validateRegistrationFields(userId, name, email, plainPassword);

        String trimmedId = userId.strip();
        String trimmedName = name.strip();
        String trimmedEmail = email.strip().toLowerCase();
        String trimmedAddr = (deliveryAddress != null && !deliveryAddress.isBlank())
                ? deliveryAddress.strip() : null;

        if (userRepository.existsById(trimmedId)) {
            throw new DuplicateItemException("User ID '" + trimmedId + "' is already registered.");
        }
        if (userRepository.existsByEmail(trimmedEmail)) {
            throw new DuplicateItemException("Email address '" + trimmedEmail + "' is already registered.");
        }

        String passwordHash = PasswordHasher.hash(plainPassword);
        Customer customer = new Customer(trimmedId, trimmedName, trimmedEmail, passwordHash, trimmedAddr);
        userRepository.save(customer);
        return customer;
    }

    /**
     * Authenticates a customer by user ID or email and password.
     *
     * @param identifier    user ID or email
     * @param plainPassword candidate plain password
     * @return the authenticated {@link Customer}
     * @throws AuthenticationException if authentication fails or user is not a customer
     */
    public Customer loginCustomer(String identifier, String plainPassword) {
        if (identifier == null || identifier.isBlank() || plainPassword == null || plainPassword.isBlank()) {
            throw new AuthenticationException("Username and password are required.");
        }

        User user = findUserByIdentifier(identifier.strip())
                .orElseThrow(() -> new AuthenticationException("Invalid username or password."));

        if (!(user instanceof Customer customer)) {
            throw new AuthenticationException("Invalid username or password.");
        }

        if (!PasswordHasher.verify(plainPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }

        return customer;
    }

    /**
     * Authenticates an administrator by user ID or email, password, and security admin code.
     *
     * @param identifier    admin user ID or email
     * @param plainPassword admin plain password
     * @param adminCode     internal admin security code
     * @return the authenticated {@link Admin}
     * @throws AuthenticationException if authentication fails or access code does not match
     */
    public Admin loginAdmin(String identifier, String plainPassword, String adminCode) {
        if (identifier == null || identifier.isBlank()
                || plainPassword == null || plainPassword.isBlank()
                || adminCode == null || adminCode.isBlank()) {
            throw new AuthenticationException("Username, password, and administrator code are required.");
        }

        User user = findUserByIdentifier(identifier.strip())
                .orElseThrow(() -> new AuthenticationException("Invalid administrator credentials."));

        if (!(user instanceof Admin admin)) {
            throw new AuthenticationException("Access denied: account does not have administrator privileges.");
        }

        if (!PasswordHasher.verify(plainPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid administrator credentials.");
        }

        if (!admin.getAdminCode().equals(adminCode.strip())) {
            throw new AuthenticationException("Invalid administrator access code.");
        }

        return admin;
    }

    private Optional<User> findUserByIdentifier(String identifier) {
        Optional<User> byId = userRepository.findById(identifier);
        if (byId.isPresent()) {
            return byId;
        }
        return userRepository.findByEmail(identifier);
    }

    private void validateRegistrationFields(String userId, String name, String email, String password) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidInputException("User ID must not be null or blank.");
        }
        if (userId.contains(" ")) {
            throw new InvalidInputException("User ID must not contain whitespace.");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Display name must not be null or blank.");
        }
        if (email == null || email.isBlank() || !email.contains("@") || !email.contains(".")) {
            throw new InvalidInputException("A valid email address is required (e.g. user@example.com).");
        }
        if (password == null || password.length() < 6) {
            throw new InvalidInputException("Password must be at least 6 characters in length.");
        }
    }

    private void validateAdminFields(String userId, String name, String email, String password, String code) {
        validateRegistrationFields(userId, name, email, password);
        if (password.length() < 8) {
            throw new InvalidInputException("Administrator password must be at least 8 characters in length.");
        }
        if (code == null || code.isBlank() || code.strip().length() < 4) {
            throw new InvalidInputException("Administrator security code must be at least 4 characters.");
        }
    }

    private String resolveConfig(String propKey, String envKey) {
        String prop = System.getProperty(propKey);
        if (prop != null && !prop.isBlank()) {
            return prop.strip();
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.strip();
        }
        return null;
    }
}
