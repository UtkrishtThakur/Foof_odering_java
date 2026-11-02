# Project Viva Presentation Script
## Java 21 Food Ordering System — Spoken Defense & Demonstration Guide

> **Tone:** Confident, articulate, professional, and grounded in real source code.  
> **Target Audience:** External Examiner, Viva Voce Committee, or Laboratory Professor.  
> **Estimated Delivery Time:** 8 to 10 minutes (with optional pause points for teacher questions).

---

## 1. 30-Second Project Introduction

> *"Good morning, Professor / Examiners.*
>
> *Today I am presenting our **Food Ordering and Restaurant Management System**, developed in modern **Java 21 LTS** with **Apache Maven**, **JUnit 5**, and **Docker containerization**.*
>
> *The goal of this project is to model the complete commercial lifecycle of a restaurant platform—including multi-role authentication for Customers and Administrators, dynamic menu catalog management, real-time stock guards, shopping cart accumulation, strategy-based discount pricing, polymorphic payment simulations, and RFC 4180 CSV persistence.*
>
> *Rather than relying on heavy web frameworks or external SQL servers, our primary objective was to demonstrate **pure, high-standard Object-Oriented Programming**, clean layered architecture, immutability, and enterprise-grade financial arithmetic using `BigDecimal`."*

---

## 2. 2-Minute Architecture Explanation

> *"Let me walk you through the system architecture.*
>
> *To prevent the common mistake of placing validation, file operations, and UI code in a single monolithic file, our codebase is organized into four strictly decoupled horizontal layers:*
>
> 1. *First, the **Presentation Layer** (`com.foodordering.console`): Handled by `ConsoleApplication`, `CustomerMenu`, and `AdminMenu`. It uses a custom input wrapper called `ConsoleInput` to handle keyboard input, range validation, and graceful stream termination without throwing unhandled exceptions.*
> 2. *Second, the **Business Service Layer** (`com.foodordering.service`): Represented by `MenuService`, `CartService`, `OrderService`, and `AuthService`. This layer is the brain of the application. It owns all business rules, invariants, transactional compensation, and lifecycle state machines.*
> 3. *Third, the **Repository Layer** (`com.foodordering.repository`): Classes like `MenuRepository`, `OrderRepository`, and `UserRepository` maintain in-memory `LinkedHashMap` stores for fast $O(1)$ lookups and insertion order preservation. They also manage in-memory rollback: if saving to disk fails, in-memory state is immediately restored.*
> 4. *Fourth, the **Persistence Layer** (`com.foodordering.persistence`): Classes like `CsvOrderStorage`, `CsvMenuStorage`, and `CsvUserStorage` serialize records using our utility `CsvUtils`, which follows RFC 4180 rules for commas, quotes, and newlines, and uses temporary files with atomic filesystem replacements to prevent file corruption.*
>
> *Connecting all these tiers are our **Domain Models** (`com.foodordering.model`), which encapsulate core business entities such as `MenuItem`, `Cart`, `Order`, and user hierarchies."*

---

## 3. 3–5 Minute Detailed OOP Concepts Walkthrough

> *"Now I would like to highlight how the **Four Pillars of Object-Oriented Programming** are implemented in our actual source code.*

### A. Encapsulation
> *In our domain models, all member fields are marked `private`. We do not expose public fields or naive setters.  
> For instance, in `MenuItem.java`, stock is updated strictly through guarded domain methods like `reduceStock(int amount)`. This method validates that the requested amount is positive and verifies that `amount <= this.stockQuantity` before decrementing. If a caller requests more items than available, it throws an `IllegalStateException`, completely preventing negative inventory.  
> Furthermore, in `Order.java`, historical receipts are made immutable using defensive copying: `this.items = Collections.unmodifiableList(new ArrayList<>(items));`. Any attempt by outside code to modify the order's item list throws an `UnsupportedOperationException`.*

### B. Abstraction
> *We use both abstract classes and interfaces to hide internal complexities behind clean contracts:  
> 1. `Discountable` is an interface with a single method: `BigDecimal applyDiscount(BigDecimal amount)`. The calling `OrderService` knows what the method does, but doesn't need to know how percentage division or flat deduction calculations work under the hood.  
> 2. `Payment` is an abstract base class. It encapsulates shared monetary state, status flags, and helper methods like `isSuccessful()`, while declaring the abstract contract `public abstract PaymentStatus processPayment()`, leaving the simulation outcome to concrete subclasses.*

### C. Inheritance
> *We have two major inheritance hierarchies:  
> 1. `Customer` and `Admin` extend the abstract class `User`. `User` holds shared identity attributes like `userId`, `name`, `email`, and `passwordHash`. Subclasses call `super(userId, name, email, passwordHash)` to run base validation, and then override the abstract method `getRole()` to return `"CUSTOMER"` or `"ADMIN"`.  
> 2. `CashPayment` and `OnlinePayment` extend `Payment`. They inherit the monetary amount and lifecycle status while specializing transaction processing.  
> All our custom domain exceptions (`InvalidInputException`, `InsufficientStockException`, etc.) inherit from `RuntimeException`, forming a clean, unchecked exception hierarchy.*

### D. Polymorphism
> *We demonstrate both runtime and compile-time polymorphism:  
> * **Runtime Polymorphism:** During checkout in `OrderService`, we execute:  
>   `PaymentStatus paymentResult = payment.processPayment();`  
>   At compile time, `payment` is treated as the abstract base class `Payment`. At runtime, the JVM uses **Dynamic Method Dispatch** to invoke either `CashPayment.processPayment()` (which immediately marks the payment as `PAID`) or `OnlinePayment.processPayment()` (which evaluates the simulated approval flag and generates a transaction reference).  
> * **Compile-Time Polymorphism:** We use constructor and method overloading. For example, `PercentageDiscount` provides three overloaded constructors taking `BigDecimal`, `int`, and `String`. Similarly, `OrderService` overloads `placeOrder` to accept either a concrete `Payment` instance or a functional payment factory `Function<BigDecimal, Payment>`."*

