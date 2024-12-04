package com.example.EnsimAsso.service;

import com.example.EnsimAsso.model.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public EventService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Create the events table (If it doesn't exist already)
    public void createEventsTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS events (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "title VARCHAR(255)," +
                "date VARCHAR(255)," +
                "location VARCHAR(255)," +
                "description VARCHAR(255))";
        
        jdbcTemplate.execute(createTableSQL);
    }

    // Insert a new event
    public void insertEvent(Event event) {
        String insertSQL = "INSERT INTO events (title, date, location, description) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(insertSQL, event.getTitle(), event.getDate(), event.getLocation(), event.getDescription());
    }

    // Retrieve all events
    public List<Event> getAllEvents() {
        String selectSQL = "SELECT * FROM events";
        return jdbcTemplate.query(selectSQL, (rs, rowNum) -> {
            Event event = new Event();
            event.setId(rs.getLong("id"));
            event.setTitle(rs.getString("title"));
            event.setDate(rs.getString("date"));
            event.setLocation(rs.getString("location"));
            event.setDescription(rs.getString("description"));
            return event;
        });
    }
}
