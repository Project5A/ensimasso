package com.example.EnsimAsso.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import java.sql.Date;
import java.util.Set;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.HashSet;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.example.EnsimAsso.model.User.Asso;
import com.example.EnsimAsso.model.User.Guest;

@Entity
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private Date date;  // Changed to type java.sql.Date
    private String location;
    private String description;
    private Double adhPrice;  // Added adhPrice attribute
    private Double nonAdhPrice;  // Added nonAdhPrice attribute
    private String eventImage;  // Attribute for event image (can be a file name or URL)

    @ManyToOne
    private Asso organizer;  // Association with the User entity (Organizer)

    // Change participants to a Set to avoid multiple bag fetch issue
    @ManyToMany
    @JoinTable(
       name = "event_participants",
       joinColumns = @JoinColumn(name = "event_id"),
       inverseJoinColumns = @JoinColumn(name = "guest_id")
    )
    @Fetch(FetchMode.SUBSELECT)
    private Set<Guest> participants = new HashSet<>();

    // Getters and setters...
    public Set<Guest> getParticipants() {
        return participants;
    }

    public void setParticipants(Set<Guest> participants) {
        this.participants = participants;
    }

    // Helper method
    public void addParticipant(Guest guest) {
        this.participants.add(guest);
    }


    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getAdhPrice() {
        return adhPrice;
    }

    public void setAdhPrice(Double adhPrice) {
        this.adhPrice = adhPrice;
    }

    public Double getNonAdhPrice() {
        return nonAdhPrice;
    }

    public void setNonAdhPrice(Double nonAdhPrice) {
        this.nonAdhPrice = nonAdhPrice;
    }

    public String getEventImage() {
        return eventImage;
    }

    public void setEventImage(String eventImage) {
        this.eventImage = eventImage;
    }

    public String getOrganizerName() {
        return organizer != null ? organizer.getName() : null;
    }


    @JsonProperty("organizerId")
    public Integer getOrganizerId() {
        return organizer != null ? organizer.getId() : null;
    }
    
    //set organizer name
    public void setOrganizerName(String organizerName) {
        if (organizer == null) {
            organizer = new Asso();
        }
        organizer.setName(organizerName);
    }

    public String getOrganizerPhoto() {
        return organizer != null ? organizer.getPhoto() : null;
    }

    //set organizer photo
    public void setOrganizerPhoto(String organizerPhoto) {
        if (organizer == null) {
            organizer = new Asso();
        }
        organizer.setPhoto(organizerPhoto);
    }
}
