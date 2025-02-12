package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.model.Comment;
import com.example.EnsimAsso.model.Post;
import com.example.EnsimAsso.repository.CommentRepository;
import com.example.EnsimAsso.repository.PostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
    @RequestMapping("/api/comments")
    public class CommentController {
    @Autowired
    private PostRepository postRepository;
        @Autowired
        private CommentRepository commentRepository;

    // Ajouter un commentaire à un post
    @PostMapping(value = "/{postId}/comment", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Post addComment(@PathVariable Long postId, @RequestBody Map<String, String> commentData) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));

        // Créer un nouvel objet Comment
        Comment comment = new Comment();
        comment.setContent(commentData.get("content"));
        comment.setAuthor(commentData.get("author"));
        comment.setPost(post);

        // Sauvegarder le commentaire
        commentRepository.save(comment);

        // Ajouter le commentaire à la liste des commentaires du post
        post.addComment(comment);

        return postRepository.save(post);
    }

    // Récupérer tous les commentaires d'un post spécifique
    @GetMapping("/{postId}/comments")
    public List<Comment> getCommentsByPost(@PathVariable Long postId) {
        return commentRepository.findByPostId(postId);
    }
    }

