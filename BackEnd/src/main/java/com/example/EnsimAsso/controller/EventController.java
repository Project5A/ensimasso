package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.model.Event;
import com.example.EnsimAsso.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    @Autowired
    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    // Initialize the events table
    @PostMapping("/init")
    public String createEventsTable() {
        eventService.createEventsTable();
        return "Table created or already exists";
    }

    // Add a new event
    @PostMapping("/add")
    public String addEvent(@RequestBody Event event) {
        eventService.insertEvent(event);
        return "Event added successfully!";
    }

    // Get all events
    @GetMapping("/all")
    public List<Event> getAllEvents() {
        return eventService.getAllEvents();
    }
}
