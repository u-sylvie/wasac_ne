package com.spring.JavaT.file;

import com.spring.JavaT.audit.AuditService;
import com.spring.JavaT.config.FileStorageProperties;
import com.spring.JavaT.exception.BusinessException;
import com.spring.JavaT.file.dto.FileUploadResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stores uploaded files on disk and records metadata in {@code uploaded_files}.
 *
 * <p>Files are linked to any business entity via {@code entityType} + {@code entityId}
 * (e.g. attach a National ID scan to a Customer, or a MoMo receipt to a Payment).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorageProperties fileStorageProperties;
    private final AuditService auditService;

    @Transactional
    public FileUploadResponse store(MultipartFile file, String entityType, Long entityId, String actor, String baseUrl) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File must not be empty", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > fileStorageProperties.getMaxFileSizeBytes()) {
            throw new BusinessException("File exceeds maximum allowed size", HttpStatus.BAD_REQUEST);
        }

        try {
            Path uploadDir = Paths.get(fileStorageProperties.getUploadDir()).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            String extension = extractExtension(file.getOriginalFilename());
            String storedName = UUID.randomUUID() + extension;
            Path target = uploadDir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            UploadedFile uploadedFile = UploadedFile.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .originalName(file.getOriginalFilename())
                    .storedName(storedName)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .filePath(target.toString())
                    .uploadedBy(actor)
                    .createdAt(Instant.now())
                    .build();

            UploadedFile saved = uploadedFileRepository.save(uploadedFile);
            auditService.log("UploadedFile", saved.getId(), "CREATE", actor,
                    "Uploaded file " + saved.getOriginalName() + " for " + entityType + ":" + entityId);
            log.info("File {} stored for {}:{}", saved.getOriginalName(), entityType, entityId);
            return FileMapper.toResponse(saved, baseUrl);
        } catch (IOException e) {
            log.error("Failed to store file: {}", e.getMessage(), e);
            throw new BusinessException("Failed to store file", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    public List<FileUploadResponse> listByEntity(String entityType, Long entityId, String baseUrl) {
        return uploadedFileRepository.findByEntityTypeAndEntityId(entityType, entityId).stream()
                .map(file -> FileMapper.toResponse(file, baseUrl))
                .toList();
    }

    @Transactional
    public void delete(Long id, String actor) {
        UploadedFile file = uploadedFileRepository.findById(id)
                .orElseThrow(() -> new com.spring.JavaT.exception.ResourceNotFoundException("UploadedFile", "id", id));
        try {
            Files.deleteIfExists(Paths.get(file.getFilePath()));
        } catch (IOException e) {
            log.warn("Could not delete physical file {}: {}", file.getFilePath(), e.getMessage());
        }
        uploadedFileRepository.delete(file);
        auditService.log("UploadedFile", id, "DELETE", actor, "Deleted file " + file.getOriginalName());
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}
