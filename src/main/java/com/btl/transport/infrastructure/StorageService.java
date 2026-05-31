package com.btl.transport.infrastructure;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String store(MultipartFile file);

    default String presign(String url) { return url; }
}
