package com.rbac;

import com.rbac.config.PasswordUtil;
import com.rbac.config.ServiceFactory;
import com.rbac.dto.Dto.*;
import com.rbac.exception.Exceptions.*;
import com.rbac.exception.RbacException;
import com.rbac.model.*;
import com.rbac.model.Invoice.Status;
import com.rbac.service.InvoiceService;
import com.rbac.service.ProductService;
import com.rbac.service.UserManagementService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        ServiceFactory        factory    = new ServiceFactory();
        UserManagementService userSvc    = factory.userService();
        ProductService        productSvc = factory.productService();
        InvoiceService        invoiceSvc = factory.invoiceService();

        User adminUser = new User("root_admin", "admin@company.com",
                PasswordUtil.hash("Admin@12345"), Role.ADMIN);
        userSvc.seedAdmin(adminUser);

        separator("DEMO: Role-Based User Management System");

        // 1. USER MANAGEMENT
        section("1. Creating Users (ADMIN actor)");
        User managerUser = userSvc.createUser(adminUser, new CreateUserRequest(
                "jane_manager", "jane@company.com", "SecurePass1!", Role.MANAGER));
        User salesUser = userSvc.createUser(adminUser, new CreateUserRequest(
                "bob_sales", "bob@company.com", "SecurePass2!", Role.SALES));
        User viewerUser = userSvc.createUser(adminUser, new CreateUserRequest(
                "alice_viewer", "alice@company.com", "SecurePass3!", Role.VIEWER));

        System.out.println("\nAll users:");
        userSvc.getAllUsers(adminUser).forEach(u ->
                System.out.printf("  %-20s role=%-8s active=%s%n",
                        u.getUsername(), u.getRole(), u.isActive()));

        section("1b. VIEWER tries to create a user (should be blocked)");
        tryAction("VIEWER creating user", () ->
                userSvc.createUser(viewerUser, new CreateUserRequest(
                        "hacker", "x@x.com", "p@ssw0rd1", Role.ADMIN)));

        section("1c. ADMIN promotes VIEWER to SALES");
        userSvc.updateUser(adminUser, viewerUser.getId(),
                new UpdateUserRequest(null, Role.SALES, null));
        System.out.printf("  Alice new role: %s%n", viewerUser.getRole());

        section("1d. ADMIN deactivates Bob");
        userSvc.deactivateUser(adminUser, salesUser.getId());
        System.out.printf("  Bob active: %s%n", salesUser.isActive());

        section("1e. Inactive user tries to act (should be blocked)");
        tryAction("inactive Bob viewing products", () ->
                productSvc.getAllProducts(salesUser));
        userSvc.reactivateUser(adminUser, salesUser.getId());
        System.out.printf("  Bob reactivated: %s%n", salesUser.isActive());

        // 2. PRODUCT MANAGEMENT
        section("2. Creating Products (MANAGER and ADMIN)");
        Product laptop = productSvc.createProduct(managerUser, new CreateProductRequest(
                "Pro Laptop 15", "High-performance laptop", "LAPTOP-001",
                new BigDecimal("1299.99"), 50, "Electronics"));
        Product mouse = productSvc.createProduct(adminUser, new CreateProductRequest(
                "Wireless Mouse", "Ergonomic wireless mouse", "MOUSE-001",
                new BigDecimal("29.99"), 200, "Accessories"));
        Product keyboard = productSvc.createProduct(managerUser, new CreateProductRequest(
                "Mechanical Keyboard", "RGB mechanical keyboard", "KB-001",
                new BigDecimal("89.99"), 100, "Accessories"));

        System.out.println("\nAll active products:");
        productSvc.getActiveProducts(adminUser).forEach(p ->
                System.out.printf("  %-25s SKU=%-12s price=%9s  stock=%d%n",
                        p.getName(), p.getSku(), p.getPrice(), p.getStockQuantity()));

        section("2b. SALES tries to create a product (should be blocked)");
        tryAction("SALES creating product", () ->
                productSvc.createProduct(salesUser, new CreateProductRequest(
                        "Rogue Item", "...", "ROGUE-001",
                        new BigDecimal("9.99"), 10, "Other")));

        section("2c. Search by keyword");
        productSvc.searchProducts(salesUser, "mouse")
                .forEach(p -> System.out.println("  Found: " + p.getName()));

        section("2d. MANAGER adjusts stock (+20 laptops)");
        productSvc.adjustStock(managerUser, laptop.getId(), 20);
        System.out.printf("  Laptop stock now: %d%n", laptop.getStockQuantity());

        // 3. INVOICE MANAGEMENT
        section("3. SALES creates an invoice");
        Invoice invoice = invoiceSvc.createInvoice(salesUser, new CreateInvoiceRequest(
                "Acme Corp", "billing@acme.com",
                LocalDate.now().plusDays(30), "Bulk order Q2"));
        System.out.println("  Created: " + invoice.getInvoiceNumber() + " | status=" + invoice.getStatus());

        section("3b. Adding line items");
        invoiceSvc.addItem(salesUser, invoice.getId(), new AddInvoiceItemRequest(laptop.getId(), 3));
        invoiceSvc.addItem(salesUser, invoice.getId(), new AddInvoiceItemRequest(mouse.getId(), 5));
        invoiceSvc.addItem(salesUser, invoice.getId(), new AddInvoiceItemRequest(keyboard.getId(), 3));

        Invoice current = invoiceSvc.getInvoiceById(salesUser, invoice.getId());
        System.out.println("  Line items:");
        current.getItems().forEach(item ->
                System.out.printf("    %-25s x%2d @ %9s = %s%n",
                        item.getProductName(), item.getQuantity(),
                        item.getUnitPrice(), item.getLineTotal()));
        System.out.println("  TOTAL: " + current.getTotalAmount());

        section("3c. SALES issues the invoice");
        invoiceSvc.issueInvoice(salesUser, invoice.getId());
        System.out.println("  Status: " + invoice.getStatus());

        section("3d. Alice (SALES) creates her own draft invoice");
        Invoice aliceInv = invoiceSvc.createInvoice(viewerUser, new CreateInvoiceRequest(
                "Beta Ltd", "finance@beta.com", LocalDate.now().plusDays(14), "Small order"));
        System.out.println("  Alice created: " + aliceInv.getInvoiceNumber());

        section("3e. MANAGER marks invoice PAID");
        invoiceSvc.markPaid(managerUser, invoice.getId());
        System.out.println("  Status: " + invoice.getStatus());

        section("3f. SALES tries to delete invoice (should be blocked)");
        tryAction("SALES deleting invoice", () ->
                invoiceSvc.deleteInvoice(salesUser, aliceInv.getId()));

        section("3g. ADMIN cancels then deletes Alice's draft");
        invoiceSvc.cancelInvoice(adminUser, aliceInv.getId());
        System.out.println("  Status after cancel: " + aliceInv.getStatus());
        invoiceSvc.deleteInvoice(adminUser, aliceInv.getId());
        System.out.println("  Invoice deleted.");

        section("3h. All invoices (any active role can view)");
        invoiceSvc.getAllInvoices(salesUser).forEach(inv ->
                System.out.printf("  %-18s  customer=%-15s  total=%10s  status=%s%n",
                        inv.getInvoiceNumber(), inv.getCustomerName(),
                        inv.getTotalAmount(), inv.getStatus()));

        // 4. SUMMARY
        separator("SUMMARY");
        System.out.println("Users by role:");
        for (Role r : Role.values()) {
            long count = userSvc.getUsersByRole(adminUser, r).size();
            if (count > 0) System.out.printf("  %-10s → %d user(s)%n", r, count);
        }
        System.out.println("\nActive products (remaining stock):");
        productSvc.getActiveProducts(adminUser).forEach(p ->
                System.out.printf("  %-25s stock=%d%n", p.getName(), p.getStockQuantity()));
        System.out.println("\nInvoices by status:");
        for (Status s : Status.values()) {
            long count = invoiceSvc.getInvoicesByStatus(adminUser, s).size();
            if (count > 0) System.out.printf("  %-12s → %d%n", s, count);
        }
        separator("DEMO COMPLETE");
    }

    private static void tryAction(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  [UNEXPECTED SUCCESS] " + label);
        } catch (UnauthorizedException e) {
            System.out.println("  [BLOCKED - UNAUTHORIZED] " + e.getMessage());
        } catch (UserInactiveException e) {
            System.out.println("  [BLOCKED - INACTIVE]     " + e.getMessage());
        } catch (RbacException e) {
            System.out.println("  [BLOCKED]                " + e.getMessage());
        }
    }

    private static void section(String title) {
        System.out.println("\n── " + title + " ──");
    }

    private static void separator(String title) {
        System.out.println("\n══════════════════════════════════════════════════════════");
        System.out.printf("  %s%n", title);
        System.out.println("══════════════════════════════════════════════════════════");
    }
}

