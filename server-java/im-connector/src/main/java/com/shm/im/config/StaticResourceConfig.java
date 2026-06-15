package com.shm.im.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源服务配置 — 为开发环境提供图片文件服务
 *
 * <p>Node.js 后端通过 Express {@code app.use('/images', express.static('public/images'))}
 * 提供图片文件访问。本配置在 Spring Boot 中实现等价行为：将 {@code /images/**} URL
 * 映射到文件系统上的图片存储目录。
 *
 * <p>图片存储路径（优先级）：
 * <ol>
 *   <li>{@code IMAGE_STORAGE_PATH} 环境变量 — 绝对路径</li>
 *   <li>默认：自动探测 {@code server/public/images/} 相对 im-connector 或 server-java 目录</li>
 * </ol>
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceConfig.class);

    private final Path imageDir;

    public StaticResourceConfig(@Value("${image.storage.path:#{null}}") String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            this.imageDir = Paths.get(configuredPath).toAbsolutePath().normalize();
        } else {
            this.imageDir = resolveImageDir();
        }
        log.info("图片静态资源服务已配置 — 存储路径: {}", imageDir);
    }

    /**
     * 图片静态资源映射 — 已迁移至 {@link com.shm.im.controller.ImageServingController}
     *
     * <p>原先使用 {@code ResourceHandlerRegistry + ResourceHttpRequestHandler} 的方案在
     * Windows 中文路径上不稳定：{@code Path.toUri()} 产生百分号编码 URI，
     * {@code UrlResource.createRelative()} 对编码路径的解析可能失败。
     *
     * <p>现在由 {@code ImageServingController} 直接使用 {@code java.nio.file.Path} +
     * {@code FileSystemResource} 读取文件，完全不经过 URL 编解码层。
     *
     * <p>此方法保留不注册任何 handler，仅作历史记录。如需恢复旧方案，取消下面的注释即可。
     */
    // @Override
    // public void addResourceHandlers(ResourceHandlerRegistry registry) { ... }
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // ⬇ 图片服务已由 ImageServingController 接管，不在此注册 resource handler
        log.info("图片静态资源服务已由 ImageServingController 接管 — 不再通过 ResourceHttpRequestHandler 注册");
    }

    /**
     * 自动探测图片存储目录
     *
     * <p>Spring Boot 的 ResourceHttpRequestHandler 要求 {@code addResourceLocations}
     * 传入有效的 URI。Windows 路径含空格时，手动拼接 {@code file:} 字符串会导致
     * URL 解析失败。使用 {@link Path#toUri()} 可正确处理特殊字符。
     */
    private static Path resolveImageDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        log.info("当前工作目录: {}", cwd);

        // 按优先级尝试多个候选路径（适配 IDEA / mvn spring-boot:run / 不同模块启动）
        String[][] candidates = {
            {"server/public/images", "cwd = SHM/ (project root)"},
            {"../server/public/images", "cwd = server-java/ (mvn from parent)"},
            {"../../server/public/images", "cwd = im-connector/ (IDEA module dir)"},
        };

        for (String[] c : candidates) {
            Path path = cwd.resolve(c[0]).normalize();
            if (Files.isDirectory(path)) {
                log.info("命中候选路径 [{}]: {}", c[1], path);
                return path;
            }
            log.debug("未命中候选路径 [{}]: {}", c[1], path);
        }

        // 兜底：返回最可能的路径（后续 createDirectories 会尝试创建）
        Path fallback = cwd.resolve("../../server/public/images").normalize();
        log.warn("所有候选路径均不存在，使用兜底路径: {}", fallback);
        return fallback;
    }
}
