package com.yqz.openblog.media.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.media.dto.MediaCategoryResponse;
import com.yqz.openblog.media.dto.MediaListItemResponse;
import com.yqz.openblog.media.dto.MediaUploadResponse;
import com.yqz.openblog.media.dto.ThumbInfoResponse;
import com.yqz.openblog.media.entity.Media;
import com.yqz.openblog.media.service.MediaService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/media")
@CrossOrigin(origins = "*")
public class MediaController {

    private static final String MEDIA_PATH_PREFIX = "/api/v1/media/";
    private static final String FILES_PATH_MARKER = MEDIA_PATH_PREFIX + "files/";

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(value = "/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MediaUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId) throws IOException {
        return ApiResponse.ok(mediaService.upload(file, folderId));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<IPage<MediaListItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long folderId) {
        return ApiResponse.ok(mediaService.listMyMedia(page, size, folderId));
    }

    @GetMapping(value = "/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<MediaCategoryResponse>> categories() {
        return ApiResponse.ok(mediaService.listCategories());
    }

    @DeleteMapping(value = "/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> delete(HttpServletRequest request) {
        String key = extractMediaKey(request, null);
        mediaService.deleteMyMedia(key);
        return ApiResponse.ok();
    }

    @GetMapping(value = "/thumb-info/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ThumbInfoResponse> thumbInfo(HttpServletRequest request) {
        String key = extractMediaKeyFromPrefix(request, "/thumb-info/");
        return ApiResponse.ok(mediaService.thumbInfo(key));
    }

  /**
   * 媒体文件访问（原图 / 缩略图）。storageKey 可能含 {@code /}（如 {@code general/uuid.jpg}），
   * 须用 {@code /**} 解析路径；Spring Boot 3 默认 PathPattern 下 {@code {key:.+}} 无法跨段匹配。
   */
    @GetMapping(value = "/files/**", produces = MediaType.ALL_VALUE)
    public void serveFile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = extractMediaKeyFromFiles(request);
        boolean thumb = path.endsWith("/thumb");
        String key = thumb ? path.substring(0, path.length() - "/thumb".length()) : path;
        if (!StringUtils.hasText(key)) {
            throw new BizException(4002, "媒体 key 为空");
        }
        serveFile(key, thumb, response);
    }

    private String extractMediaKey(HttpServletRequest request, String suffix) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf(MEDIA_PATH_PREFIX);
        if (idx < 0) {
            throw new BizException(4002, "无效媒体路径");
        }
        String key = uri.substring(idx + MEDIA_PATH_PREFIX.length());
        int q = key.indexOf('?');
        if (q >= 0) {
            key = key.substring(0, q);
        }
        if (suffix != null && key.endsWith(suffix)) {
            key = key.substring(0, key.length() - suffix.length());
        }
        key = URLDecoder.decode(key, StandardCharsets.UTF_8);
        if (!StringUtils.hasText(key)) {
            throw new BizException(4002, "媒体 key 为空");
        }
        return key;
    }

    private String extractMediaKeyFromPrefix(HttpServletRequest request, String prefix) {
        String uri = request.getRequestURI();
        String fullPrefix = MEDIA_PATH_PREFIX + prefix;
        int idx = uri.indexOf(fullPrefix);
        if (idx < 0) {
            throw new BizException(4002, "无效媒体路径");
        }
        String key = uri.substring(idx + fullPrefix.length());
        int q = key.indexOf('?');
        if (q >= 0) {
            key = key.substring(0, q);
        }
        key = URLDecoder.decode(key, StandardCharsets.UTF_8);
        if (!StringUtils.hasText(key)) {
            throw new BizException(4002, "媒体 key 为空");
        }
        return key;
    }

    private String extractMediaKeyFromFiles(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf(FILES_PATH_MARKER);
        if (idx < 0) {
            throw new BizException(4042, "无效媒体路径");
        }
        String path = uri.substring(idx + FILES_PATH_MARKER.length());
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }

    private void serveFile(String key, boolean thumb, HttpServletResponse response) throws IOException {
        Media media = mediaService.getByKey(key);
        byte[] bytes = mediaService.readFileBytes(key, thumb);
        response.setContentType(media.getContentType());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=86400");
        response.getOutputStream().write(bytes);
    }
}
