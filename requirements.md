# Food Ordering System — Requirements

## Overview

A console-based Food Ordering System implemented in Java as a college OOP project.
Users interact through a text menu in the terminal. All data is persisted using CSV/text files.

---

## Technical Stack

| Concern           | Choice              |
|-------------------|---------------------|
| Language          | Java 21             |
| Build tool        | Maven               |
| Containerization  | Docker / Docker Compose |
| Storage           | CSV / plain-text files |
| Testing           | JUnit 5             |

---

## Roles

| Role     | Description                                                      |
|----------|------------------------------------------------------------------|
| Customer | Browses the menu, searches for food, manages a cart, and places orders. |
| Admin    | Manages menu items, views all orders, and generates reports.     |

---

## Planned Features

### Customer
- Browse the full menu with categories and prices.
- Search for food items by name or category.
- Add, update, and remove items from the cart.
- Apply discount codes/coupons at checkout.
- Simulated payment processing (cash / card — no real gateway).
- View order history and download/view invoices.

### Admin
- Add, edit, and delete menu items.
- View and update order statuses.
- Manage discount codes.
- View sales reports from persisted data.

### General
- User authentication (login/register) with credentials stored in a text file.
- All orders, menu items, and user data persisted across sessions via CSV files.
- Invoice generation saved as text files.

---

## OOP Requirements

| Concept              | How it will be applied                                           |
|----------------------|------------------------------------------------------------------|
| Encapsulation        | Private fields with getters/setters in all model classes.        |
| Inheritance          | `User` base class extended by `Customer` and `Admin`.            |
| Abstraction          | Abstract classes for shared behaviour (e.g., `AbstractOrder`).   |
| Polymorphism         | Method overriding for role-specific behaviours.                  |
| Interfaces           | Defined contracts for `Payable`, `Discountable`, `Printable`, etc. |
| Collections          | `List`, `Map`, and `Set` from the Java Collections Framework.    |
| Exception Handling   | Custom exceptions for invalid items, failed payments, etc.       |
| File Handling        | `BufferedReader`/`BufferedWriter` for CSV read/write operations. |

---

## Scope Limitations

- **No GUI** — terminal/console interaction only.
- **No database** — all storage is flat CSV/text files.
- **No real payment gateway** — payment is simulated.
- **No network communication** — fully offline application.

---

## Project Structure (planned)

```
food-ordering-system/
├── src/
│   ├── main/java/com/foodordering/
│   │   ├── Main.java
│   │   ├── model/          # Data classes (User, Customer, Admin, MenuItem, Order, …)
│   │   ├── service/        # Business logic (OrderService, MenuService, …)
│   │   ├── repository/     # CSV file read/write
│   │   ├── ui/             # Console menus and input handling
│   │   ├── exception/      # Custom exceptions
│   │   └── util/           # Helpers (validators, formatters, …)
│   └── test/java/com/foodordering/
│       └── …               # JUnit 5 tests
├── data/                   # CSV data files (created at runtime)
├── invoices/               # Generated invoice text files
├── requirements.md
├── pom.xml
├── Dockerfile
├── compose.yaml
└── .dockerignore
```
