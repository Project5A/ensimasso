package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.model.Post;
import com.example.EnsimAsso.repository.PostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@CrossOrigin(origins = "http://localhost:3000") // Allow requests from your React app
public class PostController {

    @Autowired
    private PostRepository postRepository;

    // Get all posts
    @GetMapping
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }
    @GetMapping("/test")
    public String testEndpoint() {
        return "API is working!";
    }
    // Add a new post
    @PostMapping
    public Post createPost(@RequestBody Post post) {
        return postRepository.save(post);
    }
}
