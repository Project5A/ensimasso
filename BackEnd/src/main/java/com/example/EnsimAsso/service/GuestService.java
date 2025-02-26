package com.example.EnsimAsso.service;

import com.example.EnsimAsso.model.User.Guest;
import com.example.EnsimAsso.repository.GuestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class GuestService {

    @Autowired
    private GuestRepository guestRepository;
    
    public Guest getGuestById(Integer id) {
       Optional<Guest> guest = guestRepository.findById(id);
       return guest.orElse(null);
    }
    
    public void saveGuest(Guest guest) {
        guestRepository.save(guest);
    }


}

