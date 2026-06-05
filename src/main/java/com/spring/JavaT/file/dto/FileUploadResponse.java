package com.spring.JavaT.file.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class FileUploadResponse {
    Long id;
    String entityType;
    Long entityId;
    String originalName;
    String storedName;
    String contentType;
    Long fileSize;
    String fileUrl;
    String uploadedBy;
    Instant createdAt;
}
