package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.config.JwtUtil;
import com.example.EnsimAsso.model.User.Guest;
import com.example.EnsimAsso.model.User.User;
import com.example.EnsimAsso.service.UserService;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            System.out.println("Attempting login for email: " + loginRequest.getEmail());

            // Authenticate user
            User user = userService.login(loginRequest.getEmail(), loginRequest.getPassword());
            System.out.println("User authenticated: " + user.getEmail());

            // Generate JWT token
            String token = jwtUtil.generateToken(user.getEmail());
            System.out.println("Generated token: " + token);
            
            // Convert user to a simple JSON-like map (without returning a User object)
            Map<String, Object> userJson = new HashMap<>();
            userJson.put("email", user.getEmail());
            userJson.put("name", user.getName());
            userJson.put("password", user.getPassword());
            userJson.put("id", user.getId());
            // Add any other necessary fields

            // Prepare the response data with token and user as JSON object
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("token", token);
            responseData.put("user", userJson);

        // Return success response as JSON
        return ResponseEntity.ok(responseData); // Return response as JSON

        // // Return success response
        // return ResponseEntity.ok(new LoginResponse(token, user));
        } catch (AuthenticationException e) {
            System.err.println("Authentication failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid credentials");
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace(); // Print stack trace to identify the root cause
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred");
        }
    }



    // Signup endpoint
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest signupRequest) {
        try {
            // Create user in the database
            User newUser = new Guest();
            newUser.setEmail(signupRequest.getEmail());
            newUser.setPassword(signupRequest.getPassword());
            newUser.setName(signupRequest.getName());

            userService.signup(newUser); // Call signup method from UserService

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
        private User user;

        public LoginResponse(String token, User user) {
            this.token = token;
            this.user = user;
        }

        public String getToken() {
            return token;
        }

        public User getUser() {
            return user;
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