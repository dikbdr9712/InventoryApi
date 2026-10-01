// src/main/java/com/api/inventory/controller/AuthController.java
package com.api.inventory.controller;

import com.api.inventory.dto.CurrentUserDTO;
import com.api.inventory.dto.SignupRequest;
import com.api.inventory.dto.UserDTO;
import com.api.inventory.entity.Role;
import com.api.inventory.entity.User;
import com.api.inventory.repository.UserRepository;
import com.api.inventory.repository.RoleRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RoleRepository roleRepository;

    @PostMapping("/signup")
    public ResponseEntity<User> signup(@Valid @RequestBody SignupRequest request) {
        System.out.println(">>> Received signup request: " + request.getEmail() + ", " + request.getPhone());

        if (userRepository.existsByEmail(request.getEmail())) {
            System.out.println(">>> Email already exists: " + request.getEmail());
            throw new RuntimeException("Email already in use");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            System.out.println(">>> Phone already exists: " + request.getPhone());
            throw new RuntimeException("Phone number already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        
        Role userRole = roleRepository.findById(4L)
                .orElseThrow(() -> new RuntimeException("Default USER role (id=4) not found in roles table"));
        user.setRole(userRole);

        User saved = userRepository.save(user);
        System.out.println(">>> User saved to DB: ID=" + saved.getId() + ", Email=" + saved.getEmail());

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("Invalid email or password"));

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new RuntimeException("Invalid email or password");
            }

            // ✅ Create session and store user info
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("userRole", user.getRole().getName());

            System.out.println(">>> Login successful for: " + user.getEmail());

            return ResponseEntity.ok(new CurrentUserDTO(
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getRole().getName()
            ));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Login failed due to internal error", e);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("userEmail") == null) {
            System.out.println(">>> /me: No active session or user not authenticated");
            return ResponseEntity.status(401).body("Not authenticated");
        }

        String email = (String) session.getAttribute("userEmail");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found in DB"));

        System.out.println(">>> /me: Returning user: " + user.getEmail());
        return ResponseEntity.ok(new CurrentUserDTO(
            user.getEmail(),
            user.getName(),
            user.getPhone(),
            user.getRole().getName()
            
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpSession session) {
        session.invalidate();
        System.out.println(">>> User logged out");
        return ResponseEntity.ok("Logged out");
    }


    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    // Optional: Keep this if used elsewhere
    public static class CurrentUser {
        private String email;
        private String role;

        public CurrentUser(String email, String role) {
            this.email = email;
            this.role = role;
        }

        public String getEmail() { return email; }
        public String getRole() { return role; }
    }
}