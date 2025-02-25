package com.example.EnsimAsso.model.User;

import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@DiscriminatorValue("ASSO")
public class Asso extends User {

    @Override
    public String getRole() {
        return "ASSO";
    }
    
    private String bgPhoto;
    private String description;

    @ElementCollection
    private List<String> gallery;

    private String rib;

    // Côté propriétaire de la relation
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "asso_guest",
        joinColumns = @JoinColumn(name = "asso_id"),
        inverseJoinColumns = @JoinColumn(name = "guest_id")
    )
    @JsonManagedReference // Permet la sérialisation du côté asso
    private List<Guest> teamMembers;

    @Column(columnDefinition = "TEXT")
    private String socialMedia; // Exemple de JSON: {"instagram": "...", "facebook": "...", "linkedin": "..."}

    private double adhesionPrice;

    public Asso() {
        super();
    }

    public Asso(String bgPhoto, String description, List<String> gallery, String rib, List<Guest> teamMembers, String socialMedia, double adhesionPrice) {
        super();
        this.bgPhoto = bgPhoto;
        this.description = description;
        this.gallery = gallery;
        this.rib = rib;
        this.teamMembers = teamMembers;
        this.socialMedia = socialMedia;
        this.adhesionPrice = adhesionPrice;
    }

    // Getters et setters

    public String getBgPhoto() {
        return bgPhoto;
    }
    public void setBgPhoto(String bgPhoto) {
        this.bgPhoto = bgPhoto;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getGallery() {
        return gallery;
    }
    public void setGallery(List<String> gallery) {
        this.gallery = gallery;
    }

    public String getRib() {
        return rib;
    }
    public void setRib(String rib) {
        this.rib = rib;
    }

    public List<Guest> getTeamMembers() {
        return teamMembers;
    }
    public void setTeamMembers(List<Guest> teamMembers) {
        this.teamMembers = teamMembers;
    }

    public String getSocialMedia() {
        return socialMedia;
    }
    public void setSocialMedia(String socialMedia) {
        this.socialMedia = socialMedia;
    }

    public double getAdhesionPrice() {
        return adhesionPrice;
    }
    public void setAdhesionPrice(double adhesionPrice) {
        this.adhesionPrice = adhesionPrice;
    }
}