<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <groupId>com.rbac</groupId>
    <artifactId>rbac-user-management</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>RBAC User Management Service</name>
    <description>
        Role-Based Access Control service with User, Product, and Invoice
        service layers. Pure Java 11+ — no external runtime frameworks.
    </description>

    <properties>
        <java.version>11</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- dependency versions -->
        <junit.version>5.10.2</junit.version>
        <mockito.version>5.11.0</mockito.version>
        <assertj.version>3.25.3</assertj.version>
    </properties>

    <dependencies>
        <!-- ── Test ──────────────────────────────────────────────────────── -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>${mockito.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>${assertj.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Compile -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                </configuration>
            </plugin>

            <!-- Test runner (JUnit 5) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>

            <!-- Executable fat-jar -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.2</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.rbac.Main</mainClass>
                                </transformer>
                            </transformers>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>

  package com.rbac.service;

import com.rbac.config.PasswordUtil;
import com.rbac.dto.Dto.*;
import com.rbac.exception.Exceptions.*;
import com.rbac.model.Role;
import com.rbac.model.User;
import com.rbac.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UserManagementService")
class UserManagementServiceTest {

    private UserRepository        repo;
    private UserManagementService svc;
    private User                  admin;
    private User                  manager;
    private User                  sales;
    private User                  viewer;

    @BeforeEach
    void setUp() {
        repo    = new UserRepository();
        svc     = new UserManagementService(repo);

        admin   = makeAndSeed("admin",   Role.ADMIN);
        manager = makeAndSeed("manager", Role.MANAGER);
        sales   = makeAndSeed("sales",   Role.SALES);
        viewer  = makeAndSeed("viewer",  Role.VIEWER);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User makeAndSeed(String name, Role role) {
        User u = new User(name, name + "@test.com",
                PasswordUtil.hash("P@ssword1"), role);
        repo.save(u);
        return u;
    }

    private CreateUserRequest newUserReq(String name, Role role) {
        return new CreateUserRequest(name, name + "@x.com", "P@ssword1", role);
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("createUser")
    class CreateUser {

        @Test @DisplayName("ADMIN can create any role")
        void adminCanCreate() {
            User u = svc.createUser(admin, newUserReq("newbie", Role.SALES));
            assertThat(u.getUsername()).isEqualTo("newbie");
            assertThat(u.getRole()).isEqualTo(Role.SALES);
            assertThat(u.isActive()).isTrue();
        }

        @Test @DisplayName("MANAGER cannot create users")
        void managerBlocked() {
            assertThatThrownBy(() -> svc.createUser(manager, newUserReq("x", Role.VIEWER)))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test @DisplayName("SALES cannot create users")
        void salesBlocked() {
            assertThatThrownBy(() -> svc.createUser(sales, newUserReq("x", Role.VIEWER)))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test @DisplayName("VIEWER cannot create users")
        void viewerBlocked() {
            assertThatThrownBy(() -> svc.createUser(viewer, newUserReq("x", Role.VIEWER)))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test @DisplayName("duplicate username throws")
        void duplicateUsername() {
            svc.createUser(admin, newUserReq("unique1", Role.SALES));
            assertThatThrownBy(() -> svc.createUser(admin, newUserReq("unique1", Role.SALES)))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("username");
        }

        @Test @DisplayName("duplicate email throws")
        void duplicateEmail() {
            svc.createUser(admin, new CreateUserRequest("u1", "shared@x.com", "P@ssword1", Role.SALES));
            assertThatThrownBy(() -> svc.createUser(admin,
                    new CreateUserRequest("u2", "shared@x.com", "P@ssword1", Role.SALES)))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("email");
        }

        @Test @DisplayName("short password throws")
        void shortPassword() {
            assertThatThrownBy(() -> svc.createUser(admin,
                    new CreateUserRequest("u3", "u3@x.com", "short", Role.SALES)))
                    .isInstanceOf(RbacException.class)
                    .hasMessageContaining("8 characters");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("updateUser")
    class UpdateUser {

        @Test @DisplayName("ADMIN can change role")
        void adminChangesRole() {
            svc.updateUser(admin, viewer.getId(), new UpdateUserRequest(null, Role.SALES, null));
            assertThat(viewer.getRole()).isEqualTo(Role.SALES);
        }

        @Test @DisplayName("non-ADMIN cannot update")
        void nonAdminBlocked() {
            assertThatThrownBy(() ->
                    svc.updateUser(manager, viewer.getId(), new UpdateUserRequest(null, Role.SALES, null)))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test @DisplayName("update to duplicate email throws")
        void duplicateEmailOnUpdate() {
            svc.createUser(admin, new CreateUserRequest("other", "other@x.com", "P@ssword1", Role.SALES));
            assertThatThrownBy(() ->
                    svc.updateUser(admin, viewer.getId(), new UpdateUserRequest("other@x.com", null, null)))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("deactivate / reactivate")
    class Activation {

        @Test @DisplayName("ADMIN deactivates and reactivates a user")
        void deactivateReactivate() {
            svc.deactivateUser(admin, sales.getId());
            assertThat(sales.isActive()).isFalse();

            svc.reactivateUser(admin, sales.getId());
            assertThat(sales.isActive()).isTrue();
        }

        @Test @DisplayName("ADMIN cannot deactivate themselves")
        void adminCannotDeactivateSelf() {
            assertThatThrownBy(() -> svc.deactivateUser(admin, admin.getId()))
                    .isInstanceOf(RbacException.class);
        }

        @Test @DisplayName("inactive actor is blocked")
        void inactiveActorBlocked() {
            sales.setActive(false);
            assertThatThrownBy(() -> svc.getAllUsers(sales))
                    .isInstanceOf(UserInactiveException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("authenticate")
    class Authenticate {

        @Test @DisplayName("correct credentials return user")
        void correctCredentials() {
            User u = svc.authenticate("admin", "P@ssword1");
            assertThat(u.getUsername()).isEqualTo("admin");
        }

        @Test @DisplayName("wrong password throws")
        void wrongPassword() {
            assertThatThrownBy(() -> svc.authenticate("admin", "wrong"))
                    .isInstanceOf(RbacException.class)
                    .hasMessageContaining("credentials");
        }

        @Test @DisplayName("inactive user cannot authenticate")
        void inactiveUser() {
            admin.setActive(false);
            assertThatThrownBy(() -> svc.authenticate("admin", "P@ssword1"))
                    .isInstanceOf(UserInactiveException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("queries")
    class Queries {

        @Test @DisplayName("getAllUsers returns all seeded users")
        void getAllUsers() {
            List<User> users = svc.getAllUsers(admin);
            assertThat(users).hasSizeGreaterThanOrEqualTo(4);
        }

        @Test @DisplayName("getUsersByRole filters correctly")
        void byRole() {
            List<User> admins = svc.getUsersByRole(admin, Role.ADMIN);
            assertThat(admins).allMatch(u -> u.getRole() == Role.ADMIN);
        }
    }
}

package com.rbac.service;

import com.rbac.config.PasswordUtil;
import com.rbac.dto.Dto.*;
import com.rbac.exception.Exceptions.*;
import com.rbac.model.Product;
import com.rbac.model.Role;
import com.rbac.model.User;
import com.rbac.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ProductService")
class ProductServiceTest {

    private ProductRepository repo;
    private ProductService    svc;

    private User admin;
    private User manager;
    private User sales;
    private User viewer;

    @BeforeEach
    void setUp() {
        repo    = new ProductRepository();
        svc     = new ProductService(repo);

        admin   = user("admin",   Role.ADMIN);
        manager = user("manager", Role.MANAGER);
        sales   = user("sales",   Role.SALES);
        viewer  = user("viewer",  Role.VIEWER);
    }

    private User user(String name, Role role) {
        return new User(name, name + "@t.com", PasswordUtil.hash("P@ss1234"), role);
    }

    private CreateProductRequest req(String name, String sku, int stock) {
        return new CreateProductRequest(name, "desc", sku,
                new BigDecimal("19.99"), stock, "General");
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("createProduct")
    class Create {

        @Test @DisplayName("ADMIN can create")
        void adminCreates() {
            Product p = svc.createProduct(admin, req("Widget", "W-001", 10));
            assertThat(p.getName()).isEqualTo("Widget");
            assertThat(p.isActive()).isTrue();
        }

        @Test @DisplayName("MANAGER can create")
        void managerCreates() {
            Product p = svc.createProduct(manager, req("Gadget", "G-001", 5));
            assertThat(p.getSku()).isEqualTo("G-001");
        }

        @Test @DisplayName("SALES cannot create")
        void salesBlocked() {
            assertThatThrownBy(() -> svc.createProduct(sales, req("X", "X-001", 1)))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test @DisplayName("VIEWER cannot create")
        void viewerBlocked() {
            assertThatThrownBy(() -> svc.createProduct(viewer, req("X", "X-002", 1)))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test @DisplayName("duplicate SKU throws")
        void duplicateSku() {
            svc.createProduct(admin, req("A", "DUP-001", 1));
            assertThatThrownBy(() -> svc.createProduct(admin, req("B", "DUP-001", 1)))
                    .isInstanceOf(DuplicateSkuException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("read operations")
    class Read {

        @Test @DisplayName("all active roles can view products")
        void allRolesView() {
            svc.createProduct(admin, req("P1", "P-001", 5));
            for (User u : new User[]{admin, manager, sales, viewer}) {
                assertThat(svc.getAllProducts(u)).isNotEmpty();
            }
        }

        @Test @DisplayName("search by keyword is case-insensitive")
        void searchCaseInsensitive() {
            svc.createProduct(admin, req("Pro Laptop", "LP-001", 3));
            List<Product> results = svc.searchProducts(viewer, "laptop");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("Pro Laptop");
        }

        @Test @DisplayName("getProductById throws for unknown id")
        void unknownId() {
            assertThatThrownBy(() -> svc.getProductById(admin, "unknown-id"))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("adjustStock")
    class Stock {

        private Product product;

        @BeforeEach
        void seedProduct() {
            product = svc.createProduct(admin, req("StockItem", "S-001", 100));
        }

        @Test @DisplayName("positive delta increases stock")
        void addStock() {
            svc.adjustStock(manager, product.getId(), 50);
            assertThat(product.getStockQuantity()).isEqualTo(150);
        }

        @Test @DisplayName("negative delta decreases stock")
        void removeStock() {
            svc.adjustStock(admin, product.getId(), -30);
            assertThat(product.getStockQuantity()).isEqualTo(70);
        }

        @Test @DisplayName("stock cannot go below zero")
        void belowZero() {
            assertThatThrownBy(() -> svc.adjustStock(admin, product.getId(), -200))
                    .isInstanceOf(InsufficientStockException.class);
        }

        @Test @DisplayName("SALES cannot adjust stock")
        void salesBlocked() {
            assertThatThrownBy(() -> svc.adjustStock(sales, product.getId(), 10))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("softDelete / hardDelete")
    class Delete {

        @Test @DisplayName("soft delete deactivates product")
        void softDelete() {
            Product p = svc.createProduct(admin, req("ToDeactivate", "TD-001", 5));
            svc.softDeleteProduct(manager, p.getId());
            assertThat(p.isActive()).isFalse();
        }

        @Test @DisplayName("hard delete removes product — ADMIN only")
        void hardDelete() {
            Product p = svc.createProduct(admin, req("ToDelete", "DEL-001", 5));
            svc.hardDeleteProduct(admin, p.getId());
            assertThat(svc.getAllProducts(admin)).doesNotContain(p);
        }

        @Test @DisplayName("MANAGER cannot hard delete")
        void managerCannotHardDelete() {
            Product p = svc.createProduct(admin, req("KeepMe", "KM-001", 5));
            assertThatThrownBy(() -> svc.hardDeleteProduct(manager, p.getId()))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }
}

package com.rbac.service;

import com.rbac.config.PasswordUtil;
import com.rbac.dto.Dto.*;
import com.rbac.exception.Exceptions.*;
import com.rbac.model.Invoice;
import com.rbac.model.Invoice.Status;
import com.rbac.model.Product;
import com.rbac.model.Role;
import com.rbac.model.User;
import com.rbac.repository.InvoiceRepository;
import com.rbac.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("InvoiceService")
class InvoiceServiceTest {

    private InvoiceService invoiceSvc;
    private ProductService productSvc;

    private User  admin;
    private User  manager;
    private User  sales;
    private User  viewer;
    private Product laptop;

    @BeforeEach
    void setUp() {
        ProductRepository productRepo = new ProductRepository();
        InvoiceRepository invoiceRepo = new InvoiceRepository();

        productSvc = new ProductService(productRepo);
        invoiceSvc = new InvoiceService(invoiceRepo, productSvc);

        admin   = user("admin",   Role.ADMIN);
        manager = user("manager", Role.MANAGER);
        sales   = user("sales",   Role.SALES);
        viewer  = user("viewer",  Role.VIEWER);

        laptop = productSvc.createProduct(admin, new CreateProductRequest(
                "Laptop", "desc", "LT-001", new BigDecimal("999.00"), 100, "Tech"));
    }

    private User user(String name, Role role) {
        return new User(name, name + "@t.com", PasswordUtil.hash("P@ss1234"), role);
    }

    private CreateInvoiceRequest invoiceReq(String customer) {
        return new CreateInvoiceRequest(customer, customer + "@co.com",
                LocalDate.now().plusDays(30), null);
    }

    private Invoice draft(User actor) {
        return invoiceSvc.createInvoice(actor, invoiceReq("TestCo"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("createInvoice")
    class Create {

        @Test @DisplayName("ADMIN / MANAGER / SALES can create")
        void allowedRoles() {
            for (User u : new User[]{admin, manager, sales}) {
                Invoice inv = invoiceSvc.createInvoice(u, invoiceReq("Cust"));
                assertThat(inv.getStatus()).isEqualTo(Status.DRAFT);
            }
        }

        @Test @DisplayName("VIEWER cannot create invoices")
        void viewerBlocked() {
            assertThatThrownBy(() -> draft(viewer))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test @DisplayName("blank customer name throws")
        void blankCustomer() {
            assertThatThrownBy(() -> invoiceSvc.createInvoice(admin,
                    new CreateInvoiceRequest("", "a@b.com", LocalDate.now(), null)))
                    .isInstanceOf(RbacException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("addItem")
    class AddItem {

        @Test @DisplayName("adds item and deducts stock")
        void addsItem() {
            Invoice inv = draft(sales);
            invoiceSvc.addItem(sales, inv.getId(), new AddInvoiceItemRequest(laptop.getId(), 2));

            Invoice updated = invoiceSvc.getInvoiceById(sales, inv.getId());
            assertThat(updated.getItems()).hasSize(1);
            assertThat(updated.getTotalAmount()).isEqualByComparingTo("1998.00");
            assertThat(laptop.getStockQuantity()).isEqualTo(98);
        }

        @Test @DisplayName("cannot add to non-DRAFT invoice")
        void nonDraft() {
            Invoice inv = draft(sales);
            invoiceSvc.issueInvoice(sales, inv.getId());

            assertThatThrownBy(() ->
                    invoiceSvc.addItem(sales, inv.getId(), new AddInvoiceItemRequest(laptop.getId(), 1)))
                    .isInstanceOf(InvalidInvoiceStateException.class);
        }

        @Test @DisplayName("insufficient stock throws")
        void insufficientStock() {
            Invoice inv = draft(sales);
            assertThatThrownBy(() ->
                    invoiceSvc.addItem(sales, inv.getId(), new AddInvoiceItemRequest(laptop.getId(), 9999)))
                    .isInstanceOf(InsufficientStockException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("status transitions")
    class Transitions {

        @Test @DisplayName("DRAFT → ISSUED → PAID (happy path)")
        void happyPath() {
            Invoice inv = draft(sales);
            invoiceSvc.addItem(sales, inv.getId(), new AddInvoiceItemRequest(laptop.getId(), 1));

            invoiceSvc.issueInvoice(sales, inv.getId());
            assertThat(inv.getStatus()).isEqualTo(Status.ISSUED);

            invoiceSvc.markPaid(manager, inv.getId());
            assertThat(inv.getStatus()).isEqualTo(Status.PAID);
        }

        @Test @DisplayName("cannot issue empty invoice")
        void emptyInvoice() {
            Invoice inv = draft(sales);
            assertThatThrownBy(() -> invoiceSvc.issueInvoice(sales, inv.getId()))
                    .isInstanceOf(InvalidInvoiceStateException.class)
                    .hasMessageContaining("no line items");
        }

        @Test @DisplayName("VIEWER cannot issue invoices")
        void viewerCannotIssue() {
            Invoice inv = draft(admin);
            invoiceSvc.addItem(admin, inv.getId(), new AddInvoiceItemRequest(laptop.getId(), 1));
            assertThatThrownBy(() -> invoiceSvc.issueInvoice(viewer, inv.getId()))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test @DisplayName("cannot cancel a PAID invoice")
        void cancelPaid() {
            Invoice inv = draft(sales);
            invoiceSvc.addItem(sales, inv.getId(), new AddInvoiceItemRequest(laptop.getId(), 1));
            invoiceSvc.issueInvoice(sales, inv.getId());
            invoiceSvc.markPaid(manager, inv.getId());

            assertThatThrownBy(() -> invoiceSvc.cancelInvoice(admin, inv.getId()))
                    .isInstanceOf(InvalidInvoiceStateException.class);
        }

        @Test @DisplayName("cancel restores stock")
        void cancelRestoresStock() {
            Invoice inv = draft(admin);
            invoiceSvc.addItem(admin, inv.getId(), new AddInvoiceItemRequest(laptop.getId(), 10));
            int stockBefore = laptop.getStockQuantity();   // 90

            invoiceSvc.cancelInvoice(admin, inv.getId());
            assertThat(laptop.getStockQuantity()).isEqualTo(stockBefore + 10);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("delete")
    class Delete {

        @Test @DisplayName("ADMIN can delete a DRAFT invoice")
        void adminDeletesDraft() {
            Invoice inv = draft(sales);
            invoiceSvc.deleteInvoice(admin, inv.getId());
            assertThatThrownBy(() -> invoiceSvc.getInvoiceById(admin, inv.getId()))
                    .isInstanceOf(InvoiceNotFoundException.class);
        }

        @Test @DisplayName("SALES cannot delete invoices")
        void salesBlocked() {
            Invoice inv = draft(sales);
            assertThatThrownBy(() -> invoiceSvc.deleteInvoice(sales, inv.getId()))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test @DisplayName("cannot delete ISSUED invoice")
        void deleteIssued() {
            Invoice inv = draft(sales);
            invoiceSvc.addItem(sales, inv.getId(), new AddInvoiceItemRequest(laptop.getId(), 1));
            invoiceSvc.issueInvoice(sales, inv.getId());

            assertThatThrownBy(() -> invoiceSvc.deleteInvoice(admin, inv.getId()))
                    .isInstanceOf(InvalidInvoiceStateException.class);
        }
    }
}

package com.rbac.repository;

import com.rbac.config.PasswordUtil;
import com.rbac.model.Role;
import com.rbac.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UserRepository")
class UserRepositoryTest {

    private UserRepository repo;
    private User           alice;

    @BeforeEach
    void setUp() {
        repo  = new UserRepository();
        alice = new User("alice", "alice@test.com",
                PasswordUtil.hash("secret123"), Role.SALES);
        repo.save(alice);
    }

    @Test @DisplayName("findById returns saved user")
    void findById() {
        Optional<User> found = repo.findById(alice.getId());
        assertThat(found).isPresent().contains(alice);
    }

    @Test @DisplayName("findByUsername is case-insensitive")
    void findByUsername() {
        assertThat(repo.findByUsername("ALICE")).isPresent();
        assertThat(repo.findByUsername("alice")).isPresent();
    }

    @Test @DisplayName("findByEmail is case-insensitive")
    void findByEmail() {
        assertThat(repo.findByEmail("ALICE@TEST.COM")).isPresent();
    }

    @Test @DisplayName("findAllActive excludes inactive users")
    void findAllActive() {
        User bob = new User("bob", "bob@test.com",
                PasswordUtil.hash("secret123"), Role.VIEWER);
        bob.setActive(false);
        repo.save(bob);

        assertThat(repo.findAllActive())
                .contains(alice)
                .doesNotContain(bob);
    }

    @Test @DisplayName("delete removes user")
    void delete() {
        repo.delete(alice.getId());
        assertThat(repo.findById(alice.getId())).isEmpty();
    }

    @Test @DisplayName("existsByUsername returns true for existing user")
    void existsByUsername() {
        assertThat(repo.existsByUsername("alice")).isTrue();
        assertThat(repo.existsByUsername("nobody")).isFalse();
    }
}
