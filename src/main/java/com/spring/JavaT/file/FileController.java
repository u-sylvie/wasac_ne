package com.spring.JavaT.file;

import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.common.ResponseBuilder;
import com.spring.JavaT.file.dto.FileUploadResponse;
import com.spring.JavaT.notification.MailProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final MailProperties mailProperties;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String entityType,
            @RequestParam Long entityId,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        FileUploadResponse response = fileStorageService.store(
                file, entityType, entityId, principal.getUsername(), mailProperties.getBaseUrl());
        return ResponseBuilder.created(response, "File uploaded successfully", request);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<FileUploadResponse>>> list(
            @RequestParam String entityType,
            @RequestParam Long entityId,
            HttpServletRequest request) {
        List<FileUploadResponse> files = fileStorageService.listByEntity(
                entityType, entityId, mailProperties.getBaseUrl());
        return ResponseBuilder.ok(files, "Files retrieved successfully", request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        fileStorageService.delete(id, principal.getUsername());
        return ResponseBuilder.ok("File deleted successfully", request);
    }
}
