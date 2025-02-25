package com.example.EnsimAsso.model.User;

import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;

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

    @ManyToMany
    @JoinTable(
        name = "asso_guest",
        joinColumns = @JoinColumn(name = "asso_id"),
        inverseJoinColumns = @JoinColumn(name = "guest_id")
    )
    private List<Guest> teamMembers;

    @Column(columnDefinition = "TEXT")
    private String socialMedia; // Par exemple, un JSON: {"instagram": "...", "facebook": "...", "linkedin": "..."}

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
