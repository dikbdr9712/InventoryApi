// src/main/java/com/api/inventory/controller/ContactController.java

package com.api.inventory.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.api.inventory.entity.ContactMessage;
import com.api.inventory.repository.ContactMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    @Autowired
    private ContactMessageRepository repository;

    @PostMapping
    public ResponseEntity<String> submitContactForm(@RequestBody ContactMessage message) {
        repository.save(message);
        return ResponseEntity.ok("Message received!");
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('messages.view')")
    public List<ContactMessage> getAllMessages() {
        return repository.findAll();
    }
}