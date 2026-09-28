package org.linlinjava.litemall.wx.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.storage.StorageService;
import org.linlinjava.litemall.core.util.CharUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallStorage;
import org.linlinjava.litemall.db.service.LitemallStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 对象存储服务
 */
@RestController
@RequestMapping("/wx/storage")
@Validated
public class WxStorageController {
    private final Log logger = LogFactory.getLog(WxStorageController.class);

    private static final Map<String, String> ALLOWED_EXTENSIONS = new HashMap<>();
    private static final Map<String, List<String>> EXTENSION_MIME_MAP = new HashMap<>();
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>();

    static {
        ALLOWED_EXTENSIONS.put(".jpg", "image/jpeg");
        ALLOWED_EXTENSIONS.put(".jpeg", "image/jpeg");
        ALLOWED_EXTENSIONS.put(".png", "image/png");
        ALLOWED_EXTENSIONS.put(".gif", "image/gif");
        ALLOWED_EXTENSIONS.put(".webp", "image/webp");
        ALLOWED_EXTENSIONS.put(".bmp", "image/bmp");

        EXTENSION_MIME_MAP.put(".jpg", Arrays.asList("image/jpeg", "image/pjpeg", "image/jpg"));
        EXTENSION_MIME_MAP.put(".jpeg", Arrays.asList("image/jpeg", "image/pjpeg", "image/jpg"));
        EXTENSION_MIME_MAP.put(".png", Arrays.asList("image/png", "image/x-png"));
        EXTENSION_MIME_MAP.put(".gif", Collections.singletonList("image/gif"));
        EXTENSION_MIME_MAP.put(".webp", Collections.singletonList("image/webp"));
        EXTENSION_MIME_MAP.put(".bmp", Arrays.asList("image/bmp", "image/x-ms-bmp"));

        for (List<String> mimes : EXTENSION_MIME_MAP.values()) {
            ALLOWED_MIME_TYPES.addAll(mimes);
        }
    }

    @Autowired
    private StorageService storageService;
    @Autowired
    private LitemallStorageService litemallStorageService;

    private String generateKey(String originalFilename) {
        int index = originalFilename.lastIndexOf('.');
        String suffix = originalFilename.substring(index);

        String key = null;
        LitemallStorage storageInfo = null;

        do {
            key = CharUtil.getRandomString(20) + suffix;
            storageInfo = litemallStorageService.findByKey(key);
        }
        while (storageInfo != null);

        return key;
    }

    private boolean isSafeImage(String key, MediaType mediaType) {
        if (key == null || mediaType == null) {
            return false;
        }
        int index = key.lastIndexOf('.');
        if (index == -1) {
            return false;
        }
        String suffix = key.substring(index).toLowerCase();
        if (!ALLOWED_EXTENSIONS.containsKey(suffix)) {
            return false;
        }
        String mime = (mediaType.getType() + "/" + mediaType.getSubtype()).toLowerCase();
        List<String> validMimes = EXTENSION_MIME_MAP.get(suffix);
        return validMimes != null && validMimes.contains(mime);
    }

    @PostMapping("/upload")
    public Object upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseUtil.badArgument();
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.contains("\0")) {
            return ResponseUtil.badArgument();
        }

        int index = originalFilename.lastIndexOf('.');
        if (index <= 0) {
            return ResponseUtil.badArgument();
        }

        String suffix = originalFilename.substring(index).toLowerCase();
        if (!ALLOWED_EXTENSIONS.containsKey(suffix)) {
            return ResponseUtil.badArgument();
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            return ResponseUtil.badArgument();
        }

        String mimeType = contentType.split(";")[0].trim().toLowerCase();
        List<String> validMimes = EXTENSION_MIME_MAP.get(suffix);
        if (validMimes == null || !validMimes.contains(mimeType)) {
            return ResponseUtil.badArgument();
        }

        String safeMimeType = ALLOWED_EXTENSIONS.get(suffix);
        LitemallStorage litemallStorage = storageService.store(file.getInputStream(), file.getSize(), safeMimeType, originalFilename);
        return ResponseUtil.ok(litemallStorage);
    }

    /**
     * 访问存储对象
     *
     * @param key 存储对象key
     * @return
     */
    @GetMapping("/fetch/{key:.+}")
    public ResponseEntity<Resource> fetch(@PathVariable String key) {
        if (key == null || key.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        LitemallStorage litemallStorage = litemallStorageService.findByKey(key);
        if (litemallStorage == null) {
            return ResponseEntity.notFound().build();
        }

        Resource file = storageService.loadAsResource(key);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        String type = litemallStorage.getType();
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(type);
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        boolean safe = isSafeImage(key, mediaType);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType)
                .header("X-Content-Type-Options", "nosniff");

        if (!safe) {
            String filename = file.getFilename() != null ? file.getFilename().replaceAll("[\\r\\n\"]", "") : key;
            builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            builder.header("Content-Security-Policy", "default-src 'none'");
        }

        return builder.body(file);
    }

    /**
     * 访问存储对象
     *
     * @param key 存储对象key
     * @return
     */
    @GetMapping("/download/{key:.+}")
    public ResponseEntity<Resource> download(@PathVariable String key) {
        if (key == null || key.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        LitemallStorage litemallStorage = litemallStorageService.findByKey(key);
        if (litemallStorage == null) {
            return ResponseEntity.notFound().build();
        }

        Resource file = storageService.loadAsResource(key);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        String type = litemallStorage.getType();
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(type);
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String filename = file.getFilename() != null ? file.getFilename().replaceAll("[\\r\\n\"]", "") : key;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .body(file);
    }

}
