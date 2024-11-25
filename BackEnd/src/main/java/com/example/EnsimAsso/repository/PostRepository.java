package com.example.EnsimAsso.repository;

import com.example.EnsimAsso.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
} 
