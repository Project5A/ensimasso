// AssoController.java
package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.model.User.Asso;
import com.example.EnsimAsso.model.User.Guest;
import com.example.EnsimAsso.service.AssoService;
import com.example.EnsimAsso.service.BlobStorageService;
import com.example.EnsimAsso.service.GuestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assos")
@CrossOrigin(origins = "http://localhost:3000")
public class AssoController {

    @Autowired
    private AssoService assoService;

    @Autowired
    private BlobStorageService blobStorageService;
    
    @Autowired
    private GuestService guestService;

    // --- Endpoints existants pour la galerie ---

    @GetMapping
    public List<Asso> getAllAssoUsers() {
        return assoService.getAllAssoUsers();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAssoById(@PathVariable Integer id) {
        Asso asso = assoService.getAssoById(id);
        if (asso == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Asso non trouvé");
        }
        return ResponseEntity.ok(asso);
    }
    
    @PostMapping("/{id}/gallery")
    public ResponseEntity<?> uploadGalleryPhoto(@PathVariable Integer id,
                                                @RequestParam("file") MultipartFile file) {
        try {
            Asso asso = assoService.getAssoById(id);
            if (asso == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Asso non trouvé");
            }
            String photoUrl = blobStorageService.uploadFile(file);
            if (photoUrl == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de l'upload du fichier");
            }
            List<String> gallery = asso.getGallery();
            if (gallery == null) {
                gallery = new ArrayList<>();
            }
            gallery.add(photoUrl);
            asso.setGallery(gallery);
            assoService.saveAsso(asso);
            return ResponseEntity.ok(photoUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur : " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}/gallery")
    public ResponseEntity<?> deleteGalleryPhoto(@PathVariable Integer id,
                                                @RequestBody Map<String, String> payload) {
        try {
            String photoUrl = payload.get("photoUrl");
            Asso asso = assoService.getAssoById(id);
            if (asso == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Asso non trouvé");
            }
            List<String> gallery = asso.getGallery();
            if (gallery != null && gallery.remove(photoUrl)) {
                asso.setGallery(gallery);
                assoService.saveAsso(asso);
                return ResponseEntity.ok("Photo supprimée avec succès");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Photo non trouvée dans la galerie");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur : " + e.getMessage());
        }
    }
    
    // --- Nouveau endpoint pour l'adhésion ---
    
    /**
     * Permet à un guest d'adhérer à une association après paiement.
     * Le payload JSON doit contenir "guestId".
     */
    @PostMapping("/{assoId}/adhesion")
    public ResponseEntity<?> joinMembership(@PathVariable Integer assoId,
                                            @RequestBody Map<String, String> payload) {
        try {
            String guestIdStr = payload.get("guestId");
            if (guestIdStr == null) {
                return ResponseEntity.badRequest().body("L'identifiant du guest est requis");
            }
            Integer guestId = Integer.parseInt(guestIdStr);
            
            // Simulation du paiement de 10€
            // Dans une implémentation réelle, vous intégreriez ici votre solution de paiement (ex : Stripe, PayPal)
            System.out.println("Paiement de 10€ validé pour l'adhésion");
            
            Asso asso = assoService.getAssoById(assoId);
            if (asso == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Association non trouvée");
            }
            
            Guest guest = guestService.getGuestById(guestId);
            if (guest == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Guest non trouvé");
            }
            
            List<Guest> members = asso.getTeamMembers();
            if (members == null) {
                members = new ArrayList<>();
            }
            if (members.contains(guest)) {
                return ResponseEntity.badRequest().body("Vous êtes déjà membre de cette association");
            }
            members.add(guest);
            asso.setTeamMembers(members);
            assoService.saveAsso(asso);
            
            return ResponseEntity.ok("Adhésion réussie");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Erreur : " + e.getMessage());
        }
    }

    @GetMapping("/{id}/adhesions")
    public ResponseEntity<?> getAdhesionsForAsso(@PathVariable Integer id) {
        Asso asso = assoService.getAssoById(id);
        if (asso == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Association non trouvée");
        }
        List<Guest> members = asso.getTeamMembers();
        return ResponseEntity.ok(members);
    }
    

}
