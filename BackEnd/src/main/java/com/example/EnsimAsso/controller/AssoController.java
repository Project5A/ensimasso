package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.model.User.Asso;
import com.example.EnsimAsso.service.AssoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assos")
@CrossOrigin(origins = "http://localhost:3000")  // Allow requests from your React frontend
public class AssoController {

    @Autowired
    private AssoService assoService;

    // Endpoint to get all Asso users
    @GetMapping
    public List<Asso> getAllAssoUsers() {
        return assoService.getAllAssoUsers();
    }

    // Endpoint to get an Asso user by ID
    @GetMapping("/{id}")
    public Asso getAssoById(@PathVariable Integer id) {
        return assoService.getAssoById(id);
    }
}
