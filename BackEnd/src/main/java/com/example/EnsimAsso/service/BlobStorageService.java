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
    
    
}