---

## 4. Description of One Complete Customer Checkout

> *"Let me trace what happens when a customer checks out an order:*
>
> 1. *The customer initiates checkout from `CustomerMenu`. They select a 10% discount and choose simulated online payment.*
> 2. *`CustomerMenu` passes the customer, cart, `PercentageDiscount`, and `OnlinePayment` instances to `OrderService.placeOrder()`.*
> 3. *`OrderService` first re-checks every cart item against the live `MenuService` to guarantee the dish is still active and in stock. It rejects stale cart items.*
> 4. *It takes an **immutable historical snapshot** by creating `OrderItem` instances using the live menu price ($8.99) and quantity. This completely uncouples the order from future menu price changes.*
> 5. *It applies the discount strategy: the ₹1,000 cart subtotal is reduced to ₹900.*
> 6. *It calls `payment.processPayment()`. Notice that we process payment **before** touching inventory. If payment is declined, `OrderService` throws a `PaymentFailedException`, preserving both customer cart contents and restaurant inventory.*
> 7. *Once payment succeeds, `OrderService` deducts inventory stock via `MenuService.reduceStock()` for each line item. If a stock deduction fails mid-flight, an internal compensation mechanism (`rollbackInventory`) automatically restocks previously deducted items.*
> 8. *Finally, `OrderService` instantiates the `Order` object with status `CONFIRMED` and `PAID`, persists it to `orders.csv` and `order_items.csv` through `OrderRepository`, and clears the customer's cart.*
> 9. *An itemized receipt with a unique order ID (e.g. `ORD-A5C7AAD4`) and transaction reference is printed to the console."*

---

## 5. The Most Important Classes to Know

> *"If you look at the structure of our project, five classes represent the core design:*
>
> 1. *`OrderService`: The central transactional orchestrator managing validations, payments, stock deductions, and lifecycle transitions.*
> 2. *`OrderItem`: An immutable snapshot entity representing purchased items, ensuring historical audit integrity.*
> 3. *`MenuItem`: The catalog domain model encapsulating price invariants and stock decrement rules.*
> 4. *`PasswordHasher`: A security utility using standard Java cryptography (`PBKDF2WithHmacSHA256`, 16-byte random salts, 65,536 iterations, constant-time verification) to secure credentials.*
> 5. *`CsvUtils`: An RFC 4180-compliant engine that writes to temporary files first and performs atomic filesystem moves (`ATOMIC_MOVE`) to prevent data corruption during unexpected system failures."*

---

## 6. Three Technical Challenges and How We Solved Them

> *"During the development and testing of this system, we solved three major technical engineering challenges:
>
> 1. **Financial Precision vs. Floating-Point Inaccuracies:**  
>    *Problem:* Using `double` or `float` introduces binary floating-point representation errors (e.g., $0.1 + 0.2 = 0.30000000000000004$), which corrupts customer invoices.  
>    *Solution:* We enforced `BigDecimal` across the entire codebase with explicit scale of 2 and `RoundingMode.HALF_UP`. We constructed decimals from strings and used `.compareTo()` for accurate equality checks.
>
> 2. **Receipt Immutability vs. Catalog Price Changes:**  
>    *Problem:* If orders held live references to `MenuItem`, a price increase next month would retroactively change historical order totals and past tax receipts.  
>    *Solution:* We introduced the **Snapshot Pattern** via `OrderItem`. At checkout, we extract the live name and price into an immutable value object with `final` fields and no setters.
>
> 3. **Constructor Overload Ambiguity in Test Suites:**  
>    *Problem:* In `PaymentTest.java`, invoking `new OnlinePayment(null, true)` caused a compiler error because `OnlinePayment` has two overloaded constructors: `(BigDecimal, boolean)` and `(String, boolean)`. The compiler could not decide which constructor to call with an untyped `null`.  
>    *Solution:* We resolved the ambiguity without weakening tests by explicitly casting the argument: `new OnlinePayment((BigDecimal) null, true)` and adding a separate test for `new OnlinePayment((String) null, true)`."*

---

## 7. Known Limitations and Future Improvements

> *"To be completely transparent about our architecture, we identified two realistic engineering boundaries:
>
> 1. **File-Level Atomicity vs. Multi-Table ACID Transactions:**  
>    While our custom CSV storage uses `StandardCopyOption.ATOMIC_MOVE` to prevent individual file corruption, it cannot guarantee distributed multi-file transactions across `orders.csv` and `order_items.csv` simultaneously if power is abruptly lost between writes. In a commercial enterprise release, we would replace CSV persistence with a relational database like PostgreSQL using Spring Data JPA.
>
> 2. **Simulated Payments vs. Real Gateways:**  
>    Our online payment engine is simulated. This was an intentional academic choice to demonstrate polymorphism and deterministic unit testing without needing real banking API keys or network dependencies. In production, we would add a `StripePayment` or `RazorpayPayment` implementation of our `Payment` abstraction."*

---

## 8. Confident Closing Statement

> *"To conclude, our Food Ordering System is not just a console script; it is a carefully engineered Java application that demonstrates how clean Object-Oriented principles, domain modeling, and defensive programming solve real-world restaurant management challenges.
>
> All 134 automated unit and integration tests pass successfully, the application builds cleanly into a lightweight Docker image, and the system is fully verifiable.
>
> Thank you, Professor. I am now ready for your questions."*
