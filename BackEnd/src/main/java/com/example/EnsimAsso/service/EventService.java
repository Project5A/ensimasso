package com.example.EnsimAsso.service;

import com.example.EnsimAsso.model.Event;
import com.example.EnsimAsso.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;


    public Event getEventById(Long id) {
        // Vous pouvez aussi ajouter une méthode findByIdWithOrganizerAndMembers pour un seul event
        return eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with ID: " + id));
    }

    // Create a new event
    public Event createEvent(Event event) {
        return eventRepository.save(event);
    }

    public List<Event> getAllEvents() {
        return eventRepository.findAllWithParticipants();
    }
}
