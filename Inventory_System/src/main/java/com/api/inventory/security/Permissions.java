package com.api.inventory.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WHO CAN DO WHAT, on the server. This is the same table as src/app/utils/permissions.ts in the Angular app,
 * and the two must always match: the screens hide what a person may not do, and the server refuses it.
 *
 * (Later this moves into the database, so adding a role needs no code change.)
 */
public final class Permissions {

    private Permissions() {
    }

    private static final List<String> EVERYTHING = List.of(
            "orders.view",       // see the staff order list
            "orders.fulfil",     // confirm, ship, deliver and cancel orders
            "payments.verify",   // check payments and confirm that money arrived
            "items.manage",      // add and edit products
            "stock.restock",     // add stock
            "pos.use",           // counter sales and the sales history
            "sales.return",      // take items back from a sale and refund them
            "reports.view",      // sales dashboard
            "messages.view",     // customer messages
            "users.manage");     // change other people's roles

    private static final Map<String, Set<String>> BY_ROLE = Map.of(
            // runs the whole system
            "ADMIN", new LinkedHashSet<>(EVERYTHING),
            // runs the shop day to day: everything except managing other users
            "MANAGER", without(EVERYTHING, "users.manage"),
            // handles stock and getting orders out. Does not verify money or edit the catalogue.
            "CONTROLLER", new LinkedHashSet<>(List.of("orders.view", "orders.fulfil", "stock.restock", "pos.use", "messages.view")),
            // customers: no staff tools at all
            "USER", new LinkedHashSet<>());

    /** The permissions of a role. An unknown or missing role has none. */
    public static Set<String> forRole(String role) {
        if (role == null) {
            return Set.of();
        }
        Set<String> found = BY_ROLE.get(role.trim().toUpperCase());
        return found == null ? Set.of() : Set.copyOf(found);
    }

    private static Set<String> without(List<String> all, String removed) {
        Set<String> result = new LinkedHashSet<>(all);
        result.remove(removed);
        return result;
    }
}