package com.example.EnsimAsso.repository;

import com.example.EnsimAsso.model.User.Asso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssoRepository extends JpaRepository<Asso, Integer> {
}
