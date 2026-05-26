package com.yqz.openblog.media.storage;

import java.io.IOException;
import java.nio.file.Path;

public interface MediaStorage {

    String storageType();

    void saveOriginal(String key, Path file) throws IOException;

    void saveThumb(String key, Path file) throws IOException;

    byte[] readOriginal(String key) throws IOException;

    byte[] readThumb(String key) throws IOException;

    void delete(String key) throws IOException;
}
