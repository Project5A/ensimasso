package com.example.EnsimAsso.service;

import com.example.EnsimAsso.model.User.Asso;
import com.example.EnsimAsso.repository.AssoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AssoService {

    @Autowired
    private AssoRepository assoRepository;

    // Get all Asso users
    public List<Asso> getAllAssoUsers() {
        return assoRepository.findAll();
    }

    // Get Asso user by ID
    public Asso getAssoById(Integer id) {
        Optional<Asso> asso = assoRepository.findById(id);
        return asso.orElse(null);
    }
}
