package com.yqz.openblog.media.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.media.dto.MediaUploadResponse;
import com.yqz.openblog.media.dto.ThumbInfoResponse;
import com.yqz.openblog.media.entity.Media;
import com.yqz.openblog.media.service.MediaService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/media")
@CrossOrigin(origins = "*")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(value = "/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MediaUploadResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(mediaService.upload(file));
    }

    @GetMapping(value = "/{key}/thumb", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ThumbInfoResponse> thumbInfo(@PathVariable("key") String key) {
        return ApiResponse.ok(mediaService.thumbInfo(key));
    }

    @GetMapping(value = "/files/{key}", produces = MediaType.ALL_VALUE)
    public void serveOriginal(@PathVariable("key") String key, HttpServletResponse response) throws IOException {
        serveFile(key, false, response);
    }

    @GetMapping(value = "/files/{key}/thumb", produces = MediaType.ALL_VALUE)
    public void serveThumb(@PathVariable("key") String key, HttpServletResponse response) throws IOException {
        serveFile(key, true, response);
    }

    private void serveFile(String key, boolean thumb, HttpServletResponse response) throws IOException {
        Media media = mediaService.getByKey(key);
        byte[] bytes = mediaService.readFileBytes(key, thumb);
        response.setContentType(media.getContentType());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=86400");
        response.getOutputStream().write(bytes);
    }
}
