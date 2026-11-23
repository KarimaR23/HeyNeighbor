
package com.hn.heyneighbor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class ListingImageService {

    private final Path uploadDirectory;

    public ListingImageService(
            @Value("${app.upload.dir}") String uploadDir) {
        this.uploadDirectory = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();
    }

    public String saveImage(MultipartFile image) throws IOException {

        if (image == null || image.isEmpty()) {
            return null;
        }

        if (image.getSize() > 5 * 1024 * 1024) {
            throw new IOException("Image must be 5 MB or smaller.");
        }

        Set<String> allowedTypes = Set.of(
                "image/jpeg",
                "image/png",
                "image/webp"
        );

        String contentType = image.getContentType();

        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new IOException("Only JPG, PNG, and WebP images are allowed.");
        }

        // Verify the actual file signature, not just the browser's file type.
        byte[] header = new byte[12];
        try (InputStream input = image.getInputStream()) {
            if (input.readNBytes(header, 0, header.length) < 12) {
                throw new IOException("The selected image is invalid.");
            }
        }

        boolean valid = switch (contentType) {
            case "image/jpeg" ->
                    (header[0] & 0xFF) == 0xFF
                            && (header[1] & 0xFF) == 0xD8
                            && (header[2] & 0xFF) == 0xFF;

            case "image/png" ->
                    (header[0] & 0xFF) == 0x89
                            && header[1] == 0x50
                            && header[2] == 0x4E
                            && header[3] == 0x47
                            && header[4] == 0x0D
                            && header[5] == 0x0A
                            && header[6] == 0x1A
                            && header[7] == 0x0A;

            case "image/webp" ->
                    header[0] == 'R'
                            && header[1] == 'I'
                            && header[2] == 'F'
                            && header[3] == 'F'
                            && header[8] == 'W'
                            && header[9] == 'E'
                            && header[10] == 'B'
                            && header[11] == 'P';

            default -> false;
        };

        if (!valid) {
            throw new IOException("The selected file is not a valid image.");
        }

        // Confirm the image can be decoded when supported by Java.
        if (!"image/webp".equals(contentType)) {
            try (InputStream input = image.getInputStream()) {
                BufferedImage decoded = ImageIO.read(input);

                if (decoded == null) {
                    throw new IOException("The selected image is damaged or invalid.");
                }
            }
        }

        Files.createDirectories(uploadDirectory);

        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IOException("Unsupported image type.");
        };

        String filename = UUID.randomUUID() + extension;
        Path destination = uploadDirectory.resolve(filename);

        try (InputStream input = image.getInputStream()) {
            Files.copy(input, destination);
        }

        return filename;
    }
}

