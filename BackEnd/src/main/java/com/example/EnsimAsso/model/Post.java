package com.example.EnsimAsso.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A simple date string (e.g., "2025-02-12") provided by the front-end
    private String date;

    private String title;
    private String description;
    private String author;

    // Optional fields for display
    private String avatar;   // URL or file path to the avatar image
    private String image;    // URL or file path to the post's image

    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;

    // Instead of separate fields for like/dislike, we use a map for reaction counts.
    @ElementCollection
    @CollectionTable(name="post_reactions", joinColumns=@JoinColumn(name="post_id"))
    @MapKeyColumn(name="reaction_type")
    @Column(name="count")
    private Map<String, Integer> reactions = new HashMap<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<Comment> comments = new ArrayList<>();

    public Post() {}

    public Post(String title, String description, String author) {
        this.title = title;
        this.description = description;
        this.author = author;
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Map<String, Integer> getReactions() { return reactions; }
    public void setReactions(Map<String, Integer> reactions) { this.reactions = reactions; }

    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }

    // Helper methods for managing comments
    public void addComment(Comment comment) {
        comments.add(comment);
        comment.setPost(this);
    }

    public void removeComment(Comment comment) {
        comments.remove(comment);
        comment.setPost(null);
    }

    // Helper method for reacting
    public void addReaction(String reactionType) {
        reactions.put(reactionType, reactions.getOrDefault(reactionType, 0) + 1);
    }
}
