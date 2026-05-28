package com.yqz.openblog.media.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.media.MediaProperties;
import com.yqz.openblog.media.dto.MediaCategoryResponse;
import com.yqz.openblog.media.dto.MediaListItemResponse;
import com.yqz.openblog.media.dto.MediaUploadResponse;
import com.yqz.openblog.media.dto.ThumbInfoResponse;
import com.yqz.openblog.media.entity.Media;
import com.yqz.openblog.media.repo.MediaMapper;
import com.yqz.openblog.media.storage.MediaFileAccess;
import com.yqz.openblog.media.storage.MediaStorage;
import com.yqz.openblog.media.storage.MinioMediaStorage;
import com.yqz.openblog.media.storage.LocalMediaStorage;
import com.yqz.openblog.security.CurrentUser;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MediaService {

    private static final Pattern CATEGORY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    private final MediaMapper mediaMapper;
    private final MediaProperties properties;
    private final CurrentUser currentUser;
    private final MediaFileAccess mediaFileAccess;
    private final LocalMediaStorage localMediaStorage;
    private final ObjectProvider<MinioMediaStorage> minioMediaStorage;

    public MediaService(MediaMapper mediaMapper,
                        MediaProperties properties,
                        CurrentUser currentUser,
                        MediaFileAccess mediaFileAccess,
                        LocalMediaStorage localMediaStorage,
                        ObjectProvider<MinioMediaStorage> minioMediaStorage) {
        this.mediaMapper = mediaMapper;
        this.properties = properties;
        this.currentUser = currentUser;
        this.mediaFileAccess = mediaFileAccess;
        this.localMediaStorage = localMediaStorage;
        this.minioMediaStorage = minioMediaStorage;
    }

    public MediaUploadResponse upload(MultipartFile file, Long folderId, String category) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(4002, "文件不能为空");
        }
        if (file.getSize() > 10L * 1024L * 1024L) {
            throw new BizException(4002, "文件过大");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BizException(4002, "仅支持图片上传");
        }

        String originalName = file.getOriginalFilename();
        String ext = guessExt(originalName, contentType);
        if (!StringUtils.hasText(ext)) {
            throw new BizException(4002, "无法识别图片后缀");
        }

        String cat = resolveStorageCategory(folderId, category);
        String key = cat + "/" + UUID.randomUUID().toString() + "." + ext;
        MediaStorage storage = activeStorage();

        Path tempDir = Files.createTempDirectory("openblog-media-");
        Path originalPath = tempDir.resolve("original-" + key.replace('/', '-'));
        Path thumbPath = tempDir.resolve("thumb-" + key.replace('/', '-'));
        try {
            Files.copy(file.getInputStream(), originalPath);

            BufferedImage originalImage = ImageIO.read(originalPath.toFile());
            if (originalImage == null) {
                throw new BizException(4002, "图片解析失败");
            }

            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            int thumbLongEdge = properties.getThumbLongEdge();
            int targetLongEdge = Math.min(thumbLongEdge, Math.max(width, height));
            int tw;
            int th;
            if (width >= height) {
                tw = targetLongEdge;
                th = Math.max(1, (int) Math.round((double) height * targetLongEdge / width));
            } else {
                th = targetLongEdge;
                tw = Math.max(1, (int) Math.round((double) width * targetLongEdge / height));
            }

            Thumbnails.of(originalPath.toFile())
                    .size(tw, th)
                    .outputQuality(0.9)
                    .toFile(thumbPath.toFile());

            BufferedImage thumbImage = ImageIO.read(thumbPath.toFile());
            int thumbWidth = thumbImage.getWidth();
            int thumbHeight = thumbImage.getHeight();

            storage.saveOriginal(key, originalPath);
            storage.saveThumb(key, thumbPath);

            String base = properties.getPublicBaseUrl();
            String url = base + "/api/v1/media/files/" + key;
            String thumbUrl = base + "/api/v1/media/files/" + key + "/thumb";

            Media media = new Media();
            media.setStorageType(storage.storageType());
            media.setStorageKey(key);
            media.setUrl(url);
            media.setThumbUrl(thumbUrl);
            media.setSize(file.getSize());
            media.setContentType(contentType);
            media.setWidth(width);
            media.setHeight(height);
            media.setThumbWidth(thumbWidth);
            media.setThumbHeight(thumbHeight);
            media.setCategory(cat);
            media.setFolderId(folderId);
            Long uid = currentUser.userId();
            media.setUploadedBy(uid);
            mediaMapper.insert(media);

            MediaUploadResponse resp = new MediaUploadResponse();
            resp.setKey(key);
            resp.setUrl(url);
            resp.setThumbUrl(thumbUrl);
            resp.setWidth(width);
            resp.setHeight(height);
            resp.setThumbWidth(thumbWidth);
            resp.setThumbHeight(thumbHeight);
            resp.setCategory(cat);
            resp.setFolderId(folderId);
            return resp;
        } finally {
            Files.deleteIfExists(originalPath);
            Files.deleteIfExists(thumbPath);
            Files.deleteIfExists(tempDir);
        }
    }

    public byte[] readFileBytes(String key, boolean thumb) throws IOException {
        Media media = getByKey(key);
        return mediaFileAccess.read(media, thumb);
    }

    public Media getByKey(String key) {
        Media media = mediaMapper.selectOne(Wrappers.lambdaQuery(Media.class).eq(Media::getStorageKey, key));
        if (media == null) {
            throw new BizException(4042, "媒体不存在");
        }
        return media;
    }

    /**
     * 按当前 {@link MediaProperties#getPublicBaseUrl()} 重写已存储的媒体绝对 URL，
     * 避免换域名或启用 HTTPS 后仍返回旧 http://IP:8082，导致混合内容拦截或端口不可达。
     */
    public String normalizePublicMediaUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        String marker = "/api/v1/media/files/";
        int idx = url.indexOf(marker);
        if (idx < 0) {
            return url;
        }
        String pathFromApi = url.substring(idx);
        String base = properties.getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + pathFromApi;
    }

    public ThumbInfoResponse thumbInfo(String key) {
        Media media = getByKey(key);
        ThumbInfoResponse resp = new ThumbInfoResponse();
        resp.setThumbUrl(normalizePublicMediaUrl(media.getThumbUrl()));
        resp.setWidth(media.getThumbWidth());
        resp.setHeight(media.getThumbHeight());
        return resp;
    }

    public IPage<MediaListItemResponse> listMyMedia(int page, int size, Long folderId) {
        Long uid = currentUser.userId();
        Page<Media> mpPage = new Page<>(page + 1L, size);
        var w = Wrappers.lambdaQuery(Media.class)
                .eq(Media::getUploadedBy, uid);
        if (folderId != null) {
            w.eq(Media::getFolderId, folderId);
        }
        w.orderByDesc(Media::getCreatedAt);
        IPage<Media> p = mediaMapper.selectPage(mpPage, w);
        return p.convert(this::toListItem);
    }

    public List<MediaCategoryResponse> listCategories() {
        Long uid = currentUser.userId();
        List<Media> all = mediaMapper.selectList(
                Wrappers.lambdaQuery(Media.class)
                        .eq(Media::getUploadedBy, uid)
                        .orderByAsc(Media::getCategory));
        Map<String, Long> grouped = all.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getCategory() != null ? m.getCategory() : "unknown",
                        Collectors.counting()));
        return grouped.entrySet().stream()
                .map(e -> new MediaCategoryResponse(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private String resolveStorageCategory(Long folderId, String category) {
        if (folderId != null) {
            return "folder-" + folderId;
        }
        if (StringUtils.hasText(category)) {
            String c = category.trim().toLowerCase(Locale.ROOT);
            if (!CATEGORY_PATTERN.matcher(c).matches()) {
                throw new BizException(4002, "无效的分类标识");
            }
            return c;
        }
        return "general";
    }

    public void deleteMyMedia(String key) {
        Long uid = currentUser.userId();
        Media media = mediaMapper.selectOne(Wrappers.lambdaQuery(Media.class)
                .eq(Media::getStorageKey, key)
                .eq(Media::getUploadedBy, uid));
        if (media == null) {
            throw new BizException(4042, "媒体不存在或无权删除");
        }
        try {
            mediaFileAccess.delete(media);
        } catch (IOException e) {
            throw new BizException(5002, "删除媒体文件失败: " + e.getMessage());
        }
        mediaMapper.deleteById(media.getId());
    }

    private MediaListItemResponse toListItem(Media m) {
        MediaListItemResponse r = new MediaListItemResponse();
        r.setKey(m.getStorageKey());
        r.setUrl(normalizePublicMediaUrl(m.getUrl()));
        r.setThumbUrl(normalizePublicMediaUrl(m.getThumbUrl()));
        r.setContentType(m.getContentType());
        r.setSize(m.getSize());
        r.setWidth(m.getWidth());
        r.setHeight(m.getHeight());
        r.setThumbWidth(m.getThumbWidth());
        r.setThumbHeight(m.getThumbHeight());
        r.setCategory(m.getCategory());
        r.setFolderId(m.getFolderId());
        r.setCreatedAt(m.getCreatedAt());
        return r;
    }

    private MediaStorage activeStorage() {
        if (properties.isMinioEnabled()) {
            MinioMediaStorage minio = minioMediaStorage.getIfAvailable();
            if (minio == null) {
                throw new BizException(5001, "已配置 storage.type=minio 但 MinIO 未就绪");
            }
            return minio;
        }
        return localMediaStorage;
    }

    private String guessExt(String originalName, String contentType) {
        if (StringUtils.hasText(originalName) && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf('.') + 1).trim().toLowerCase(Locale.ROOT);
            if (ext.length() <= 5) {
                return ext;
            }
        }
        if (contentType == null) {
            return null;
        }
        if (contentType.contains("png")) {
            return "png";
        }
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return "jpg";
        }
        if (contentType.contains("webp")) {
            return "webp";
        }
        if (contentType.contains("gif")) {
            return "gif";
        }
        if (contentType.contains("bmp")) {
            return "bmp";
        }
        return null;
    }
}
