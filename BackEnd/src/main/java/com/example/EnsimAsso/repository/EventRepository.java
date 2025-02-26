package com.example.EnsimAsso.repository;

import com.example.EnsimAsso.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("SELECT DISTINCT e FROM Event e " +
        "JOIN FETCH e.organizer o " +
        "LEFT JOIN FETCH o.teamMembers " +
        "LEFT JOIN FETCH e.participants") // Ajout du fetch des participants
    List<Event> findAllWithOrganizerAndMembers();
    
    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.participants WHERE e.id = :id")
    Optional<Event> findByIdWithParticipants(@Param("id") Long id);

    @Query("SELECT DISTINCT e FROM Event e " +
       "JOIN FETCH e.organizer o " +
       "LEFT JOIN FETCH e.participants p") // Chargement explicite des participants
    List<Event> findAllWithParticipants();
}