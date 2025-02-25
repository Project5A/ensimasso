package com.example.EnsimAsso.model.User;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("GUEST")
public class Guest extends User {

    @Override
    public String getRole() {
        return "GUEST";
    }
    
    private String promo;
    private String statut;
    // Nouveau champ pour stocker les réseaux sociaux sous forme de JSON
    private String socialMedia;

    public Guest() {
        super();
    }

    public Guest(String promo, String statut, String socialMedia) {
        super();
        this.promo = promo;
        this.statut = statut;
        this.socialMedia = socialMedia;
    }

    public String getPromo() {
        return promo;
    }
    public void setPromo(String promo) {
        this.promo = promo;
    }

    public String getStatut() {
        return statut;
    }
    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getSocialMedia() {
        return socialMedia;
    }
    public void setSocialMedia(String socialMedia) {
        this.socialMedia = socialMedia;
    }
}
