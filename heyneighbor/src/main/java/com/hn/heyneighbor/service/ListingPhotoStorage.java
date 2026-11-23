package com.hn.heyneighbor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Saves listing photos to the uploads folder and gives back the web paths (/uploads/listings/...)
 * that go in listing.listing_images. Files get random names, and only real JPEG, PNG or WebP
 * images are accepted (checked from the file's first bytes, not its name).
 */
@Component
public class ListingPhotoStorage {

    public static final int MAX_PHOTOS = 4;
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final String WEB_PREFIX = "/uploads/listings/";

    private final Path folder;

    public ListingPhotoStorage(@Value("${heyneighbor.upload-dir:uploads}") String uploadDir) {
        this.folder = Path.of(uploadDir, "listings").toAbsolutePath().normalize();
    }

    /** The real files sent with the form; an empty file box still sends one empty part, which is skipped. */
    public static List<MultipartFile> chosen(List<MultipartFile> files) {
        List<MultipartFile> chosen = new ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    chosen.add(f);
                }
            }
        }
        return chosen;
    }

    /** Problems with the chosen photos, in plain words. Empty when they can all be saved. */
    public List<String> check(List<MultipartFile> photos) {
        List<String> errors = new ArrayList<>();
        if (photos.size() > MAX_PHOTOS) {
            errors.add("You can add up to " + MAX_PHOTOS + " photos.");
            return errors;
        }
        for (MultipartFile photo : photos) {
            String name = photo.getOriginalFilename() == null ? "A photo" : "\"" + photo.getOriginalFilename() + "\"";
            if (photo.getSize() > MAX_BYTES) {
                errors.add(name + " is larger than 5 MB.");
            } else if (extensionFor(photo) == null) {
                errors.add(name + " is not a JPG, PNG or WebP image.");
            }
        }
        return errors;
    }

    /** Saves photos that already passed check() and returns their web paths, in the order chosen. */
    public List<String> save(List<MultipartFile> photos) throws IOException {
        Files.createDirectories(folder);
        List<String> paths = new ArrayList<>();
        for (MultipartFile photo : photos) {
            String fileName = UUID.randomUUID() + extensionFor(photo);
            Path target = folder.resolve(fileName).normalize();
            if (!target.startsWith(folder)) {
                throw new IOException("Bad photo path");
            }
            try (InputStream in = photo.getInputStream()) {
                Files.copy(in, target);
            }
            paths.add(WEB_PREFIX + fileName);
        }
        return paths;
    }

    /** Removes saved photos again, used when the listing itself could not be saved. */
    public void deleteQuietly(List<String> webPaths) {
        for (String web : webPaths) {
            try {
                Files.deleteIfExists(folder.resolve(web.substring(WEB_PREFIX.length())).normalize());
            } catch (IOException | RuntimeException ignored) {
                // nothing else to do; a stray file in uploads does no harm
            }
        }
    }

    /** The folder photos are saved in, for serving them at /uploads/listings/. */
    public Path folder() {
        return folder;
    }

    /** ".jpg", ".png" or ".webp" judged from the file's first bytes, or null when it is not one of those images. */
    private static String extensionFor(MultipartFile photo) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = photo.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return null;
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return ".png";
        }
        if (read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return ".webp";
        }
        return null;
    }
}
