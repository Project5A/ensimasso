package com.example.EnsimAsso.service;

import com.example.EnsimAsso.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository; // Ensure this repository is defined and returns your User entity

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Adjust this if you use username or email for login
        com.example.EnsimAsso.model.User.User appUser = userRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
        
        // Return a Spring Security User instance. Customize authorities/roles as needed.
        return new org.springframework.security.core.userdetails.User(appUser.getEmail(), appUser.getPassword(), Collections.emptyList());
    }
}
