package com.example.EnsimAsso.service;

import com.azure.storage.blob.*;
import com.azure.storage.blob.models.BlobHttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.azure.storage.blob.sas.*;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

@Service
public class BlobStorageService {

    private final BlobContainerClient blobContainerClient;

    public BlobStorageService(
            @Value("${azure.storage.connection-string}") String connectionString,
            @Value("${azure.storage.container-name}") String containerName) {
        // Crée le client du service blob à partir de la chaîne de connexion
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        // Obtient (ou crée si inexistant) le conteneur de stockage
        this.blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!blobContainerClient.exists()) {
            blobContainerClient.create();
        }
    }
    
    public String uploadFile(MultipartFile file) {
        try {
            // Générer un nom de fichier unique
            String fileName = UUID.randomUUID().toString() + "-" + file.getOriginalFilename();
            BlobClient blobClient = blobContainerClient.getBlobClient(fileName);
    
            try (InputStream is = file.getInputStream()) {
                blobClient.upload(is, file.getSize(), true);
            }
    
            // Définir le content-type
            BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(file.getContentType());
            blobClient.setHttpHeaders(headers);
    
            // 🔥 Générer un SAS Token valable 24h
            BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
            OffsetDateTime expiryTime = OffsetDateTime.now().plus(24, ChronoUnit.HOURS);
            BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(expiryTime, permission);
    
            String sasToken = blobClient.generateSas(values);
            String fileUrlWithSas = blobClient.getBlobUrl() + "?" + sasToken;
    
            System.out.println("Fichier uploadé avec accès sécurisé: " + fileUrlWithSas);
            return fileUrlWithSas; // Retourne l'URL sécurisée
    
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // BlobStorageService.java (ajout d'une méthode deleteFile)
    public boolean deleteFile(String fileUrlWithSas) {
        try {
            // Extraire le nom du blob à partir de l'URL
            // Par exemple, si l'URL est de la forme "https://<account>.blob.core.windows.net/<container>/<blob>?<sas-token>"
            // On peut extraire le nom du blob en supprimant la partie avant le nom du container.
            URI uri = new URI(fileUrlWithSas);
            String path = uri.getPath(); // /container/blobName
            String[] segments = path.split("/");
            if (segments.length < 3) {
                return false;
            }
            // Le blob name se trouve après le container name
            String blobName = path.substring(path.indexOf(segments[2]));
            BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
            blobClient.delete();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

        
    
}
