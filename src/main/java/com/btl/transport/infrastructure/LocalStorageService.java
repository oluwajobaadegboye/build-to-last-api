package com.btl.transport.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@ConditionalOnMissingBean(name = "s3StorageService")
public class LocalStorageService implements StorageService {

    @Value("${btl.uploads.dir:./uploads}")
    private String uploadsDir;

    @Value("${btl.uploads.base-url:http://localhost:8080/api/v1/uploads}")
    private String baseUrl;

    @Override
    public String store(MultipartFile file) {
        try {
            Path dir = Paths.get(uploadsDir);
            Files.createDirectories(dir);
            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + ext;
            Files.copy(file.getInputStream(), dir.resolve(filename));
            return baseUrl + "/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
