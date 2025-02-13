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

    public Post createPost(String title, String description, String author, String date, String image) {
        Post post = new Post();
        post.setTitle(title);
        post.setDescription(description);
        post.setAuthor(author);
        post.setDate(date);
        post.setImage(image);
        return postRepository.save(post);
    }

    public Post updatePost(Long id, Post postDetails) {
        Post post = getPostById(id);
        post.setTitle(postDetails.getTitle());
        post.setDescription(postDetails.getDescription());
        post.setAuthor(postDetails.getAuthor());
        post.setDate(postDetails.getDate());
        post.setAvatar(postDetails.getAvatar());
        post.setImage(postDetails.getImage());
        // We leave reactions and comments unchanged here.
        return postRepository.save(post);
    }
    

    public void deletePost(Long id) {
        Post post = getPostById(id);
        postRepository.delete(post);
    }

    @Transactional
    public Post reactToPost(Long id, String reactionType) {
        logger.info("Adding reaction '{}' to post with ID: {}", reactionType, id);
        Post post = getPostById(id);
        post.addReaction(reactionType);
        return postRepository.save(post);
    }
}
