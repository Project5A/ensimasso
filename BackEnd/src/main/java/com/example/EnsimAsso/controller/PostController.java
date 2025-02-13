package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.model.Comment;
import com.example.EnsimAsso.model.Post;
import com.example.EnsimAsso.model.User.User;
import com.example.EnsimAsso.repository.CommentRepository;
import com.example.EnsimAsso.repository.PostRepository;
import com.example.EnsimAsso.repository.UserRepository;
import com.example.EnsimAsso.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@CrossOrigin(origins = "*")
public class PostController {

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;
    
    @Autowired
    private UserRepository userRepository;

    // Get all posts
    @GetMapping
    public List<Post> getAllPosts() {
        return postService.getAllPosts();
    }

    // Get a post by ID
    @GetMapping("/{id}")
    public ResponseEntity<Post> getPostById(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getPostById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Post> createPost(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("date") String date,
            @RequestParam("image") String image,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Get the username (which is typically the email)
        String email = userDetails.getUsername();

        // Retrieve the user from the repository
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = postService.createPost(title, description, user, date, image);
        return ResponseEntity.ok(post);
    }


    // Update an existing post
    @PutMapping("/{id}")
    public ResponseEntity<Post> updatePost(@PathVariable Long id, @RequestBody Post postDetails) {
        return ResponseEntity.ok(postService.updatePost(id, postDetails));
    }

    // Endpoint to react to a post (expects JSON like { "reaction": "Like" })
    @PostMapping("/{id}/react")
    public ResponseEntity<Map<String, Integer>> reactToPost(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reaction = body.get("reaction");
        Post updatedPost = postService.reactToPost(id, reaction);
        return ResponseEntity.ok(updatedPost.getReactions());
    }

    // Endpoint to add a comment to a post.
    // The front-end sends a JSON object with at least a "response" field and optionally "author"
    @PostMapping("/{postId}/response")
    public ResponseEntity<Post> addComment(@PathVariable Long postId, 
                                           @RequestBody Map<String, String> commentData,
                                           Principal principal) {
        // Retrieve the post
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found with ID: " + postId));
    
        // Retrieve the connected user from the Principal
        User user = userRepository.findByEmail(principal.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
    
        // Create and save the comment, setting the user instead of a plain author string
        Comment comment = new Comment();
        comment.setContent(commentData.get("response"));
        comment.setUser(user);
        comment.setPost(post);
        commentRepository.save(comment);
        
        // Re-fetch the post from the database to get the latest data (including comments)
        Post updatedPost = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found after update"));
        
        return ResponseEntity.ok(updatedPost);
    }    

    // (Optional) Endpoint to get all comments for a post
    @GetMapping("/{postId}/comments")
    public ResponseEntity<List<Comment>> getCommentsByPost(@PathVariable Long postId) {
        List<Comment> comments = commentRepository.findByPostId(postId);
        return ResponseEntity.ok(comments);
    }
}
