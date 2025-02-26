// PaymentController.java
package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.model.Event;
import com.example.EnsimAsso.model.User.Guest;
import com.example.EnsimAsso.repository.EventRepository;
import com.example.EnsimAsso.repository.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.example.EnsimAsso.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "http://localhost:3000")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/create-payment-intent")
    public ResponseEntity<Map<String, String>> createPaymentIntent(@RequestBody Map<String, Object> data) {
        try {
            long amount = ((Number) data.get("amount")).longValue();
            String currency = (String) data.get("currency");
            PaymentIntent intent = paymentService.createPaymentIntent(amount, currency);
            Map<String, String> responseData = new HashMap<>();
            responseData.put("clientSecret", intent.getClientSecret());
            return ResponseEntity.ok(responseData);
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    // Nouvel endpoint pour confirmer le paiement et enregistrer la participation
    @PostMapping("/confirm")
    public ResponseEntity<?> confirmPayment(@RequestParam Long eventId, @RequestParam Integer guestId) {
        try {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new RuntimeException("Event not found with ID: " + eventId));
            // Note: ensure your userRepository returns a Guest instance!
            Guest guest = (Guest) userRepository.findById((long) guestId)
                    .orElseThrow(() -> new RuntimeException("Guest not found with ID: " + guestId));
            event.addParticipant(guest);
            eventRepository.save(event);
            return ResponseEntity.ok("Participation enregistrée avec succès");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur : " + e.getMessage());
        }
    }

}
