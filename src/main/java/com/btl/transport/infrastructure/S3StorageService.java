package com.btl.transport.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service("s3StorageService")
@Primary
@ConditionalOnExpression("!'${btl.uploads.s3.bucket:}'.isEmpty()")
public class S3StorageService implements StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;

    @Value("${btl.uploads.s3.bucket}")
    private String bucket;

    @Value("${btl.uploads.s3.cdn-base-url:}")
    private String cdnBaseUrl;

    public S3StorageService() {
        this.s3 = S3Client.create();
        this.presigner = S3Presigner.create();
    }

    @Override
    public String store(MultipartFile file) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String key = "uploads/" + UUID.randomUUID() + ext;
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
            String base = cdnBaseUrl.isBlank()
                ? "https://" + bucket + ".s3.amazonaws.com"
                : cdnBaseUrl;
            return base + "/" + key;
        } catch (IOException e) {
            throw new RuntimeException("S3 upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String presign(String url) {
        if (url == null || url.isBlank()) return url;
        int i = url.indexOf("/uploads/");
        if (i < 0) return url;
        String key = url.substring(i + 1);
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofDays(7))
            .getObjectRequest(r -> r.bucket(bucket).key(key))
            .build();
        return presigner.presignGetObject(req).url().toString();
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
