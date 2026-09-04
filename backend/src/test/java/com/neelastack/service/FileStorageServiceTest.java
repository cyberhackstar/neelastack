package com.neelastack.service;

import com.cloudinary.Cloudinary;
import com.neelastack.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class FileStorageServiceTest {

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        // Cloudinary itself is never reached in these tests — every case here is rejected
        // by validation before the upload call, which is exactly what we're checking.
        fileStorageService = new FileStorageService(mock(Cloudinary.class));
    }

    @Test
    void upload_emptyFile_rejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.upload(empty, "folder"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void upload_oversizedFile_rejected() {
        byte[] tooBig = new byte[11 * 1024 * 1024]; // 11MB, over the 10MB limit
        MockMultipartFile huge = new MockMultipartFile("file", "big.pdf", "application/pdf", tooBig);

        assertThatThrownBy(() -> fileStorageService.upload(huge, "folder"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    void upload_contentTypeSpoofedAsPdf_rejectedByMagicByteDetection() {
        // Claims to be a PDF via its declared Content-Type and filename, but the actual
        // bytes are an executable (MZ header). Tika detects the real type from the bytes
        // themselves, so the spoofed header doesn't get it past validation.
        byte[] exeBytes = {0x4D, 0x5A, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00}; // "MZ..." DOS/PE header
        MockMultipartFile spoofed = new MockMultipartFile("file", "report.pdf", "application/pdf", exeBytes);

        assertThatThrownBy(() -> fileStorageService.upload(spoofed, "folder"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void upload_disallowedButHonestType_rejected() {
        // Gzip magic bytes (0x1F 0x8B) — a real, honestly-labeled type that's simply not
        // on the allow-list. Confirms the allow-list is actually enforced, not just the
        // spoofing check above.
        byte[] gzipBytes = {0x1F, (byte) 0x8B, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile archive = new MockMultipartFile("file", "data.gz", "application/gzip", gzipBytes);

        assertThatThrownBy(() -> fileStorageService.upload(archive, "folder"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not allowed");
    }
}
