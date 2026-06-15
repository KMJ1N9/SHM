package com.shm.im.controller;

import com.shm.common.util.ResponseBuilder;
import com.shm.im.service.CosService;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 上传控制器 — COS STS 临时凭证 + 开发环境服务端图片上传
 *
 * <p>生产环境：前端拿 STS 临时密钥直传 COS，图片不经过服务器。
 * <p>开发环境（COS 未配置真实凭证时）：前端回退到 {@code POST /api/upload/image}，
 * 图片存储于服务端文件系统，由 {@code StaticResourceConfig} 提供静态文件访问。
 *
 * <p>安全模型：Gateway 的 {@code JwtAuthGatewayFilter} 验证 JWT → 注入 {@code X-User-Id} 请求头 →
 * 本控制器从请求头读取（与 Node.js {@code req.user.id} 行为一致，用户无法伪造）。
 * 按 userId 隔离上传路径（user_{userId}/）。
 */
@RestController
@RequestMapping("/api/upload")
@Validated
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    /** 允许的 MIME 类型（与 Node.js upload.js 一致） */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    /** 单文件最大 5MB */
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private final CosService cosService;
    private final Path imageStoragePath;

    public UploadController(CosService cosService,
                            @Value("${image.storage.path:#{null}}") String configuredPath) {
        this.cosService = cosService;
        if (configuredPath != null && !configuredPath.isBlank()) {
            this.imageStoragePath = Paths.get(configuredPath).toAbsolutePath().normalize();
        } else {
            this.imageStoragePath = resolveImageDir();
        }
    }

    /**
     * 自动探测图片存储目录（与 ImageServingController 逻辑完全一致）
     */
    private static Path resolveImageDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        // 候选路径 — 按优先级排序。
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
            if (Files.isDirectory(path)) {
                log.info("[UploadController] 图片存储目录命中 [{}]: {}", c[1], path);
                return path;
            }
        }
        Path fallback = cwd.resolve("../../server/public/images").normalize();
        log.warn("[UploadController] 所有候选路径均不存在，使用兜底: {}", fallback);
        return fallback;
    }

    /**
     * 获取 COS 临时上传凭证
     *
     * <p>前端拿到凭证后用 COS JS SDK 直传图片。凭证 30 分钟过期，前端需提前刷新。
     *
     * <p>userId 从 Gateway 注入的 {@code X-User-Id} 请求头读取（JWT 已验证），
     * 前端无需传参，与 Node.js {@code req.user.id} 行为完全一致。
     *
     * @param userId 用户 ID（由 Gateway JWT 鉴权后注入）
     * @return { code: 0, message: "ok", data: { credentials, policy, expiredTime, prefix, ... } }
     */
    @GetMapping("/cos-credential")
    public Map<String, Object> getCredential(
            @RequestHeader("X-User-Id") @Positive(message = "用户 ID 必须为正整数") Long userId) {
        Map<String, Object> data = cosService.generateCredential(userId);
        return ResponseBuilder.ok(data);
    }

    /**
     * 开发环境服务端图片上传（COS 未配置时的回退方案）
     *
     * <p>前端在 {@code credential.mock === true} 时调用此端点，
     * 返回可被 {@code /images/} 静态资源服务访问的 URL 路径。
     *
     * <p>与 Node.js {@code uploadController.uploadImage} 行为完全一致：
     * <ul>
     *   <li>存储路径：{@code images/user_{userId}/}</li>
     *   <li>文件名：{@code {timestamp}_{6位随机hex}.{ext}}</li>
     *   <li>返回：{@code { code: 0, data: { url: "/images/user_6/xxx.jpg" } }}</li>
     * </ul>
     *
     * @param userId 用户 ID（由 Gateway JWT 鉴权后注入）
     * @param file   上传的图片文件（multipart/form-data, field name = "file"）
     * @return { code: 0, message: "ok", data: { url: "/images/user_6/xxx.jpg" } }
     */
    @PostMapping("/image")
    public Map<String, Object> uploadImage(
            @RequestHeader("X-User-Id") @Positive(message = "用户 ID 必须为正整数") Long userId,
            @RequestParam("file") MultipartFile file) {

        // 1. 文件类型校验（与 Node.js upload.js fileFilter 一致）
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ResponseBuilder.error(5011, "不支持的图片格式: " + contentType);
        }

        // 2. 文件大小校验
        if (file.getSize() > MAX_SIZE) {
            return ResponseBuilder.error(5010, "图片大小不能超过 5MB");
        }

        if (file.isEmpty()) {
            return ResponseBuilder.error(5012, "请选择要上传的图片");
        }

        // 3. 确保用户子目录存在
        Path userDir = imageStoragePath.resolve("user_" + userId);
        try {
            Files.createDirectories(userDir);
        } catch (IOException e) {
            log.error("创建用户图片目录失败: {}", userDir, e);
            return ResponseBuilder.error(6999, "服务器内部错误");
        }

        // 4. 生成唯一文件名（与 Node.js 格式一致：timestamp_randomhex.ext）
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x100000, 0xffffff));
        String ext = getExtension(contentType);
        String filename = timestamp + "_" + random + "." + ext;

        // 5. 保存文件
        Path destPath = userDir.resolve(filename);
        try {
            file.transferTo(destPath.toFile());
            log.info("图片上传成功: {}", destPath);
        } catch (IOException e) {
            log.error("图片保存失败: {}", destPath, e);
            return ResponseBuilder.error(6999, "图片保存失败，请稍后重试");
        }

        // 6. 返回可访问 URL（与 Node.js 一致：/images/user_{userId}/{filename}）
        String url = "/images/user_" + userId + "/" + filename;
        return ResponseBuilder.ok(Map.of("url", url));
    }

    /**
     * 根据 MIME 类型返回文件扩展名（与 Node.js cos.js getExtension 一致）
     */
    private String getExtension(String mimeType) {
        switch (mimeType) {
            case "image/jpeg":
            case "image/jpg":
                return "jpg";
            case "image/png":
                return "png";
            case "image/webp":
                return "webp";
            default:
                return "jpg";
        }
    }
}
