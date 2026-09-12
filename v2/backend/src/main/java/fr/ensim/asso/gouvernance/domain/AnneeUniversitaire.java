package fr.ensim.asso.gouvernance.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Année universitaire. Sert d'étiquette lisible et de référence de calendrier
 * — ce n'est <em>pas</em> la clé d'un mandat : voir {@link Mandat#getPeriode()}.
 */
@Entity
@Table(name = "annee_universitaire")
public class AnneeUniversitaire {

    @Id
    @Column(length = 9)
    private String code;                 // « 2025-2026 »

    @Column(nullable = false)
    private LocalDate debut;

    @Column(nullable = false)
    private LocalDate fin;

    protected AnneeUniversitaire() { }

    public AnneeUniversitaire(String code, LocalDate debut, LocalDate fin) {
        this.code = code;
        this.debut = debut;
        this.fin = fin;
    }

    public String getCode() { return code; }
    public LocalDate getDebut() { return debut; }
    public LocalDate getFin() { return fin; }

    public boolean contient(LocalDate date) {
        return !date.isBefore(debut) && !date.isAfter(fin);
    }

    /** « 2025-2026 » -> « 2026-2027 ». */
    public static String anneeSuivante(String code) {
        String[] p = code.split("-");
        return (Integer.parseInt(p[0]) + 1) + "-" + (Integer.parseInt(p[1]) + 1);
    }
}
