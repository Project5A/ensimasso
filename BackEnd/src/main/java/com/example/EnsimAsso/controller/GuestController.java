// GuestController.java
package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.model.User.Asso;
import com.example.EnsimAsso.model.User.Guest;
import com.example.EnsimAsso.service.GuestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/guests")
@CrossOrigin(origins = "http://localhost:3000")
public class GuestController {

    @Autowired
    private GuestService guestService;
    
    @GetMapping("/{id}/adhesions")
    public ResponseEntity<?> getMembershipsForGuest(@PathVariable Integer id) {
        Guest guest = guestService.getGuestById(id);
        if (guest == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Guest non trouvé");
        }
        List<Asso> memberships = guest.getMemberships();
        return ResponseEntity.ok(memberships);
    }

}
