package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.model.Comment;
import com.example.EnsimAsso.model.Post;
import com.example.EnsimAsso.model.User.User;
import com.example.EnsimAsso.repository.CommentRepository;
import com.example.EnsimAsso.repository.PostRepository;
import com.example.EnsimAsso.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommentRepository commentRepository;
    
    // Inject messaging template if you want to broadcast updates
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Add a comment to a post and broadcast the updated post
    @PostMapping(value = "/{postId}/comment", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Post addComment(@PathVariable Long postId, 
                           @RequestBody Map<String, String> commentData, 
                           Principal principal) {
        // Retrieve the user using the email from the Principal
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Retrieve the post
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // Create and save the comment with the connected user
        Comment comment = new Comment();
        comment.setContent(commentData.get("content"));
        comment.setUser(user);
        comment.setPost(post);
        commentRepository.save(comment);
        
        // Add the comment to the post and update it
        post.addComment(comment);
        Post updatedPost = postRepository.save(post);
        
        // Optionally broadcast the updated post to live subscribers
        messagingTemplate.convertAndSend("/topic/posts", updatedPost);
        
        return updatedPost;
    }

    // Retrieve all comments for a given post
    @GetMapping("/{postId}/comments")
    public List<Comment> getCommentsByPost(@PathVariable Long postId) {
        return commentRepository.findByPostId(postId);
    }
}
