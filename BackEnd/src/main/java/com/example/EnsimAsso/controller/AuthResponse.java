package com.example.EnsimAsso.controller;

public class AuthResponse {

    private String token;

    // Constructor
    public AuthResponse(String token) {
        this.token = token;
    }

    // Getter and Setter
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
