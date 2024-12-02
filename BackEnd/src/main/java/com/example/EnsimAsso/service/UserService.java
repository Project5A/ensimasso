package com.example.EnsimAsso.service;

import com.example.EnsimAsso.model.User;
import com.example.EnsimAsso.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public User signup(User user) throws Exception {
        Optional<User> existingUser = userRepository.findByEmail(user.getEmail());
        if (existingUser.isPresent()) {
            throw new Exception("Email already registered.");
        }
        return userRepository.save(user);
    }

    public User login(String email, String password) throws Exception {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty() || !user.get().getPassword().equals(password)) {
            throw new Exception("Invalid email or password.");
        }
        return user.get();
    }
}
