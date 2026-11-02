package com.foodordering.service;

import com.foodordering.exception.AuthenticationException;
import com.foodordering.exception.DuplicateItemException;
import com.foodordering.exception.InvalidInputException;
import com.foodordering.model.Admin;
import com.foodordering.model.Customer;
import com.foodordering.repository.UserRepository;
import com.foodordering.util.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Authentication & User Management Tests")
class AuthServiceTest {

    private UserRepository userRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository();
        authService = new AuthService(userRepository);
    }

    @Nested
    @DisplayName("Admin Provisioning & Configuration Tests")
    class AdminProvisioningTests {

        @Test
        @DisplayName("Should detect when no administrator exists")
        void shouldDetectNoAdminInitially() {
            assertFalse(authService.hasAdministrator());
            assertTrue(userRepository.findAdmins().isEmpty());
        }

        @Test
        @DisplayName("Should securely provision administrator with PBKDF2 hash")
        void shouldProvisionAdminSuccessfully() {
            Admin admin = authService.registerAdmin(
                    "sysadmin", "System Lead", "lead@foodordering.com", "SuperSecurePassword123", "AUTH-9999");

            assertNotNull(admin);
            assertEquals("sysadmin", admin.getUserId());
            assertEquals("lead@foodordering.com", admin.getEmail());
            assertEquals("AUTH-9999", admin.getAdminCode());
            assertTrue(admin.getPasswordHash().startsWith("PBKDF2WithHmacSHA256:"));
            assertTrue(PasswordHasher.verify("SuperSecurePassword123", admin.getPasswordHash()));
            assertTrue(authService.hasAdministrator());
            assertEquals(1, userRepository.findAdmins().size());
        }

        @Test
        @DisplayName("Should reject admin registration with short password (< 8 chars) or weak access code (< 4 chars)")
        void shouldRejectWeakAdminCredentials() {
            assertThrows(InvalidInputException.class, () ->
                    authService.registerAdmin("admin1", "Admin", "a@test.com", "short", "CODE1"));
            assertThrows(InvalidInputException.class, () ->
                    authService.registerAdmin("admin1", "Admin", "a@test.com", "Password@123", "COD"));
        }

        @Test
        @DisplayName("Should reject duplicate admin user ID or email")
        void shouldRejectDuplicateAdmin() {
            authService.registerAdmin("admin1", "Admin One", "admin1@test.com", "Password@123", "CODE1");

            assertThrows(DuplicateItemException.class, () ->
                    authService.registerAdmin("admin1", "Admin Two", "admin2@test.com", "Password@123", "CODE2"));
            assertThrows(DuplicateItemException.class, () ->
                    authService.registerAdmin("admin2", "Admin Two", "admin1@test.com", "Password@123", "CODE2"));
        }

        @Test
        @DisplayName("Should bootstrap admin from system properties when configured")
        void shouldBootstrapFromConfig() {
            System.setProperty(AuthService.PROP_ADMIN_ID, "cfg_admin");
            System.setProperty(AuthService.PROP_ADMIN_NAME, "Configured Admin");
            System.setProperty(AuthService.PROP_ADMIN_EMAIL, "cfg@test.com");
            System.setProperty(AuthService.PROP_ADMIN_PASSWORD, "ConfigPass@123");
            System.setProperty(AuthService.PROP_ADMIN_CODE, "CFG-CODE-01");

            try {
                Optional<Admin> bootstrapped = authService.bootstrapAdminFromConfig();
                assertTrue(bootstrapped.isPresent());
                assertEquals("cfg_admin", bootstrapped.get().getUserId());
                assertTrue(authService.hasAdministrator());

                // Idempotency: second attempt should not re-create or overwrite
                Optional<Admin> secondAttempt = authService.bootstrapAdminFromConfig();
                assertTrue(secondAttempt.isEmpty());
                assertEquals(1, userRepository.findAdmins().size());
            } finally {
                System.clearProperty(AuthService.PROP_ADMIN_ID);
                System.clearProperty(AuthService.PROP_ADMIN_NAME);
                System.clearProperty(AuthService.PROP_ADMIN_EMAIL);
                System.clearProperty(AuthService.PROP_ADMIN_PASSWORD);
                System.clearProperty(AuthService.PROP_ADMIN_CODE);
            }
        }
    }

    @Nested
    @DisplayName("Customer Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Should register a new customer successfully with PBKDF2 salted hash")
        void shouldRegisterCustomer() {
            Customer customer = authService.registerCustomer(
                    "cust01", "John Doe", "john@example.com", "Secret123", "123 Main St");

            assertNotNull(customer);
            assertEquals("cust01", customer.getUserId());
            assertEquals("John Doe", customer.getName());
            assertEquals("john@example.com", customer.getEmail());
            assertEquals("123 Main St", customer.getDeliveryAddress());
            assertTrue(customer.getPasswordHash().startsWith("PBKDF2WithHmacSHA256:"));
            assertTrue(PasswordHasher.verify("Secret123", customer.getPasswordHash()));
            assertNotEquals("Secret123", customer.getPasswordHash()); // Never plain text
        }

        @Test
        @DisplayName("Should reject blank user ID, name, or invalid email")
        void shouldRejectInvalidFields() {
            assertThrows(InvalidInputException.class, () ->
                    authService.registerCustomer("", "John", "john@example.com", "Secret123", "Addr"));
            assertThrows(InvalidInputException.class, () ->
                    authService.registerCustomer("cust 01", "John", "john@example.com", "Secret123", "Addr"));
            assertThrows(InvalidInputException.class, () ->
                    authService.registerCustomer("cust01", "", "john@example.com", "Secret123", "Addr"));
            assertThrows(InvalidInputException.class, () ->
                    authService.registerCustomer("cust01", "John", "not-an-email", "Secret123", "Addr"));
            assertThrows(InvalidInputException.class, () ->
                    authService.registerCustomer("cust01", "John", "john@example.com", "12345", "Addr")); // < 6 chars
        }

        @Test
        @DisplayName("Should reject duplicate user ID or duplicate email")
        void shouldRejectDuplicates() {
            authService.registerCustomer("cust01", "John Doe", "john@example.com", "Secret123", null);

            // Duplicate ID
            assertThrows(DuplicateItemException.class, () ->
                    authService.registerCustomer("cust01", "Other Name", "other@example.com", "Secret123", null));

            // Duplicate Email
            assertThrows(DuplicateItemException.class, () ->
                    authService.registerCustomer("cust02", "Other Name", "john@example.com", "Secret123", null));
        }
    }

    @Nested
    @DisplayName("Customer Authentication Tests")
    class CustomerLoginTests {

        @BeforeEach
        void registerSample() {
            authService.registerCustomer("cust01", "Alice", "alice@example.com", "Pass@123", "Street 1");
        }

        @Test
        @DisplayName("Should log in with user ID or with email")
        void shouldLoginByIdOrEmail() {
            Customer byId = authService.loginCustomer("cust01", "Pass@123");
            assertNotNull(byId);
            assertEquals("cust01", byId.getUserId());

            Customer byEmail = authService.loginCustomer("alice@example.com", "Pass@123");
            assertNotNull(byEmail);
            assertEquals("cust01", byEmail.getUserId());
        }

        @Test
        @DisplayName("Should reject invalid password")
        void shouldRejectInvalidPassword() {
            assertThrows(AuthenticationException.class, () ->
                    authService.loginCustomer("cust01", "WrongPassword"));
        }

        @Test
        @DisplayName("Should reject unknown user identifier")
        void shouldRejectUnknownUser() {
            assertThrows(AuthenticationException.class, () ->
                    authService.loginCustomer("unknown_id", "Pass@123"));
        }

        @Test
        @DisplayName("Should reject customer login when account has admin role")
        void shouldRejectAdminLoggingInAsCustomer() {
            authService.registerAdmin("admin1", "Administrator", "admin@test.com", "AdminPass@123", "ADM-01");

            assertThrows(AuthenticationException.class, () ->
                    authService.loginCustomer("admin1", "AdminPass@123"));
        }
    }

    @Nested
    @DisplayName("Admin Authentication Tests")
    class AdminLoginTests {

        @BeforeEach
        void provisionAdmin() {
            authService.registerAdmin("admin_user", "SecAdmin", "admin@corp.com", "MasterPass@123", "KEY-7777");
        }

        @Test
        @DisplayName("Should log in admin with correct credentials and admin code")
        void shouldLoginAdminSuccessfully() {
            Admin admin = authService.loginAdmin("admin_user", "MasterPass@123", "KEY-7777");
            assertNotNull(admin);
            assertEquals("admin_user", admin.getUserId());
            assertEquals("KEY-7777", admin.getAdminCode());
        }

        @Test
        @DisplayName("Should reject wrong admin password")
        void shouldRejectWrongPassword() {
            assertThrows(AuthenticationException.class, () ->
                    authService.loginAdmin("admin_user", "WrongPass", "KEY-7777"));
        }

        @Test
        @DisplayName("Should reject wrong admin access code")
        void shouldRejectWrongAdminCode() {
            assertThrows(AuthenticationException.class, () ->
                    authService.loginAdmin("admin_user", "MasterPass@123", "WRONG-CODE"));
        }

        @Test
        @DisplayName("Should reject admin login when account is a customer")
        void shouldRejectCustomerLoggingInAsAdmin() {
            authService.registerCustomer("cust01", "Alice", "alice@example.com", "Pass@123", null);

            assertThrows(AuthenticationException.class, () ->
                    authService.loginAdmin("cust01", "Pass@123", "KEY-7777"));
        }
    }
}
