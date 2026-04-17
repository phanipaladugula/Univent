package com.univent.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PostConstruct;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name:univent-id-cards}")
    private String bucketName;

    @PostConstruct
    public void init() {
        try {
            ensureBucketExists();
            log.info("MinIO bucket ready: {}", bucketName);
        } catch (Exception e) {
            throw new RuntimeException("MinIO bucket init failed", e);
        }
    }

    // Method for regular MultipartFile upload
    public String uploadFile(MultipartFile file, UUID userId) {
        try {
            String fileName = userId.toString() + "/" + UUID.randomUUID().toString() + ".enc";

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType("application/octet-stream")
                    .build());

            log.info("Uploaded file: {} for user: {}", fileName, userId);
            return fileName;

        } catch (Exception e) {
            log.error("Failed to upload file: {}", e.getMessage());
            throw new RuntimeException("Failed to upload ID card", e);
        }
    }

    // Upload encrypted byte array directly (ONLY ONE COPY OF THIS METHOD)
    public String uploadEncryptedData(byte[] encryptedData, String originalFilename, UUID userId) {
        try {
            // Generate unique filename
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = userId.toString() + "/" + UUID.randomUUID().toString() + extension + ".enc";

            // Upload encrypted data
            try (ByteArrayInputStream bais = new ByteArrayInputStream(encryptedData)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .stream(bais, encryptedData.length, -1)
                        .contentType("application/octet-stream")
                        .build());
            }

            log.info("Uploaded encrypted data: {} for user: {}", fileName, userId);
            return fileName;

        } catch (Exception e) {
            log.error("Failed to upload encrypted data: {}", e.getMessage());
            throw new RuntimeException("Failed to upload ID card", e);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucketName)
                .build());

        if (!bucketExists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build());
            log.info("Created bucket: {}", bucketName);
        }
    }

    public InputStream downloadFile(String filePath) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(filePath)
                    .build());
        } catch (Exception e) {
            log.error("Failed to download file: {}", e.getMessage());
            throw new RuntimeException("Failed to download ID card", e);
        }
    }

    public void deleteFile(String filePath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(filePath)
                    .build());
            log.info("Deleted file: {}", filePath);
        } catch (Exception e) {
            log.error("Failed to delete file: {}", e.getMessage());
        }
    }

    public String getPresignedUrl(String filePath, int expiryMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucketName)
                    .object(filePath)
                    .method(Method.GET)
                    .expiry(expiryMinutes, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL: {}", e.getMessage());
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }
}