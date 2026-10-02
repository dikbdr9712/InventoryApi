package com.api.inventory.security;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * THE LIST OF THINGS A PERSON CAN BE ALLOWED TO DO.
 *
 * Each permission guards real code (an @PreAuthorize or a check in a service), so the list itself lives here.
 * WHICH ROLE HAS WHICH permission is stored in the database (role_permissions) and changed with tick boxes
 * on the User management screen. The defaults below are only used the first time a role is created.
 *
 * Adding a permission: add it to CATALOG, protect the endpoint with hasAuthority('...'), and add it to the
 * Angular type in utils/permissions.ts.
 */
public final class Permissions {

    private Permissions() {
    }

    /** One permission, as shown on the screen. */
    public record Definition(String key, String group, String label, String description, boolean sensitive) {
    }

    public static final List<Definition> CATALOG = List.of(
            // Selling at the counter
            new Definition("pos.use", "Counter sales", "Use the point of sale",
                    "Sell at the counter, open and close their own cash drawer, see sales history.", false),
            new Definition("pos.discount", "Counter sales", "Give extra discounts",
                    "Sell below the shop price at the counter (within each product's maximum discount).", false),
            new Definition("pos.shifts.manage", "Counter sales", "Manage all cash drawers",
                    "See every cashier's shifts and close a drawer someone forgot to close.", false),
            new Definition("sales.return", "Counter sales", "Take returns and refund",
                    "Take items back from a sale and give the money back.", true),
            // Online orders
            new Definition("orders.view", "Online orders", "See orders",
                    "See the staff order list and the deliveries board.", false),
            new Definition("orders.fulfil", "Online orders", "Pack, send and cancel orders",
                    "Confirm, pack, hand over, deliver and cancel orders.", false),
            new Definition("payments.verify", "Online orders", "Verify payments",
                    "Check bank transfers and mark orders as paid.", true),
            // Products and stock
            new Definition("items.manage", "Products and stock", "Add and edit products",
                    "Change products, prices and photos (also sees cost prices).", false),
            new Definition("stock.restock", "Products and stock", "Restock",
                    "Add stock that arrived from suppliers.", false),
            // Customers and reports
            new Definition("messages.view", "Customers and reports", "Read customer messages",
                    "Read messages sent from the Contact page.", false),
            new Definition("reports.view", "Customers and reports", "See the sales dashboard",
                    "See sales totals, tax and discount reports.", false),
            new Definition("customers.view", "Customers and reports", "See customers",
                    "See the customer list with contact details, visits and order history.", true),
            new Definition("customers.manage", "Customers and reports", "Edit customer details",
                    "Correct a customer's name, phone, email, address and notes.", false),
            // Running the business
            new Definition("users.manage", "Administration", "Manage users and roles",
                    "Add people, switch accounts off, reset passwords, and change what each role may do.", true),
            new Definition("marketplace.manage", "Administration", "Run the marketplace",
                    "Approve sellers and riders, set commission and delivery fees, record payouts.", true),
            // Marketplace sellers (their own shop only, never other sellers' data)
            new Definition("seller.portal", "Marketplace sellers", "Seller dashboard",
                    "Open My shop and see its overview.", false),
            new Definition("seller.products", "Marketplace sellers", "Manage own products",
                    "Add and edit the shop's own products, prices, photos and stock.", false),
            new Definition("seller.orders", "Marketplace sellers", "Pack orders",
                    "See paid orders for the shop and mark packages ready for pickup.", false),
            new Definition("seller.earnings", "Marketplace sellers", "See earnings and payouts",
                    "See what the shop earned, commission taken and money paid out.", true),
            // Delivery drivers (their own jobs only)
            new Definition("rider.portal", "Delivery drivers", "Driver dashboard",
                    "Open My deliveries.", false),
            new Definition("rider.jobs", "Delivery drivers", "Take and deliver jobs",
                    "See open jobs, accept them, pick up and deliver with the customer's code.", false),
            new Definition("rider.earnings", "Delivery drivers", "See earnings and payouts",
                    "See delivery earnings and money paid out.", true));

    public static final Set<String> ALL_KEYS;

    static {
        Set<String> keys = new LinkedHashSet<>();
        CATALOG.forEach(d -> keys.add(d.key()));
        ALL_KEYS = Set.copyOf(keys);
    }

    /** The built-in roles. They cannot be deleted or renamed. ADMIN always has everything, so nobody can lock the shop out. */
    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";
    public static final String SELLER = "SELLER";
    public static final String RIDER = "RIDER";

    /** Everything a seller or a driver can be given (their own data only). */
    public static final Set<String> SELLER_ALL = Set.of("seller.portal", "seller.products", "seller.orders", "seller.earnings");
    public static final Set<String> RIDER_ALL = Set.of("rider.portal", "rider.jobs", "rider.earnings");

    /**
     * What ADMIN always has: every SHOP permission. Not the seller / driver dashboards: an admin has no shop or
     * driver account (sellers and drivers are separate accounts, approved in Marketplace).
     */
    public static final Set<String> ADMIN_KEYS;

    static {
        Set<String> keys = new LinkedHashSet<>(ALL_KEYS);
        keys.removeAll(SELLER_ALL);
        keys.removeAll(RIDER_ALL);
        ADMIN_KEYS = Set.copyOf(keys);
    }

    /** Starting permissions for the roles the shop has always had. Used once, when a role has never been set up. */
    public static final Map<String, Set<String>> DEFAULTS;

    static {
        Map<String, Set<String>> d = new LinkedHashMap<>();
        d.put(ADMIN, ADMIN_KEYS);
        d.put("MANAGER", Set.of("pos.use", "pos.discount", "pos.shifts.manage", "sales.return", "orders.view", "orders.fulfil",
                "payments.verify", "items.manage", "stock.restock", "messages.view", "reports.view", "customers.view", "customers.manage"));
        d.put("CONTROLLER", Set.of("pos.use", "orders.view", "orders.fulfil", "stock.restock", "messages.view", "customers.view"));
        d.put(SELLER, SELLER_ALL);
        d.put(RIDER, RIDER_ALL);
        d.put(USER, Set.of());
        DEFAULTS = Map.copyOf(d);
    }

    public static final Map<String, String> DESCRIPTIONS = Map.of(
            ADMIN, "Runs the whole system. Always has every permission.",
            "MANAGER", "Runs the shop day to day.",
            "CONTROLLER", "Handles stock and sends orders out. Does not approve money.",
            SELLER, "Marketplace seller. Given when an application is approved in Marketplace.",
            RIDER, "Delivery driver. Given when an application is approved in Marketplace.",
            USER, "Customer. Shops online; no staff tools.");

    /** Roles that are part of the system and cannot be deleted or renamed. */
    public static final Set<String> BUILT_IN = Set.of(ADMIN, "MANAGER", "CONTROLLER", SELLER, RIDER, USER);

    /**
     * Permissions added to the catalog after a version: roles that were already set up get them ONCE (only those in
     * the role's defaults), so a new feature reaches existing roles without overwriting what an admin chose.
     */
    public static final int CATALOG_VERSION = 2;
    public static final Map<Integer, Set<String>> ADDED_IN = Map.of(
            2, Set.of("customers.view", "customers.manage"));

    /** Default permissions of a role (empty for unknown roles). For tests and first-time setup only. */
    public static Set<String> defaultsFor(String role) {
        if (role == null) {
            return Set.of();
        }
        return DEFAULTS.getOrDefault(role.trim().toUpperCase(), Set.of());
    }
}
