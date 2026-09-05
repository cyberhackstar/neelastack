package com.neelastack.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.neelastack.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final Cloudinary cloudinary;
    private final Tika tika = new Tika();

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/zip",
            "text/plain");

    public record UploadResult(
            String url,
            String publicId,
            String resourceType) {
    }

    public UploadResult upload(MultipartFile file, String folder) {

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File exceeds the 10MB size limit");
        }

        byte[] bytes;

        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException(
                    "Could not read the uploaded file — please try again");
        }

        /*
         * Detect MIME type from the actual file bytes rather than trusting the
         * client-provided Content-Type header.
         */
        String detectedType = tika.detect(bytes);

        if (!ALLOWED_TYPES.contains(detectedType)) {
            throw new BadRequestException(
                    "File type not allowed: " + detectedType);
        }

        try {

            /*
             * authenticated delivery prevents direct public access to the
             * underlying Cloudinary asset without a valid signed URL.
             */
            Map<?, ?> result = cloudinary.uploader().upload(
                    bytes,
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "auto",
                            "type", "authenticated",
                            "use_filename", true,
                            "unique_filename", true));

            String resourceType = (String) result.get("resource_type");
            String publicId = (String) result.get("public_id");

            return new UploadResult(
                    generateSignedUrl(publicId, resourceType),
                    publicId,
                    resourceType);

        } catch (IOException e) {
            log.error(
                    "Cloudinary upload failed: {}",
                    e.getMessage(),
                    e);

            throw new BadRequestException(
                    "File upload failed — please try again");
        }
    }

    /**
     * Generates a Cloudinary signed URL for an authenticated asset.
     *
     * This uses Cloudinary's API credentials to generate a signed delivery URL.
     * The URL does not use Cloudinary token-based authentication and therefore
     * does not have the additional 15-minute token expiration layer.
     *
     * The application should regenerate this URL when returning file metadata
     * rather than permanently persisting the URL.
     */
    public String generateSignedUrl(
            String publicId,
            String resourceType) {

        return cloudinary.url()
                .resourceType(resourceType)
                .type("authenticated")
                .signed(true)
                .secure(true)
                .generate(publicId);
    }

    /**
     * Deletes an authenticated Cloudinary asset.
     */
    public void delete(
            String publicId,
            String resourceType) {

        try {

            Map<?, ?> result = cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap(
                            "resource_type", resourceType,
                            "type", "authenticated"));

            String outcome = String.valueOf(result.get("result"));

            if (!"ok".equals(outcome)) {
                log.warn(
                        "Cloudinary reported '{}' deleting asset {} (resource_type={})",
                        outcome,
                        publicId,
                        resourceType);
            }

        } catch (IOException e) {
            log.warn(
                    "Failed to delete Cloudinary asset {}: {}",
                    publicId,
                    e.getMessage());
        }
    }
}