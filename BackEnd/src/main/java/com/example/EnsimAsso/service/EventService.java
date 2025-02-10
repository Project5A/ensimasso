package com.example.EnsimAsso.service;

import com.example.EnsimAsso.model.Event;
import com.example.EnsimAsso.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EventService {

    private final EventRepository eventRepository;

    @Autowired
    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    // Create a new Event
    public Event createEvent(Event event) {
        return eventRepository.save(event);
    }

    // Get all Events
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    // Get Event by ID
    public Optional<Event> getEventById(Long id) {
        return eventRepository.findById(id);
    }

    // Update an existing Event
    public Event updateEvent(Long id, Event updatedEvent) {
        // Check if the Event exists
        Optional<Event> existingEvent = eventRepository.findById(id);
        if (existingEvent.isPresent()) {
            Event event = existingEvent.get();
            event.setTitle(updatedEvent.getTitle());
            event.setDate(updatedEvent.getDate());
            event.setLocation(updatedEvent.getLocation());
            event.setDescription(updatedEvent.getDescription());
            event.setAdhPrice(updatedEvent.getAdhPrice());
            event.setNonAdhPrice(updatedEvent.getNonAdhPrice());
            event.setEventImage(updatedEvent.getEventImage());
            event.setOrganizerName(updatedEvent.getOrganizerName());
            event.setOrganizerPhoto(updatedEvent.getOrganizerPhoto());
            return eventRepository.save(event);
        }
        return null;  // If the event with the given id does not exist
    }

    // Delete an Event by ID
    public boolean deleteEvent(Long id) {
        if (eventRepository.existsById(id)) {
            eventRepository.deleteById(id);
            return true;
        }
        return false;  // Event not found
    }
}
