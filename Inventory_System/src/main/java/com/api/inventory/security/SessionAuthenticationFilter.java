package com.api.inventory.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Login (AuthController) stores "userEmail" and "userRole" in the HTTP session, but nothing told Spring Security
 * who the person was. So @PreAuthorize never worked, and "who did this" was always "system".
 *
 * This filter reads those two session values on every request and tells Spring Security:
 * the signed-in person is this email, and they have these permissions (see Permissions).
 * If there is no session, nothing changes and the visitor stays anonymous.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        SecurityContext before = SecurityContextHolder.getContext();
        try {
            HttpSession session = request.getSession(false); // never creates a session
            if (session != null) {
                Object email = session.getAttribute("userEmail");
                Object role = session.getAttribute("userRole");

                if (email instanceof String signedInEmail && role instanceof String roleName) {
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName.trim().toUpperCase())); // for hasRole(...)
                    for (String permission : Permissions.forRole(roleName)) {
                        authorities.add(new SimpleGrantedAuthority(permission));                         // for hasAuthority(...)
                    }
                    var principal = User.withUsername(signedInEmail).password("").authorities(authorities).build();
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
                    SecurityContextHolder.setContext(context);
                }
            }
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.setContext(before); // leave nothing behind for the next request on this thread
        }
    }
}