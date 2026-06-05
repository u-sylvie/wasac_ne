package com.spring.JavaT.file;

import com.spring.JavaT.common.ApiResponse;
import com.spring.JavaT.common.ResponseBuilder;
import com.spring.JavaT.file.dto.FileUploadResponse;
import com.spring.JavaT.notification.MailProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * File upload endpoints — attach supporting documents to any business entity.
 *
 * <p><b>Why files exist in this project:</b> utility billing needs proof and reference documents
 * stored against customers, meters, bills, or payments. Examples:
 * <ul>
 *   <li>{@code Customer} + customer id — National ID scan, signed application form</li>
 *   <li>{@code Meter} + meter id — installation photo, inspection certificate</li>
 *   <li>{@code Bill} + bill id — printed bill PDF, dispute evidence</li>
 *   <li>{@code Payment} + payment id — MoMo/bank transfer receipt screenshot</li>
 *   <li>{@code MeterReading} + reading id — meter photo taken by operator in the field</li>
 * </ul>
 *
 * <p>Files are stored on disk under {@code uploads/} and metadata is saved in {@code uploaded_files}.
 * Access uploaded files via the {@code downloadUrl} returned in the response.
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "Attach documents to customers, meters, bills, and payments")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final FileStorageService fileStorageService;
    private final MailProperties mailProperties;

    /**
     * Upload a document and link it to a business record using entityType + entityId.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload a file linked to an entity — any authenticated user")
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @Parameter(description = "The file to upload (max 5 MB)")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Entity type: Customer, Meter, Bill, Payment, MeterReading", example = "Customer")
            @RequestParam String entityType,
            @Parameter(description = "ID of the entity to attach the file to", example = "1")
            @RequestParam Long entityId,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        FileUploadResponse response = fileStorageService.store(
                file, entityType, entityId, principal.getUsername(), mailProperties.getBaseUrl());
        return ResponseBuilder.created(response, "File uploaded successfully", request);
    }

    /**
     * List all files previously uploaded for a given entity.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List files for an entity — any authenticated user")
    public ResponseEntity<ApiResponse<List<FileUploadResponse>>> list(
            @Parameter(description = "Entity type: Customer, Meter, Bill, Payment, MeterReading", example = "Customer")
            @RequestParam String entityType,
            @Parameter(description = "Entity ID", example = "1")
            @RequestParam Long entityId,
            HttpServletRequest request) {
        List<FileUploadResponse> files = fileStorageService.listByEntity(
                entityType, entityId, mailProperties.getBaseUrl());
        return ResponseBuilder.ok(files, "Files retrieved successfully", request);
    }

    /**
     * Permanently delete a file record and its file on disk.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "Delete an uploaded file — ADMIN or FINANCE only")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest request) {
        fileStorageService.delete(id, principal.getUsername());
        return ResponseBuilder.ok("File deleted successfully", request);
    }
}
