package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.model.User.User;
import com.example.EnsimAsso.service.BlobStorageService;
// import com.example.EnsimAsso.service.AzureStorageService; // Ensure this class exists or remove if not needed
import com.example.EnsimAsso.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private BlobStorageService blobStorageService;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody User user) {
        try {
            User newUser = userService.signup(user);
            return ResponseEntity.ok(newUser);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {
        try {
            User loggedInUser = userService.login(user.getEmail(), user.getPassword());
            return ResponseEntity.ok(loggedInUser);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // New endpoint for updating a user profile
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User userUpdate) {
        try {
            User updatedUser = userService.updateUser(id, userUpdate);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // UserController.java
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        try {
            User user = userService.getUserById(id);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // UserController.java
    @GetMapping("/users/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        try {
            String email = authentication.getName(); // Get the email from the authentication object
            User user = userService.getUserByEmail(email);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/users/{id}/photo")
    public ResponseEntity<?> uploadUserPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            // Étape 1: Upload sur Azure Blob Storage
            String photoUrl = blobStorageService.uploadFile(file);  
            if (photoUrl == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur lors de l'upload du fichier");
            }
    
            // Étape 2: Récupération du User
            User user = userService.getUserById(id);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé");
            }
    
            // Étape 3: Mise à jour de l'URL de la photo
            user.setPhoto(photoUrl);
            userService.saveUser(user);  // 🔥 **Sauvegarde explicite en base de données**
    
            System.out.println("Photo URL mise à jour: " + user.getPhoto()); // Debug console
    
            return ResponseEntity.ok(user);  // Retourne l'utilisateur mis à jour
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur lors du téléversement : " + e.getMessage());
        }
    }
  

    
}