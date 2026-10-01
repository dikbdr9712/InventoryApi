package com.api.inventory.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * When @PreAuthorize refuses a request, the screen gets a clear answer:
 *   401 "Please sign in."                          nobody is signed in (the app then shows the sign-in page)
 *   403 "Your account is not allowed to do this."  signed in, but this role may not do it
 * It goes first, so a general error handler elsewhere cannot turn these into a 500 error.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionAdvice {

    @ExceptionHandler({AccessDeniedException.class, AuthenticationCredentialsNotFoundException.class})
    public ResponseEntity<Map<String, String>> refused(RuntimeException e) {
        Authentication who = SecurityContextHolder.getContext().getAuthentication();
        boolean signedIn = who != null && who.isAuthenticated() && !(who instanceof AnonymousAuthenticationToken);

        if (signedIn) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Your account is not allowed to do this."));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please sign in."));
    }
}