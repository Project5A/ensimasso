package com.example.EnsimAsso.model.User;

import jakarta.persistence.Entity;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@DiscriminatorValue("GUEST")
public class Guest extends User {

    @Override
    public String getRole() {
        return "GUEST";
    }
    
    private String promo;
    private String statut;
    private String socialMedia; // Stocke le JSON pour les réseaux sociaux

    // Côté inverse de la relation ManyToMany
    @ManyToMany(mappedBy = "teamMembers", fetch = FetchType.EAGER)
    @JsonBackReference // Empêche la récursivité lors de la sérialisation
    private List<Asso> memberships;

    public Guest() {
        super();
    }

    public Guest(String promo, String statut, String socialMedia) {
        super();
        this.promo = promo;
        this.statut = statut;
        this.socialMedia = socialMedia;
    }

    // Getters et setters

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

    public List<Asso> getMemberships() {
        return memberships;
    }
    public void setMemberships(List<Asso> memberships) {
        this.memberships = memberships;
    }
}
