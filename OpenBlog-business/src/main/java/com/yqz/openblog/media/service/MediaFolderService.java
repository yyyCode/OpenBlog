package com.yqz.openblog.media.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.media.dto.MediaFolderFlatItemResponse;
import com.yqz.openblog.media.dto.MediaFolderTreeNodeResponse;
import com.yqz.openblog.media.dto.MediaFolderUpsertRequest;
import com.yqz.openblog.media.entity.Media;
import com.yqz.openblog.media.entity.MediaFolder;
import com.yqz.openblog.media.repo.MediaFolderRepository;
import com.yqz.openblog.media.repo.MediaMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class MediaFolderService {

    private final MediaFolderRepository folderRepository;
    private final MediaMapper mediaMapper;

    public MediaFolderService(MediaFolderRepository folderRepository, MediaMapper mediaMapper) {
        this.folderRepository = folderRepository;
        this.mediaMapper = mediaMapper;
    }

    public List<MediaFolderTreeNodeResponse> listTree() {
        List<MediaFolder> all = folderRepository.findAllByOrderBySortOrderAscIdAsc();
        return buildTree(all);
    }

    public List<MediaFolderFlatItemResponse> listFlat() {
        List<MediaFolder> all = folderRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, MediaFolder> byId = indexById(all);
        List<MediaFolderFlatItemResponse> items = new ArrayList<>();
        for (MediaFolder f : all) {
            MediaFolderFlatItemResponse r = new MediaFolderFlatItemResponse();
            r.setId(f.getId());
            r.setName(f.getName());
            r.setParentId(f.getParentId());
            r.setSortOrder(f.getSortOrder());
            r.setPath(buildPathNames(f.getId(), byId));
            items.add(r);
        }
        return items;
    }

    @Transactional
    public MediaFolderFlatItemResponse create(MediaFolderUpsertRequest req) {
        MediaFolder f = new MediaFolder();
        apply(f, req, null);
        f = folderRepository.save(f);
        return toFlatItem(f, indexById(folderRepository.findAllByOrderBySortOrderAscIdAsc()));
    }

    @Transactional
    public MediaFolderFlatItemResponse update(Long id, MediaFolderUpsertRequest req) {
        MediaFolder f = folderRepository.findById(id)
                .orElseThrow(() -> new BizException(4041, "文件夹不存在"));
        apply(f, req, id);
        f = folderRepository.save(f);
        return toFlatItem(f, indexById(folderRepository.findAllByOrderBySortOrderAscIdAsc()));
    }

    @Transactional
    public void delete(Long id) {
        MediaFolder f = folderRepository.findById(id)
                .orElseThrow(() -> new BizException(4041, "文件夹不存在"));
        if (folderRepository.existsByParentId(f.getId())) {
            throw new BizException(4091, "请先删除或移动子文件夹");
        }
        LambdaQueryWrapper<Media> w = Wrappers.lambdaQuery();
        w.eq(Media::getFolderId, f.getId());
        Long count = mediaMapper.selectCount(w);
        if (count != null && count > 0) {
            throw new BizException(4091, "该文件夹下仍有文件，无法删除");
        }
        folderRepository.deleteById(id);
    }

    public Set<Long> collectSelfAndDescendantIds(Long folderId) {
        if (folderId == null) {
            return Collections.emptySet();
        }
        List<MediaFolder> all = folderRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (MediaFolder f : all) {
            if (f.getParentId() == null) {
                continue;
            }
            childrenMap.computeIfAbsent(f.getParentId(), k -> new ArrayList<>()).add(f.getId());
        }
        Set<Long> out = new LinkedHashSet<>();
        collectDescendants(folderId, childrenMap, out);
        return out;
    }

    private void collectDescendants(Long id, Map<Long, List<Long>> childrenMap, Set<Long> out) {
        if (id == null || !out.add(id)) {
            return;
        }
        List<Long> children = childrenMap.getOrDefault(id, List.of());
        for (Long childId : children) {
            collectDescendants(childId, childrenMap, out);
        }
    }

    private void apply(MediaFolder f, MediaFolderUpsertRequest req, Long excludeId) {
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) {
            throw new BizException(4000, "文件夹名称不能为空");
        }
        Long parentId = req.getParentId();
        if (parentId != null) {
            if (excludeId != null && parentId.equals(excludeId)) {
                throw new BizException(4000, "父文件夹不能是自己");
            }
            if (!folderRepository.existsById(parentId)) {
                throw new BizException(4000, "父文件夹不存在");
            }
            if (excludeId != null && isDescendant(excludeId, parentId)) {
                throw new BizException(4000, "父文件夹不能是当前文件夹的子节点");
            }
        }
        f.setName(name);
        f.setParentId(parentId);
        f.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
    }

    private boolean isDescendant(Long ancestorId, Long nodeId) {
        Map<Long, MediaFolder> byId = indexById(folderRepository.findAllByOrderBySortOrderAscIdAsc());
        Long current = nodeId;
        while (current != null) {
            if (current.equals(ancestorId)) {
                return true;
            }
            MediaFolder f = byId.get(current);
            current = f == null ? null : f.getParentId();
        }
        return false;
    }

    private List<MediaFolderTreeNodeResponse> buildTree(List<MediaFolder> all) {
        Map<Long, MediaFolderTreeNodeResponse> nodes = new LinkedHashMap<>();
        // count files per folder
        Map<Long, Long> fileCounts = new HashMap<>();
        List<Media> allMedia = mediaMapper.selectList(Wrappers.lambdaQuery(Media.class).isNotNull(Media::getFolderId));
        for (Media m : allMedia) {
            Long fid = m.getFolderId();
            if (fid != null) {
                fileCounts.merge(fid, 1L, Long::sum);
            }
        }

        for (MediaFolder f : all) {
            MediaFolderTreeNodeResponse n = new MediaFolderTreeNodeResponse();
            n.setId(f.getId());
            n.setName(f.getName());
            n.setParentId(f.getParentId());
            n.setSortOrder(f.getSortOrder());
            n.setFileCount(fileCounts.getOrDefault(f.getId(), 0L));
            nodes.put(f.getId(), n);
        }
        List<MediaFolderTreeNodeResponse> roots = new ArrayList<>();
        for (MediaFolder f : all) {
            MediaFolderTreeNodeResponse n = nodes.get(f.getId());
            if (f.getParentId() == null) {
                roots.add(n);
                continue;
            }
            MediaFolderTreeNodeResponse parent = nodes.get(f.getParentId());
            if (parent == null) {
                roots.add(n);
            } else {
                parent.getChildren().add(n);
            }
        }
        return roots;
    }

    private Map<Long, MediaFolder> indexById(List<MediaFolder> all) {
        Map<Long, MediaFolder> map = new HashMap<>();
        for (MediaFolder f : all) {
            map.put(f.getId(), f);
        }
        return map;
    }

    private List<String> buildPathNames(Long folderId, Map<Long, MediaFolder> byId) {
        List<String> path = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long current = folderId;
        while (current != null && visited.add(current)) {
            MediaFolder f = byId.get(current);
            if (f == null) {
                break;
            }
            path.add(0, f.getName());
            current = f.getParentId();
        }
        return path;
    }

    private MediaFolderFlatItemResponse toFlatItem(MediaFolder f, Map<Long, MediaFolder> byId) {
        MediaFolderFlatItemResponse r = new MediaFolderFlatItemResponse();
        r.setId(f.getId());
        r.setName(f.getName());
        r.setParentId(f.getParentId());
        r.setSortOrder(f.getSortOrder());
        r.setPath(buildPathNames(f.getId(), byId));
        return r;
    }
}
