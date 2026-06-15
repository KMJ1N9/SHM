package com.shm.im.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * 图片文件服务 — 直接用 {@link FileSystemResource} 读取磁盘文件返回
 *
 * <p>为什么不用 Spring 的 {@code ResourceHandlerRegistry + ResourceHttpRequestHandler}？
 * <ol>
 *   <li>{@code ResourceHttpRequestHandler} 内部使用 {@code UrlResource}，在 Windows 中文路径
 *       上 {@code Path.toUri()} 会生成百分号编码 URI（如 {@code %E5%AD%A6...}），
 *       {@code UrlResource.createRelative()} 对编码路径的解析不稳定。</li>
 *   <li>手动 {@link FileSystemResource} + {@link Path#resolve(String)} 使用操作系统原生文件路径，
 *       完全不经过 URL 编码/解码层，在 Windows + 中文路径上最可靠。</li>
 * </ol>
 *
 * <p>与原 Node.js 行为等价：{@code app.use('/images', express.static('public/images'))}
 *
 * <p><b>Spring 6 PathPattern 注意：</b>{@code @GetMapping("/images/{*path}")} 使用
 * {@code {*path}} 捕获剩余全部路径段（含多层目录）。{@code /**} 在 PathPattern 中
 * 不能直接用于 @RequestMapping 映射（那是 AntPathMatcher 的语法）。
 */
@RestController
public class ImageServingController {

    private static final Logger log = LoggerFactory.getLogger(ImageServingController.class);

    private final Path imageDir;

    public ImageServingController(@Value("${image.storage.path:#{null}}") String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            this.imageDir = Paths.get(configuredPath).toAbsolutePath().normalize();
        } else {
            this.imageDir = resolveImageDir();
        }
        log.info("[ImageServingController] 图片存储目录: {}", imageDir.toAbsolutePath());
    }

    /**
     * 处理所有 {@code /images/**} 请求，直接返回磁盘文件。
     *
     * <p>{@code {*path}} 是 Spring 6 PathPattern 语法，捕获 {@code /images/} 之后的
     * 全部剩余路径段（例如 {@code user_6/1780878570529_35a387ac.jpg}），支持任意层深。
     */
    @GetMapping("/images/{*path}")
    public ResponseEntity<Resource> serveImage(@PathVariable("path") String relativePath) {
        log.debug("[ImageServingController] 请求图片: {}", relativePath);

        if (relativePath == null || relativePath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // 去掉可能的 leading "/"（PathPattern {*path} 可能带 /）
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        // 路径穿越防护：normalize 后必须在 imageDir 之内
        Path file = imageDir.resolve(relativePath).normalize();
        if (!file.startsWith(imageDir)) {
            log.warn("[ImageServingController] 路径穿越攻击已阻止: {}", relativePath);
            return ResponseEntity.notFound().build();
        }

        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            log.info("[ImageServingController] 文件不存在/不可读: {} (请求路径: {})",
                    file.toAbsolutePath(), relativePath);
            return ResponseEntity.notFound().build();
        }

        try {
            String contentType = Files.probeContentType(file);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                    .body(new FileSystemResource(file));
        } catch (IOException e) {
            log.error("[ImageServingController] 读取文件失败: {}", file, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 自动探测图片存储目录（与 {@code StaticResourceConfig} 逻辑相同）
     */
    private static Path resolveImageDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        log.info("[ImageServingController] 当前工作目录: {}", cwd);

        // 候选路径 — 按优先级排序：先精确后宽泛。
        // 注意：../../server/public/images 在 cwd=SHM/ 时解析为错误目录，
        // 若该目录因历史原因存在（如 createDirectories 残留），会提前命中而跳过
        // 正确的 server/public/images。因此将 server/public/images 放在首位。
        String[][] candidates = {
            {"server/public/images", "cwd = SHM/"},
            {"../server/public/images", "cwd = server-java/"},
            {"../../server/public/images", "cwd = im-connector/"},
        };

        for (String[] c : candidates) {
            Path path = cwd.resolve(c[0]).normalize();
            log.info("[ImageServingController] 探测候选路径 [{}]: {}", c[1], path);
            if (Files.isDirectory(path)) {
                log.info("[ImageServingController] ✅ 命中候选路径 [{}]: {}", c[1], path);
                return path;
            }
            log.info("[ImageServingController] ❌ 未命中 [{}]", c[1]);
        }

        Path fallback = cwd.resolve("../../server/public/images").normalize();
        log.warn("[ImageServingController] 所有候选路径均不存在，使用兜底: {}", fallback);
        return fallback;
    }
}
