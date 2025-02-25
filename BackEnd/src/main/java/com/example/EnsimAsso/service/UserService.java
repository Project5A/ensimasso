package com.example.EnsimAsso.service;

import com.example.EnsimAsso.model.User.Asso;
import com.example.EnsimAsso.model.User.Guest;
import com.example.EnsimAsso.model.User.User;
import com.example.EnsimAsso.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;  // Inject PasswordEncoder

    public User signup(User user) throws Exception {
        Optional<User> existingUser = userRepository.findByEmail(user.getEmail());
        if (existingUser.isPresent()) {
            throw new Exception("Email already registered.");
        }

        // Encrypt the user's password before saving it
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return userRepository.save(user);
    }

    public User login(String email, String password) throws Exception {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty() || !passwordEncoder.matches(password, user.get().getPassword())) {
            throw new Exception("Invalid email or password.");
        }
        return user.get();
    }

    public User updateUser(Long id, User updatedUser) throws Exception {
        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isEmpty()) {
            throw new Exception("User not found");
        }
        User user = optionalUser.get();
        
        // Mise à jour des champs communs
        user.setName(updatedUser.getName());
        user.setEmail(updatedUser.getEmail());
        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
        }
        user.setPhoto(updatedUser.getPhoto());
        user.setDateNaissance(updatedUser.getDateNaissance());
        
        // Mise à jour pour Guest
        if (user instanceof Guest && updatedUser instanceof Guest) {
            Guest guest = (Guest) user;
            Guest updatedGuest = (Guest) updatedUser;
            guest.setPromo(updatedGuest.getPromo());
            guest.setStatut(updatedGuest.getStatut());
            // Mettez à jour le champ socialMedia pour Guest
            guest.setSocialMedia(updatedGuest.getSocialMedia());
        }
        
        // Mise à jour pour Asso
        if (user instanceof Asso && updatedUser instanceof Asso) {
            Asso asso = (Asso) user;
            Asso updatedAsso = (Asso) updatedUser;
            asso.setBgPhoto(updatedAsso.getBgPhoto());
            asso.setDescription(updatedAsso.getDescription());
            asso.setGallery(updatedAsso.getGallery());
            asso.setRib(updatedAsso.getRib());
            asso.setSocialMedia(updatedAsso.getSocialMedia());
            asso.setAdhesionPrice(updatedAsso.getAdhesionPrice());
        }
        
        return userRepository.save(user);
    }    


    // UserService.java
    public User getUserById(Long id) throws Exception {
        Optional<User> user = userRepository.findById(id);
        if (user.isEmpty()) {
            throw new Exception("User not found");
        }
        return user.get();
    }

    // UserService.java
    public User getUserByEmail(String email) throws Exception {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            throw new Exception("User not found");
        }
        return user.get();
    }

    public void saveUser(User user) {
        userRepository.save(user);
    }
    
}