package com.example.EnsimAsso.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.example.EnsimAsso.config.JwtUtil;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // Injecting PasswordEncoder and JwtUtil
    public AuthController(JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Replace with actual user retrieval from database
        String storedPasswordHash = "$2a$10$N9qo8uLOickgx2Z5Xm1o.V/8B5.0ZzM9/sg3Zm7PgsMOHgfDaq5hS"; // Example hashed password ("password")

        // Validate the credentials by comparing the hashed password
        if ("user".equals(request.getUsername()) && passwordEncoder.matches(request.getPassword(), storedPasswordHash)) {
            String token = jwtUtil.generateToken(request.getUsername());
            return ResponseEntity.ok(new LoginResponse(token));
        }

        return ResponseEntity.status(401).body("Invalid credentials");
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String token) {
        token = token.replace("Bearer ", "");
        if (jwtUtil.validateToken(token)) {
            return ResponseEntity.ok("Token is valid");
        }
        return ResponseEntity.status(401).body("Invalid token");
    }
}


// DTOs for login request and response

class LoginRequest {
    private String username;
    private String password;

    // Constructor
    public LoginRequest() {}

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

class LoginResponse {
    private String token;

    // Constructor
    public LoginResponse(String token) {
        this.token = token;
    }

    // Getter
    public String getToken() {
        return token;
    }

    // Setter (optional, but useful if you plan to deserialize the response)
    public void setToken(String token) {
        this.token = token;
    }
}

