package com.spring.JavaT.file;

import com.spring.JavaT.file.dto.FileUploadResponse;

public final class FileMapper {

    private FileMapper() {}

    public static FileUploadResponse toResponse(UploadedFile file, String baseUrl) {
        return FileUploadResponse.builder()
                .id(file.getId())
                .entityType(file.getEntityType())
                .entityId(file.getEntityId())
                .originalName(file.getOriginalName())
                .storedName(file.getStoredName())
                .contentType(file.getContentType())
                .fileSize(file.getFileSize())
                .fileUrl(baseUrl + "/uploads/" + file.getStoredName())
                .uploadedBy(file.getUploadedBy())
                .createdAt(file.getCreatedAt())
                .build();
    }
}
