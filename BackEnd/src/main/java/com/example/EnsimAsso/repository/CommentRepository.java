package com.example.EnsimAsso.repository;

import com.example.EnsimAsso.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Recherche des commentaires liés à un post spécifique
    List<Comment> findByPostId(Long postId);

    // Optionnel : Recherche des commentaires d'un utilisateur spécifique
    List<Comment> findByAuthor(String author);

    // Optionnel : Recherche des commentaires contenant un mot-clé spécifique
    List<Comment> findByContentContaining(String keyword);
}
