package com.example.EnsimAsso.repository;

import com.example.EnsimAsso.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    // Retrieve comments by the post's ID
    List<Comment> findByPostId(Long postId);
}
