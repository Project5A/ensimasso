package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.config.JwtUtil;
import com.example.EnsimAsso.model.User;
import com.example.EnsimAsso.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    // Login endpoint
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            // Attempt to authenticate the user using UserService
            User user = userService.login(loginRequest.getEmail(), loginRequest.getPassword());

            // Generate JWT token
            String token = jwtUtil.generateToken(user.getEmail());

            // Return token in response
            return ResponseEntity.ok(new LoginResponse(token, user.getName()));
        } catch (Exception e) {
            // Return error if authentication fails
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid credentials");
        }
    }

    // Signup endpoint
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest signupRequest) {
        try {
            // Create user in the database
            User newUser = new User();
            newUser.setEmail(signupRequest.getEmail());
            newUser.setPassword(signupRequest.getPassword());
            newUser.setName(signupRequest.getName());

            User savedUser = userService.signup(newUser); // Call signup method from UserService

            // Return success response after registration
            return ResponseEntity.status(HttpStatus.CREATED).body("User created successfully");
        } catch (Exception e) {
            // Return error if user creation fails
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Signup failed. Please try again.");
        }
    }

    // Request body for login
    static class LoginRequest {
        private String email;
        private String password;

        // Getters and setters
        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    // Response body for login
    static class LoginResponse {
        private String token;
        private String username;

        public LoginResponse(String token, String username) {
            this.token = token;
            this.username = username;
        }

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }
    }

    // Request body for signup
    static class SignupRequest {
        private String email;
        private String password;
        private String name;

        // Getters and setters
        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
