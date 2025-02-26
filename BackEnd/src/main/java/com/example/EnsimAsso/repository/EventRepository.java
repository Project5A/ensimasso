package com.example.EnsimAsso.repository;

import com.example.EnsimAsso.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("SELECT DISTINCT e FROM Event e " +
           "JOIN FETCH e.organizer o " +
           "LEFT JOIN FETCH o.teamMembers")
    List<Event> findAllWithOrganizerAndMembers();
}
