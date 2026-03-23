package com.yqz.openblog.media.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.media.MediaProperties;
import com.yqz.openblog.media.dto.MediaUploadResponse;
import com.yqz.openblog.media.dto.ThumbInfoResponse;
import com.yqz.openblog.media.entity.Media;
import com.yqz.openblog.media.repo.MediaMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.security.CurrentUser;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class MediaService {

    private final MediaMapper mediaMapper;
    private final MediaProperties properties;
    private final CurrentUser currentUser;

    public MediaService(MediaMapper mediaMapper, MediaProperties properties, CurrentUser currentUser) {
        this.mediaMapper = mediaMapper;
        this.properties = properties;
        this.currentUser = currentUser;
    }

    public MediaUploadResponse upload(org.springframework.web.multipart.MultipartFile file) throws IOException {
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

        String key = UUID.randomUUID().toString() + "." + ext;

        // 修改点1: 获取配置的根目录（应该是绝对路径）
        String rootPath = properties.getRootPath();
        // 如果配置的是相对路径，转换为绝对路径
        Path root = Path.of(rootPath).toAbsolutePath().normalize();

        // 修改点2: 使用绝对路径创建目录
        Path originalDir = root.resolve("original");
        Path thumbDir = root.resolve("thumb");
        Files.createDirectories(originalDir);
        Files.createDirectories(thumbDir);

        // 修改点3: 构建绝对路径的文件路径
        Path originalPath = originalDir.resolve(key);
        Path thumbPath = thumbDir.resolve(key);

        // 保存原图
        Files.copy(file.getInputStream(), originalPath, StandardCopyOption.REPLACE_EXISTING);

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

        String base = properties.getPublicBaseUrl();
        String url = base + "/api/v1/media/files/" + key;
        String thumbUrl = base + "/api/v1/media/files/" + key + "/thumb";

        Media media = new Media();
        media.setStorageType("LOCAL");
        media.setStorageKey(key);
        // 修改点4: 可选 - 存储文件的绝对路径，方便后续操作
        media.setUrl(url);
        media.setThumbUrl(thumbUrl);
        media.setSize(file.getSize());
        media.setContentType(contentType);
        media.setWidth(width);
        media.setHeight(height);
        media.setThumbWidth(thumbWidth);
        media.setThumbHeight(thumbHeight);
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
        return resp;
    }

    public Media getByKey(String key) {
        Media media = mediaMapper.selectOne(Wrappers.lambdaQuery(Media.class).eq(Media::getStorageKey, key));
        if (media == null) {
            throw new BizException(4042, "媒体不存在");
        }
        return media;
    }

    public ThumbInfoResponse thumbInfo(String key) {
        Media media = getByKey(key);
        ThumbInfoResponse resp = new ThumbInfoResponse();
        resp.setThumbUrl(media.getThumbUrl());
        resp.setWidth(media.getThumbWidth());
        resp.setHeight(media.getThumbHeight());
        return resp;
    }

    private String guessExt(String originalName, String contentType) {
        if (StringUtils.hasText(originalName) && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf('.') + 1).trim().toLowerCase(Locale.ROOT);
            if (ext.length() <= 5) return ext;
        }
        if (contentType == null) return null;
        if (contentType.contains("png")) return "png";
        if (contentType.contains("jpeg") || contentType.contains("jpg")) return "jpg";
        if (contentType.contains("webp")) return "webp";
        if (contentType.contains("gif")) return "gif";
        if (contentType.contains("bmp")) return "bmp";
        return null;
    }
}

