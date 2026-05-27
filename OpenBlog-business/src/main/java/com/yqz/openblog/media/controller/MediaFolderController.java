package com.yqz.openblog.media.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.media.dto.MediaFolderFlatItemResponse;
import com.yqz.openblog.media.dto.MediaFolderTreeNodeResponse;
import com.yqz.openblog.media.dto.MediaFolderUpsertRequest;
import com.yqz.openblog.media.service.MediaFolderService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/media-folders")
@CrossOrigin(origins = "*")
public class MediaFolderController {

    private final MediaFolderService folderService;

    public MediaFolderController(MediaFolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping("/tree")
    public ApiResponse<List<MediaFolderTreeNodeResponse>> tree() {
        return ApiResponse.ok(folderService.listTree());
    }

    @GetMapping
    public ApiResponse<List<MediaFolderFlatItemResponse>> listFlat() {
        return ApiResponse.ok(folderService.listFlat());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MediaFolderFlatItemResponse> create(@RequestBody @Valid MediaFolderUpsertRequest req) {
        return ApiResponse.ok(folderService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MediaFolderFlatItemResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid MediaFolderUpsertRequest req) {
        return ApiResponse.ok(folderService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        folderService.delete(id);
        return ApiResponse.ok();
    }
}
