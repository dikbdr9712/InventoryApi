// src/main/java/com/api/inventory/controller/AuthController.java
package com.api.inventory.controller;

import com.api.inventory.dto.CurrentUserDTO;
import com.api.inventory.dto.SignupRequest;
import com.api.inventory.entity.Role;
import com.api.inventory.entity.User;
import com.api.inventory.repository.UserRepository;
import com.api.inventory.repository.RoleRepository;
import com.api.inventory.security.AccessControlService;
import com.api.inventory.security.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * Sign up, sign in, "who am I", sign out and change password.
 * Answers never contain the password hash. Problems with what the person typed are 4xx with a plain message
 * (the screens show that text), never a 500 error.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Same rule as the sign-up screen. */
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final String WRONG_LOGIN = "That email and password do not match.";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AccessControlService access;

    @Autowired
    private com.api.inventory.service.PasswordResetService passwordReset;

    @Autowired
    private com.api.inventory.service.LegalTermsService legal;

    @Autowired
    private com.api.inventory.service.CustomerService customerService;

    @PostMapping("/signup")
    @org.springframework.transaction.annotation.Transactional // the account and the terms acceptance are saved together
    public ResponseEntity<?> signup(@RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        String name = trim(request.getName());
        String email = trim(request.getEmail());
        String phone = trim(request.getPhone());
        String password = request.getPassword() == null ? "" : request.getPassword();

        // The browser checks these too, but the server decides.
        if (name.isEmpty()) return badRequest("Enter your full name.");
        if (!email.matches("^\\S+@\\S+\\.\\S+$")) return badRequest("Enter a valid email address.");
        if (!phone.matches("^[0-9]{8}$")) return badRequest("Enter an 8-digit phone number.");
        if (password.length() < MIN_PASSWORD_LENGTH) return badRequest("Use at least " + MIN_PASSWORD_LENGTH + " characters for the password.");

        // the customer must agree to the CURRENT Terms of Use and Privacy (recorded below, with time and IP)
        Integer currentTerms = legal.current("CUSTOMER").getVersion();
        if (request.getAcceptedTermsVersion() == null) return badRequest("Please read and accept the Terms of Use and Privacy.");
        if (!request.getAcceptedTermsVersion().equals(currentTerms)) {
            return badRequest("The Terms of Use were updated. Please reload the page and accept the latest version.");
        }

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("This email is already registered. Please sign in.");
        }
        if (userRepository.existsByPhone(phone)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("This phone number is already registered.");
        }

        Role customerRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("The customer role (USER) is missing."));

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(customerRole);
        User saved = userRepository.save(user);

        legal.accept(saved.getEmail(), "CUSTOMER", currentTerms, httpRequest);
        customerService.linkUser(saved); // their customer record (joined to earlier counter visits with the same phone)
        return ResponseEntity.ok(toDto(saved));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String email = trim(request.getEmail());
        String password = request.getPassword() == null ? "" : request.getPassword();

        User user = userRepository.findByEmail(email).orElse(null);
        // Same answer for "no such email" and "wrong password", so nobody can find out which emails exist.
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(WRONG_LOGIN);
        }
        if (!user.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("This account is switched off. Please contact the shop.");
        }
        user.setLastLoginAt(java.time.Instant.now());
        userRepository.save(user);

        // Fresh session id at sign-in, so a session id someone saw before sign-in is worth nothing afterwards.
        HttpSession old = httpRequest.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("userEmail", user.getEmail());
        session.setAttribute("userRole", user.getRole().getName());
        session.setAttribute(com.api.inventory.security.SessionAuthenticationFilter.PASSWORD_STAMP, user.passwordStamp());

        return ResponseEntity.ok(toDto(user));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(HttpServletRequest request) {
        String email = CurrentUser.email();
        User user = email == null ? null : userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }
        // The role may have changed since sign-in (an admin approved a seller or rider): use the current one from now on
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute("userRole", user.getRole().getName());
        }
        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok("Logged out");
    }

    /** The signed-in person changes their own password (for example one an admin gave them). */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(CurrentUser.email()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Please sign in.");
        }

        String current = request.getCurrentPassword() == null ? "" : request.getCurrentPassword();
        String next = request.getNewPassword() == null ? "" : request.getNewPassword();

        if (!passwordEncoder.matches(current, user.getPassword())) {
            return badRequest("Your current password is not correct.");
        }
        if (next.length() < MIN_PASSWORD_LENGTH) {
            return badRequest("Use at least " + MIN_PASSWORD_LENGTH + " characters for the new password.");
        }
        if (next.equals(current)) {
            return badRequest("The new password must be different from the current one.");
        }

        user.setPassword(passwordEncoder.encode(next));
        user.setPasswordChangedAt(java.time.Instant.now());
        userRepository.save(user);
        // this browser stays signed in; every other place where the account was signed in is signed out
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.setAttribute(com.api.inventory.security.SessionAuthenticationFilter.PASSWORD_STAMP, user.passwordStamp());
        }
        return ResponseEntity.ok(Map.of("message", "Your password has been changed. Other devices were signed out."));
    }

    // ================= Forgot password =================

    /** Sends a reset link by email. The answer is the same whether or not the email has an account. */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        passwordReset.request(body == null ? null : body.get("email"), clientIp(httpRequest));
        return ResponseEntity.ok(Map.of("message",
                "If an account uses that email, we have sent it a link to choose a new password. The link works for 30 minutes."));
    }

    /** Is this link still good? (The page shows the form, or explains that the link expired.) */
    @GetMapping("/reset-password/check")
    public Map<String, Boolean> checkResetLink(@RequestParam String token) {
        return Map.of("valid", passwordReset.isValid(token));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        passwordReset.reset(body == null ? null : body.get("token"), body == null ? null : body.get("newPassword"), MIN_PASSWORD_LENGTH);
        return ResponseEntity.ok(Map.of("message", "Your password has been changed. Sign in with the new password."));
    }

    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr(); // behind the proxy, server.forward-headers-strategy makes this the visitor's address
    }

    private CurrentUserDTO toDto(User user) {
        CurrentUserDTO dto = new CurrentUserDTO(user.getEmail(), user.getName(), user.getPhone(), user.getRole().getName());
        dto.setPermissions(access.permissionsOf(user.getRole().getName()).stream().sorted().toList());
        return dto;
    }

    private static ResponseEntity<String> badRequest(String message) {
        return ResponseEntity.badRequest().body(message);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;

        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
