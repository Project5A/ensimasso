package com.example.EnsimAsso.model.User;

import java.util.List;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;

@Entity
@DiscriminatorValue("GUEST")
public class Guest extends User {

    @Override
    public String getRole() {
        return "GUEST";
    }
    private String promo;

    @ManyToMany(mappedBy = "teamMembers")
    private List<Asso> adhesion; // A guest can be part of multiple associations

    public Guest() {
    }

    public Guest(String promo, List<Asso> adhesion) {
        this.promo = promo;
        this.adhesion = adhesion;
    }

    public String getPromo() {
        return promo;
    }

    public void setPromo(String promo) {
        this.promo = promo;
    }

    public List<Asso> getAdhesion() {
        return adhesion;
    }

    public void setAdhesion(List<Asso> adhesion) {
        this.adhesion = adhesion;
    }
}
