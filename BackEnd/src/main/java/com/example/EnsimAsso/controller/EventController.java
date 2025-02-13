package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.model.Event;
import com.example.EnsimAsso.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "http://localhost:3000")  // Allow requests from your React frontend
public class EventController {

    @Autowired
    private EventService eventService;

    // Endpoint to get all events
    @GetMapping
    public List<Event> getAllEvents() {
        return eventService.getAllEvents();
    }

    // Endpoint to get an event by ID
    @GetMapping("/{id}")
    public Event getEventById(@PathVariable Long id) {
        return eventService.getEventById(id);
    }

    // Endpoint to create a new event
    @PostMapping
    public Event createEvent(@RequestBody Event event) {
        return eventService.createEvent(event);
    }
}
