package com.api.inventory.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Who is calling, for code inside a controller.
 * (SessionAuthenticationFilter fills this in from the login session on every request.)
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The signed-in person's email, or null when nobody is signed in. */
    public static String email() {
        Authentication who = SecurityContextHolder.getContext().getAuthentication();
        if (who == null || !who.isAuthenticated() || who instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return who.getName();
    }

    /** True when the signed-in person has this permission (for example "items.manage"). */
    public static boolean has(String permission) {
        Authentication who = SecurityContextHolder.getContext().getAuthentication();
        if (who == null || !who.isAuthenticated() || who instanceof AnonymousAuthenticationToken) {
            return false;
        }
        for (GrantedAuthority authority : who.getAuthorities()) {
            if (authority.getAuthority().equals(permission)) {
                return true;
            }
        }
        return false;
    }
}