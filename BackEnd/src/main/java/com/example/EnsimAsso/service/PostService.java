package com.example.EnsimAsso.service;

import com.example.EnsimAsso.model.Post;
import com.example.EnsimAsso.repository.PostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@Service
public class PostService {

    private static final Logger logger = LoggerFactory.getLogger(PostService.class);

    @Autowired
    private PostRepository postRepository;

    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    public Post getPostById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with ID: " + id));
    }

    public Post createPost(Post post) {
        return postRepository.save(post);
    }

    public Post updatePost(Long id, Post postDetails) {
        Post post = getPostById(id);
        post.setTitle(postDetails.getTitle());
        post.setDescription(postDetails.getDescription());
        post.setauthor(postDetails.getauthor()); // Correction ici
        return postRepository.save(post);
    }

    public void deletePost(Long id) {
        Post post = getPostById(id);
        postRepository.delete(post);
    }

    @Transactional
    public void likePost(Long id) {
        logger.info("Tentative de like pour le post avec ID: {}", id);
        Post post = getPostById(id);
        post.setLikes(post.getLikes() + 1);
        postRepository.save(post);
        logger.info("Like ajouté avec succès pour le post ID: {}", id);
    }

    @Transactional
    public void dislikePost(Long id) {
        logger.info("Tentative de dislike pour le post avec ID: {}", id);
        Post post = getPostById(id);
        post.setDislikes(post.getDislikes() + 1);
        postRepository.save(post);
        logger.info("Dislike ajouté avec succès pour le post ID: {}", id);
    }
}
