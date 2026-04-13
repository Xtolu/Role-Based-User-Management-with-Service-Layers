# Role-Based-User-Management-with-Service-Layers
A robust RBAC system built with java, featuring a decoupled service layer architecture for a scalable user management and secure authentication
# RBAC User Management Service

A **Role-Based Access Control (RBAC)** system implemented in pure Java 11 — no Spring, no Hibernate, no external runtime dependencies. It ships three fully integrated service layers: **User Management**, **Product**, and **Invoice**.

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Role Permission Matrix](#role-permission-matrix)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Running the Demo](#running-the-demo)
- [Running Tests](#running-tests)
- [Service API Reference](#service-api-reference)
- [Extending the Project](#extending-the-project)

---

## Features

- **Four built-in roles**: `ADMIN`, `MANAGER`, `SALES`, `VIEWER`
- **User lifecycle**: create, update, deactivate, reactivate, delete, authenticate
- **Product management**: CRUD, stock adjustment, soft/hard delete, keyword search
- **Invoice lifecycle**: draft → issued → paid, line-item stock reservation, cancellation with stock restore
- **Centralized `AuthGuard`**: every service method enforces role checks before any business logic runs
- **Salted SHA-256 password hashing** (drop-in replacement slot for BCrypt/Argon2)
- **Thread-safe in-memory repositories** backed by `ConcurrentHashMap`
- **Full JUnit 5 test suite** — unit tests for every service and repository

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        Main.java                        │  ← entry point / demo
└───────────────────┬─────────────────────────────────────┘
                    │ uses
          ┌─────────▼──────────┐
          │   ServiceFactory   │  ← manual DI / wiring
          └──┬──────┬──────────┘
             │      │
   ┌──────────▼─┐ ┌─▼───────────┐ ┌────────────────┐
   │ UserMgmt   │ │ Product     │ │ Invoice        │
   │ Service    │ │ Service     │ │ Service        │
   └──────┬─────┘ └──────┬──────┘ └───────┬────────┘
          │              │                │
   ┌──────▼──────────────▼────────────────▼────────┐
   │               AuthGuard                        │  ← role enforcement
   └───────────────────────────────────────────────┘
          │              │                │
   ┌──────▼─────┐ ┌──────▼──────┐ ┌──────▼──────┐
   │ UserRepo   │ │ ProductRepo │ │ InvoiceRepo │
   └────────────┘ └─────────────┘ └─────────────┘
          (ConcurrentHashMap — swap for JPA/JDBC)
```

### Package layout

| Package | Responsibility |
|---|---|
| `model` | Domain entities: `User`, `Product`, `Invoice`, `Role` |
| `dto` | Request/response data transfer objects |
| `repository` | In-memory CRUD stores |
| `service` | Business logic + RBAC enforcement |
| `config` | `AuthGuard`, `PasswordUtil`, `ServiceFactory` |
| `exception` | Typed exception hierarchy |

---

## Role Permission Matrix

| Action | ADMIN | MANAGER | SALES | VIEWER |
|---|:---:|:---:|:---:|:---:|
| Create / update / delete users | ✅ | ❌ | ❌ | ❌ |
| Deactivate / reactivate users | ✅ | ❌ | ❌ | ❌ |
| Create / update products | ✅ | ✅ | ❌ | ❌ |
| Adjust product stock | ✅ | ✅ | ❌ | ❌ |
| Hard-delete products | ✅ | ❌ | ❌ | ❌ |
| View products | ✅ | ✅ | ✅ | ✅ |
| Create invoices | ✅ | ✅ | ✅ | ❌ |
| Issue invoices | ✅ | ✅ | ✅ | ❌ |
| Mark invoices PAID | ✅ | ✅ | ❌ | ❌ |
| Cancel / delete invoices | ✅ | ❌ | ❌ | ❌ |
| View invoices | ✅ | ✅ | ✅ | ✅ |

---

## Project Structure

```
rbac-service/
├── pom.xml
├── .gitignore
├── README.md
└── src/
    ├── main/
    │   └── java/com/rbac/
    │       ├── Main.java
    │       ├── config/
    │       │   ├── AuthGuard.java
    │       │   ├── PasswordUtil.java
    │       │   └── ServiceFactory.java
    │       ├── dto/
    │       │   └── Dto.java
    │       ├── exception/
    │       │   ├── RbacException.java
    │       │   └── Exceptions.java
    │       ├── model/
    │       │   ├── Role.java
    │       │   ├── User.java
    │       │   ├── Product.java
    │       │   └── Invoice.java
    │       ├── repository/
    │       │   ├── UserRepository.java
    │       │   ├── ProductRepository.java
    │       │   └── InvoiceRepository.java
    │       └── service/
    │           ├── UserManagementService.java
    │           ├── ProductService.java
    │           └── InvoiceService.java
    └── test/
        └── java/com/rbac/
            ├── repository/
            │   └── UserRepositoryTest.java
            └── service/
                ├── UserManagementServiceTest.java
                ├── ProductServiceTest.java
                └── InvoiceServiceTest.java
```

---

## Getting Started

### Prerequisites

| Tool | Minimum version |
|---|---|
| JDK | 11 |
| Maven | 3.8 |

### Clone & build

```bash
git clone https://github.com/<your-username>/rbac-service.git
cd rbac-service
mvn package -q
```

This produces `target/rbac-user-management-1.0.0.jar` (executable fat-jar).

---

## Running the Demo

```bash
# via Maven
mvn compile exec:java -Dexec.mainClass="com.rbac.Main" -q

# or via the packaged jar
java -jar target/rbac-user-management-1.0.0.jar
```

The demo walks through the full lifecycle:

1. **Bootstrap** — seeds a root `ADMIN`
2. **Users** — ADMIN creates MANAGER / SALES / VIEWER users; unauthorized attempts are blocked
3. **Products** — MANAGER creates products; SALES is blocked from creating/modifying
4. **Invoices** — SALES builds a draft, adds line items (stock is reserved), issues, MANAGER marks PAID
5. **Teardown** — ADMIN cancels and deletes a draft invoice (stock is restored)

---

## Running Tests

```bash
mvn test
```

The test suite covers:

- Role-based access control for every service method
- Correct exception types on every violation
- Repository CRUD and query correctness
- Invoice state machine transitions
- Stock reservation and restoration on cancel
- Password hashing and authentication

---

## Service API Reference

### UserManagementService

```java
User   createUser(User actor, CreateUserRequest req)
User   getUserById(User actor, String userId)
List<User> getAllUsers(User actor)
List<User> getUsersByRole(User actor, Role role)
User   updateUser(User actor, String targetId, UpdateUserRequest req)
void   changePassword(User actor, String targetId, ChangePasswordRequest req)
void   deactivateUser(User actor, String targetId)
void   reactivateUser(User actor, String targetId)
void   deleteUser(User actor, String targetId)
User   authenticate(String username, String password)
```

### ProductService

```java
Product createProduct(User actor, CreateProductRequest req)
Product getProductById(User actor, String productId)
Product getProductBySku(User actor, String sku)
List<Product> getAllProducts(User actor)
List<Product> getActiveProducts(User actor)
List<Product> getProductsByCategory(User actor, String category)
List<Product> searchProducts(User actor, String keyword)
Product updateProduct(User actor, String productId, UpdateProductRequest req)
Product adjustStock(User actor, String productId, int delta)
void    softDeleteProduct(User actor, String productId)
void    hardDeleteProduct(User actor, String productId)
```

### InvoiceService

```java
Invoice createInvoice(User actor, CreateInvoiceRequest req)
Invoice addItem(User actor, String invoiceId, AddInvoiceItemRequest req)
Invoice getInvoiceById(User actor, String invoiceId)
List<Invoice> getAllInvoices(User actor)
List<Invoice> getInvoicesByStatus(User actor, Status status)
List<Invoice> getMyInvoices(User actor)
List<Invoice> getInvoicesByCustomer(User actor, String customerEmail)
Invoice issueInvoice(User actor, String invoiceId)
Invoice markPaid(User actor, String invoiceId)
Invoice cancelInvoice(User actor, String invoiceId)
void    deleteInvoice(User actor, String invoiceId)
```

---

## Extending the Project

### Swap the repositories for a real database

Each `*Repository` class is a plain Java class with no framework coupling. Replace the `ConcurrentHashMap` body with JDBC / JPA calls and wire them in `ServiceFactory` — no service code changes needed.

### Add Spring Boot

1. Annotate services with `@Service`, repositories with `@Repository`
2. Replace `ServiceFactory` with Spring's `@Configuration` + `@Bean` wiring
3. Wrap services in `@RestController` classes to expose REST endpoints

###  JWT authentication

`UserManagementService.authenticate()` already returns the validated `User` object. Wrap the result in a JWT and pass it back via an HTTP header; decode it in a servlet filter and reconstruct the actor before every service call.

### Replace password hashing

`PasswordUtil` is a single-class seam. Swap the body to use `BCryptPasswordEncoder` from Spring Security or `Argon2` from Bouncy Castle — the service code is unchanged.

---
