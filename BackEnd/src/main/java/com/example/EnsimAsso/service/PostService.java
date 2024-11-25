package com.example.EnsimAsso.service;



import com.example.EnsimAsso.model.Post;

import com.example.EnsimAsso.repository.PostRepository;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;



import java.util.List;



@Service

public class PostService {

    @Autowired

    private PostRepository postRepository;



    public List<Post> getAllPosts() {

        return postRepository.findAll();

    }



    public Post addPost(Post post) {

        return postRepository.save(post);

    }



    public Post savePost(Post post) {

        return postRepository.save(post);

    }

} 


