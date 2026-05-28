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

    private static final String FILES_PATH_MARKER = "/api/v1/media/files/";

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

    @DeleteMapping(value = "/{key:.+}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> delete(@PathVariable("key") String key) {
        mediaService.deleteMyMedia(key);
        return ApiResponse.ok();
    }

    @GetMapping(value = "/{key:.+}/thumb", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ThumbInfoResponse> thumbInfo(@PathVariable("key") String key) {
        return ApiResponse.ok(mediaService.thumbInfo(key));
    }

  /**
   * 媒体文件访问（原图 / 缩略图）。storageKey 可能含 {@code /}（如 {@code general/uuid.jpg}），
   * 须用 {@code /**} 解析路径；Spring Boot 3 默认 PathPattern 下 {@code {key:.+}} 无法跨段匹配。
   */
    @GetMapping(value = "/files/**", produces = MediaType.ALL_VALUE)
    public void serveFile(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
        path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        boolean thumb = path.endsWith("/thumb");
        String key = thumb ? path.substring(0, path.length() - "/thumb".length()) : path;
        if (!StringUtils.hasText(key)) {
            throw new BizException(4002, "媒体 key 为空");
        }
        serveFile(key, thumb, response);
    }

    private void serveFile(String key, boolean thumb, HttpServletResponse response) throws IOException {
        Media media = mediaService.getByKey(key);
        byte[] bytes = mediaService.readFileBytes(key, thumb);
        response.setContentType(media.getContentType());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=86400");
        response.getOutputStream().write(bytes);
    }
}
