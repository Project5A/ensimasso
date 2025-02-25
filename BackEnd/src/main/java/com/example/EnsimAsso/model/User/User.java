package com.example.EnsimAsso.model.User;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.*;
import java.time.LocalDate;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "role",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Guest.class, name = "GUEST"),
    @JsonSubTypes.Type(value = Asso.class, name = "ASSO")
})
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role", discriminatorType = DiscriminatorType.STRING)
public abstract class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private String email;
    private String password;
    private String photo; // URL de la photo

    // Nouvelle colonne pour stocker la date de naissance
    private LocalDate dateNaissance;

    public User() {
    }

    public User(Integer id, String name, String email, String password, String photo, LocalDate dateNaissance) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.photo = photo;
        this.dateNaissance = dateNaissance;
    }

    public Integer getId() {
        return id;
    }
    public void setId(Integer id) { this.id = id; }

    public String getName() {
        return name;
    }
    public void setName(String name) { this.name = name; }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() {
        return password;
    }
    public void setPassword(String password) { this.password = password; }

    public String getPhoto() {
        return photo;
    }
    public void setPhoto(String photo) { this.photo = photo; }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }
    public void setDateNaissance(LocalDate dateNaissance) { this.dateNaissance = dateNaissance; }

    // Méthode helper pour calculer l'âge
    public Integer getAge() {
        if (dateNaissance == null) return null;
        return LocalDate.now().getYear() - dateNaissance.getYear();
    }

    public abstract String getRole();
}
