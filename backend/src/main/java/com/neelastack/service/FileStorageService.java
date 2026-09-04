package com.neelastack.service;

import com.cloudinary.AuthToken;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.neelastack.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
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

    // Enables real time-limited access windows on signed URLs via Cloudinary's
    // token-based authentication feature. This is a distinct secret from the API
    // secret and must be turned on under Cloudinary Console -> Settings -> Security
    // -> "Token-based authentication" and set here (CLOUDINARY_AUTH_TOKEN_KEY). Until
    // that's configured, generateSignedUrl() falls back to a plain signed URL that
    // doesn't expire on its own — same behavior as before this change, just explicit
    // about the gap instead of silently assuming it's covered.
    @Value("${app.cloudinary.auth-token-key:}")
    private String authTokenKey;

    private static final long SIGNED_URL_TTL_SECONDS = 15 * 60; // 15 minutes

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/zip", "text/plain"
    );

    public record UploadResult(String url, String publicId, String resourceType) {}

    public UploadResult upload(MultipartFile file, String folder) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File exceeds the 10MB size limit");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file — please try again");
        }

        // Detected from the file's actual bytes (magic numbers / structure), not the
        // Content-Type header the client sent — that header is just a string the client
        // chooses and is trivially spoofed (e.g. renaming a .exe to report.pdf and
        // setting Content-Type: application/pdf would sail straight through a
        // header-only check). This also protects against MIME confusion, where a file
        // matches its claimed extension but is actually something else entirely.
        String detectedType = tika.detect(bytes);
        if (!ALLOWED_TYPES.contains(detectedType)) {
            throw new BadRequestException("File type not allowed: " + detectedType);
        }

        try {
            // type=authenticated (not the default "upload" delivery type) means the file is
            // never publicly fetchable by its raw URL alone — Cloudinary rejects requests
            // that don't carry a valid signature. Without this, project files were served
            // from a fully public CDN URL: anyone who ever saw the link (even someone who
            // never logged into Neelastack at all) could access a client's files, forever,
            // since the URL itself carried no access control whatsoever.
            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto",
                    "type", "authenticated",
                    "use_filename", true,
                    "unique_filename", true
            ));
            String resourceType = (String) result.get("resource_type");
            String publicId = (String) result.get("public_id");
            return new UploadResult(generateSignedUrl(publicId, resourceType), publicId, resourceType);
        } catch (IOException e) {
            log.error("Cloudinary upload failed: {}", e.getMessage());
            throw new BadRequestException("File upload failed — please try again");
        }
    }

    /**
     * Regenerates a fresh signed URL for an already-uploaded authenticated asset. Called
     * every time a file list is served (not stored statically) so the signature is always
     * generated from the backend's own credentials at the moment of use, rather than a URL
     * baked in once at upload time and handed out indefinitely.
     */
    public String generateSignedUrl(String publicId, String resourceType) {
        var url = cloudinary.url()
                .resourceType(resourceType)
                .type("authenticated")
                .signed(true)
                .secure(true);

        if (authTokenKey != null && !authTokenKey.isBlank()) {
            long expiresAt = (System.currentTimeMillis() / 1000L) + SIGNED_URL_TTL_SECONDS;
            AuthToken token = new AuthToken(authTokenKey);
            token.expiration(expiresAt);
            url.authToken(token);
        } else {
            log.warn("app.cloudinary.auth-token-key is not configured — signed URLs are reusable "
                    + "indefinitely instead of expiring after {} seconds. Enable token-based "
                    + "authentication in the Cloudinary console to close this gap.", SIGNED_URL_TTL_SECONDS);
        }

        return url.generate(publicId);
    }

    /**
     * Deletes an uploaded asset. The resource_type and type must match exactly what the
     * asset was uploaded with (resource_type "auto" resolves to "image"/"video"/"raw" at
     * upload time, and type is "authenticated" — not Cloudinary's default "upload"). Calling
     * destroy() without these, as a bare publicId, silently no-ops against an authenticated
     * asset: Cloudinary reports nothing to delete under the default type/resource_type, the
     * call returns without error, and the file is quietly orphaned — still billed, still
     * technically reachable via a correctly-scoped signed URL, forever.
     */
    public void delete(String publicId, String resourceType) {
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", resourceType,
                    "type", "authenticated"
            ));
            String outcome = String.valueOf(result.get("result"));
            if (!"ok".equals(outcome)) {
                log.warn("Cloudinary reported '{}' deleting asset {} (resource_type={})",
                        outcome, publicId, resourceType);
            }
        } catch (IOException e) {
            log.warn("Failed to delete Cloudinary asset {}: {}", publicId, e.getMessage());
        }
    }
}
