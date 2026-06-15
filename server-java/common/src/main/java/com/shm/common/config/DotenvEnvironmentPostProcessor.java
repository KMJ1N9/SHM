package com.shm.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 Spring Environment 最早期阶段加载 .env 文件，使所有
 * {@code ${...}} 占位符在 Bean 初始化前即可解析。
 *
 * <h3>搜索路径（按优先级）</h3>
 * <ol>
 *   <li>系统属性 {@code dotenv.path} 指定的绝对路径</li>
 *   <li>{@code user.dir}/.env — 当前工作目录</li>
 *   <li>{@code user.dir}/../.env — 父目录（适配各 service 子模块从 server-java/ 加载）</li>
 * </ol>
 *
 * <p>注册方式: {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor}
 */
@Order(Ordered.HIGHEST_PRECEDENCE) // 必须在 ConfigDataEnvironmentPostProcessor (HIGHEST_PRECEDENCE+10) 之前执行
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);
    private static final String DOTENV_FILENAME = ".env";
    private static final String PROPERTY_SOURCE_NAME = "dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path dotenvPath = resolveDotenvPath();
        if (dotenvPath == null) {
            log.warn(".env 文件未找到 — 请确认 server-java/.env 已创建（参考 .env.example）");
            return;
        }

        Map<String, Object> entries = parseDotenv(dotenvPath);
        if (entries.isEmpty()) {
            log.warn(".env 文件为空或无有效键值对: {}", dotenvPath);
            return;
        }

        // 最高优先级插入，覆盖同名系统属性/application.yml 中的占位符
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, entries));
        log.info("已加载 {} 个环境变量 (source: {})", entries.size(), dotenvPath.toAbsolutePath());
    }

    /**
     * 按优先级搜索 .env 文件，返回第一个存在的路径。
     */
    private Path resolveDotenvPath() {
        // 1. 系统属性显式指定（IDEA Run Configuration VM options: -Ddotenv.path=...）
        String explicitPath = System.getProperty("dotenv.path");
        if (explicitPath != null && !explicitPath.isBlank()) {
            Path p = Paths.get(explicitPath);
            if (Files.isRegularFile(p)) return p;
            log.warn("dotenv.path 指定的文件不存在: {}", explicitPath);
        }

        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            // 2. user.dir/.env
            Path p1 = Paths.get(userDir, DOTENV_FILENAME);
            if (Files.isRegularFile(p1)) return p1;

            // 3. user.dir/../.env   (各模块在 server-java/*/ 下运行时)
            Path p2 = Paths.get(userDir, "..", DOTENV_FILENAME).normalize();
            if (Files.isRegularFile(p2)) return p2;
        }

        return null;
    }

    /**
     * 解析 .env 文件为键值对 Map。
     *
     * <p>支持格式：
     * <ul>
     *   <li>{@code KEY=VALUE} — 标准解析</li>
     *   <li>{@code # comment} — 以 # 开头的行为注释</li>
     *   <li>空行 — 跳过</li>
     *   <li>值中的引号自动去除</li>
     * </ul>
     */
    private Map<String, Object> parseDotenv(Path path) {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.trim();
                // 跳过空行和注释
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                int eqIdx = trimmed.indexOf('=');
                if (eqIdx <= 0) continue; // 无键或键为空

                String key = trimmed.substring(0, eqIdx).trim();
                String value = trimmed.substring(eqIdx + 1).trim();

                // 去除首尾引号（双引号或单引号）
                if (value.length() >= 2) {
                    char first = value.charAt(0);
                    char last = value.charAt(value.length() - 1);
                    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                        value = value.substring(1, value.length() - 1);
                    }
                }

                if (!key.isEmpty()) {
                    map.put(key, value);
                }
            }
        } catch (IOException e) {
            log.error("读取 .env 文件失败: {} — {}", path, e.getMessage());
        }
        return map;
    }
}
