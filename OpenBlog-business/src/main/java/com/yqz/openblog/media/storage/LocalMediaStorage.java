package com.yqz.openblog.media.storage;

import com.yqz.openblog.media.MediaProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class LocalMediaStorage implements MediaStorage {

    private final MediaProperties properties;

    public LocalMediaStorage(MediaProperties properties) {
        this.properties = properties;
    }

    @Override
    public String storageType() {
        return "LOCAL";
    }

    @Override
    public void saveOriginal(String key, Path file) throws IOException {
        Path target = resolveOriginal(key);
        Files.createDirectories(target.getParent());
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void saveThumb(String key, Path file) throws IOException {
        Path target = resolveThumb(key);
        Files.createDirectories(target.getParent());
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public byte[] readOriginal(String key) throws IOException {
        return Files.readAllBytes(resolveOriginal(key));
    }

    @Override
    public byte[] readThumb(String key) throws IOException {
        return Files.readAllBytes(resolveThumb(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolveOriginal(key));
        Files.deleteIfExists(resolveThumb(key));
    }

    private Path root() {
        return Path.of(properties.getRootPath()).toAbsolutePath().normalize();
    }

    private Path resolveOriginal(String key) {
        return root().resolve("original").resolve(key);
    }

    private Path resolveThumb(String key) {
        return root().resolve("thumb").resolve(key);
    }
}
