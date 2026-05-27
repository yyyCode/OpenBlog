package com.yqz.openblog.media.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.media.dto.MediaCategoryResponse;
import com.yqz.openblog.media.dto.MediaListItemResponse;
import com.yqz.openblog.media.dto.MediaUploadResponse;
import com.yqz.openblog.media.dto.ThumbInfoResponse;
import com.yqz.openblog.media.entity.Media;
import com.yqz.openblog.media.service.MediaService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/media")
@CrossOrigin(origins = "*")
public class MediaController {

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

    @GetMapping(value = "/files/{key:.+}/thumb", produces = MediaType.ALL_VALUE)
    public void serveThumb(@PathVariable("key") String key, HttpServletResponse response) throws IOException {
        serveFile(key, true, response);
    }

    @GetMapping(value = "/files/{key:.+}", produces = MediaType.ALL_VALUE)
    public void serveOriginal(@PathVariable("key") String key, HttpServletResponse response) throws IOException {
        serveFile(key, false, response);
    }

    private void serveFile(String key, boolean thumb, HttpServletResponse response) throws IOException {
        Media media = mediaService.getByKey(key);
        byte[] bytes = mediaService.readFileBytes(key, thumb);
        response.setContentType(media.getContentType());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=86400");
        response.getOutputStream().write(bytes);
    }
}
