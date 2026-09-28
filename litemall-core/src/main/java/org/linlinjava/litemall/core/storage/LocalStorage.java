package org.linlinjava.litemall.core.storage;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * 服务器本地对象存储服务
 */
public class LocalStorage implements Storage {


    private final Log logger = LogFactory.getLog(LocalStorage.class);

    private String storagePath;
    private String address;

    private Path rootLocation;

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;

        this.rootLocation = storagePath != null ? Paths.get(storagePath).toAbsolutePath().normalize() : null;
        if (rootLocation != null) {
            try {
                Files.createDirectories(rootLocation);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public void store(InputStream inputStream, long contentLength, String contentType, String keyName) {
        try {
            Path file = load(keyName);
            if (file == null) {
                throw new RuntimeException("Failed to store file " + keyName);
            }
            Files.copy(inputStream, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file " + keyName, e);
        }
    }

    @Override
    public Stream<Path> loadAll() {
        try {
            return Files.walk(rootLocation, 1)
                    .filter(path -> !path.equals(rootLocation))
                    .map(path -> rootLocation.relativize(path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read stored files", e);
        }

    }

    @Override
    public Path load(String filename) {
        if (rootLocation == null || filename == null || filename.isEmpty()) {
            return null;
        }
        try {
            Path file = Paths.get(filename);
            if (file.isAbsolute()) {
                return null;
            }
            Path path = rootLocation.resolve(filename).normalize().toAbsolutePath();
            if (!path.startsWith(rootLocation) || path.equals(rootLocation)) {
                return null;
            }
            return path;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Resource loadAsResource(String filename) {
        try {
            Path file = load(filename);
            if (file == null) {
                return null;
            }
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                return null;
            }
        } catch (MalformedURLException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void delete(String filename) {
        Path file = load(filename);
        if (file == null) {
            return;
        }
        try {
            Files.delete(file);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public String generateUrl(String keyName) {

        return address + keyName;
    }
}
