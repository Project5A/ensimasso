package com.example.EnsimAsso.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import com.fasterxml.jackson.annotation.JsonBackReference;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String content;

    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;

    // Instead of storing the author's email or name as a string,
    // we store a reference to the User entity.
    @ManyToOne
    @JoinColumn(name = "user_id")
    private com.example.EnsimAsso.model.User.User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "post_id", nullable = false)
    @JsonBackReference
    private Post post;

    public Comment() {}

    public Comment(String content, com.example.EnsimAsso.model.User.User user, Post post) {
        this.content = content;
        this.user = user;
        this.post = post;
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public com.example.EnsimAsso.model.User.User getUser() {
        return user;
    }

    public void setUser(com.example.EnsimAsso.model.User.User user) {
        this.user = user;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }
}
