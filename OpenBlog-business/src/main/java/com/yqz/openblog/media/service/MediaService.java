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
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleBody;
import com.yqz.openblog.article.repo.ArticleBodyMapper;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.project.entity.Project;
import com.yqz.openblog.project.repo.ProjectRepository;
import com.yqz.openblog.smallcompany.entity.SmallCompany;
import com.yqz.openblog.smallcompany.repo.SmallCompanyRepository;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class MediaService {

    private static final Pattern CATEGORY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    /** 允许入库/回写的图片 Content-Type 白名单（与解码器可识别的栅格格式一致，不含 svg 等可内联脚本的格式）。 */
    private static final Map<String, String> EXT_BY_CONTENT_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/bmp", "bmp",
            "image/webp", "webp"
    );
    private static final Set<String> SAFE_CONTENT_TYPES = EXT_BY_CONTENT_TYPE.keySet();

    private final MediaMapper mediaMapper;
    private final MediaProperties properties;
    private final CurrentUser currentUser;
    private final MediaFileAccess mediaFileAccess;
    private final LocalMediaStorage localMediaStorage;
    private final ObjectProvider<MinioMediaStorage> minioMediaStorage;
    private final ArticleMapper articleMapper;
    private final ArticleBodyMapper articleBodyMapper;
    private final ProjectRepository projectRepository;
    private final SmallCompanyRepository smallCompanyRepository;

    public MediaService(MediaMapper mediaMapper,
                        MediaProperties properties,
                        CurrentUser currentUser,
                        MediaFileAccess mediaFileAccess,
                        LocalMediaStorage localMediaStorage,
                        ObjectProvider<MinioMediaStorage> minioMediaStorage,
                        ArticleMapper articleMapper,
                        ArticleBodyMapper articleBodyMapper,
                        ProjectRepository projectRepository,
                        SmallCompanyRepository smallCompanyRepository) {
        this.mediaMapper = mediaMapper;
        this.properties = properties;
        this.currentUser = currentUser;
        this.mediaFileAccess = mediaFileAccess;
        this.localMediaStorage = localMediaStorage;
        this.minioMediaStorage = minioMediaStorage;
        this.articleMapper = articleMapper;
        this.articleBodyMapper = articleBodyMapper;
        this.projectRepository = projectRepository;
        this.smallCompanyRepository = smallCompanyRepository;
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

        String cat = resolveStorageCategory(folderId, category);
        MediaStorage storage = activeStorage();

        Path tempDir = Files.createTempDirectory("openblog-media-");
        Path originalPath = tempDir.resolve("original");
        // 缩略图输出必须带扩展名：Thumbnailator 靠扩展名推断输出格式。
        // 无扩展名时它会在同目录生成 thumb.JPEG 而目标文件不落盘（ImageIO 读不回、清理时目录非空 → DirectoryNotEmptyException）。
        // ext 要等权威格式判定后才能确定，故 thumbPath 延迟到判定后赋值。
        Path thumbPath = null;
        try {
            Files.copy(file.getInputStream(), originalPath);

            BufferedImage originalImage = ImageIO.read(originalPath.toFile());
            if (originalImage == null) {
                throw new BizException(4002, "图片解析失败");
            }

            // 权威判定：由解码器识别的实际格式推导 Content-Type 与后缀，不信任客户端文件名/Content-Type（防伪造 image/svg+xml 之类）
            String detectedType = detectImageContentType(originalPath);
            if (detectedType == null) {
                throw new BizException(4002, "不支持的图片格式");
            }
            String ext = EXT_BY_CONTENT_TYPE.get(detectedType);
            String key = cat + "/" + UUID.randomUUID().toString() + "." + ext;
            thumbPath = tempDir.resolve("thumb." + ext);

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
            media.setContentType(detectedType);
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
            // 递归清掉整个临时目录：任何库（如 Thumbnailator 无扩展名输出生成的 thumb.JPEG）留下的残留都不再触发 DirectoryNotEmptyException
            deleteRecursively(tempDir);
        }
    }

    /** 递归删除目录及其全部内容；文件不存在时静默返回。 */
    private static void deleteRecursively(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
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
        assertNotReferenced(media);
        try {
            mediaFileAccess.delete(media);
        } catch (IOException e) {
            throw new BizException(5002, "删除媒体文件失败: " + e.getMessage());
        }
        mediaMapper.deleteById(media.getId());
    }

    /**
     * 删除媒体前的引用校验：若图片仍被文章/项目封面、公司 Logo（DB 列存 key，精确匹配）
     * 或文章/项目正文 Markdown（存完整媒体链接，按 {@code /api/v1/media/files/{key}} 匹配）
     * 引用，则拒绝删除并提示引用位置，避免删除后页面破链。
     */
    private void assertNotReferenced(Media media) {
        String key = media.getStorageKey();
        if (key == null || key.isEmpty()) {
            return;
        }
        String marker = "/api/v1/media/files/" + key;
        List<String> refs = new ArrayList<>();

        // 文章封面列精确引用
        for (Article a : articleMapper.selectList(
                Wrappers.lambdaQuery(Article.class).eq(Article::getCoverMediaKey, key))) {
            refs.add("文章《" + titleOf(a.getTitle()) + "》封面");
        }

        // 项目封面列 / 项目正文
        for (Project p : projectRepository.findAll()) {
            if (key.equals(p.getCoverMediaKey())) {
                refs.add("项目《" + titleOf(p.getTitle()) + "》封面");
            }
            if (p.getContentMarkdown() != null && p.getContentMarkdown().contains(marker)) {
                refs.add("项目《" + titleOf(p.getTitle()) + "》正文");
            }
        }

        // 公司 Logo 列
        for (SmallCompany sc : smallCompanyRepository.findAll()) {
            if (key.equals(sc.getLogoMediaKey())) {
                refs.add("公司「" + titleOf(sc.getName()) + "」Logo");
            }
        }

        // 文章正文内嵌
        List<ArticleBody> bodies = articleBodyMapper.selectList(
                Wrappers.lambdaQuery(ArticleBody.class).like(ArticleBody::getContentMarkdown, marker));
        if (!bodies.isEmpty()) {
            Set<Long> articleIds = bodies.stream().map(ArticleBody::getArticleId).collect(Collectors.toSet());
            Map<Long, Article> byId = articleMapper.selectBatchIds(articleIds).stream()
                    .collect(Collectors.toMap(Article::getId, a -> a, (a, b) -> a));
            for (ArticleBody b : bodies) {
                Article a = byId.get(b.getArticleId());
                refs.add("文章《" + titleOf(a != null ? a.getTitle() : "#" + b.getArticleId()) + "》正文");
            }
        }

        if (refs.isEmpty()) {
            return;
        }
        int extra = refs.size() - 5;
        String detail = String.join("、", refs.size() > 5 ? refs.subList(0, 5) : refs);
        if (extra > 0) {
            detail += " 等 " + refs.size() + " 处";
        }
        throw new BizException(4091, "该图片仍被引用（" + detail + "），请先解除引用后再删除");
    }

    private static String titleOf(String v) {
        return v == null || v.isBlank() ? "(未命名)" : v;
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

    /**
     * 由解码器实际识别的格式推导 Content-Type（ImageIO 按文件头识别，客户端无法伪造）。
     * 非白名单格式（svg/tiff/ico 等）返回 null，由调用方拒绝。
     */
    static String detectImageContentType(Path file) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            String formatName = readers.next().getFormatName().toLowerCase(Locale.ROOT);
            return switch (formatName) {
                case "png" -> "image/png";
                case "jpeg", "jpg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "bmp", "wbmp" -> "image/bmp";
                case "webp" -> "image/webp";
                default -> null;
            };
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 输出 Content-Type 归一化：历史遗留记录可能存过客户端伪造的类型（如 image/svg+xml），
     * 服务端回写前必须收口到白名单；非法类型一律降级 {@code application/octet-stream}（触发下载而非内联解析）。
     */
    public static String sanitizeContentType(String stored) {
        if (stored != null && SAFE_CONTENT_TYPES.contains(stored.toLowerCase(Locale.ROOT))) {
            return stored;
        }
        return "application/octet-stream";
    }
}
