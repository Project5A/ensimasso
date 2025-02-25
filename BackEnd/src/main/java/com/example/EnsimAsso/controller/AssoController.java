// AssoController.java
package com.example.EnsimAsso.controller;

import com.example.EnsimAsso.model.User.Asso;
import com.example.EnsimAsso.service.AssoService;
import com.example.EnsimAsso.service.BlobStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/assos")
@CrossOrigin(origins = "http://localhost:3000")
public class AssoController {

    @Autowired
    private AssoService assoService;

    @Autowired
    private BlobStorageService blobStorageService;

    // --- Endpoint existants ---

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

    // --- Endpoint pour la gestion de la galerie ---

    /**
     * Upload d'une nouvelle photo dans la galerie de l'asso.
     * L'endpoint attend un fichier dans une requête multipart/form-data.
     */
    @PostMapping("/{id}/gallery")
    public ResponseEntity<?> uploadGalleryPhoto(@PathVariable Integer id,
                                                @RequestParam("file") MultipartFile file) {
        try {
            Asso asso = assoService.getAssoById(id);
            if (asso == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Asso non trouvé");
            }
            // Utilisation du service Azure Blob Storage pour uploader le fichier
            String photoUrl = blobStorageService.uploadFile(file);
            if (photoUrl == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Erreur lors de l'upload du fichier");
            }
            // Récupération ou initialisation de la galerie
            List<String> gallery = asso.getGallery();
            if (gallery == null) {
                gallery = new ArrayList<>();
            }
            // Ajout de l'URL de la nouvelle photo
            gallery.add(photoUrl);
            asso.setGallery(gallery);
            assoService.saveAsso(asso);
            return ResponseEntity.ok(photoUrl);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur : " + e.getMessage());
        }
    }

    /**
     * Suppression d'une photo de la galerie de l'asso.
     * L'endpoint attend un JSON dans le corps avec la clé "photoUrl".
     */
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
                // Ici, vous pouvez également ajouter la suppression du fichier dans Azure Blob Storage si nécessaire.
                return ResponseEntity.ok("Photo supprimée avec succès");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Photo non trouvée dans la galerie");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur : " + e.getMessage());
        }
    }
}
