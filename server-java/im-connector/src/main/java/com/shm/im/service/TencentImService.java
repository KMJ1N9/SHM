package com.shm.im.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.im.config.ImConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 腾讯云 IM REST API 封装（与 Node.js utils/im-api.js + services/im/tencent.js 行为一致）
 *
 * <h3>已实现 API</h3>
 * <ul>
 *   <li>账号导入 — POST /v4/im_open_login_svc/account_import</li>
 *   <li>发送单聊消息 — POST /v4/openim/sendmsg</li>
 * </ul>
 *
 * @see <a href="https://cloud.tencent.com/document/product/269/1608">账号导入</a>
 * @see <a href="https://cloud.tencent.com/document/product/269/2282">发送单聊消息</a>
 */
@Service
public class TencentImService {

    private static final Logger log = LoggerFactory.getLogger(TencentImService.class);

    /** 腾讯云 IM REST API 基础地址 */
    private static final String IM_REST_BASE = "https://console.tim.qq.com";

    /** RFC 4.2 伪随机数上限 */
    private static final long RANDOM_MAX = 4_294_967_295L;

    private final ImConfig imConfig;
    private final UserSigService userSigService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public TencentImService(ImConfig imConfig,
                            UserSigService userSigService,
                            RestTemplate restTemplate,
                            ObjectMapper objectMapper) {
        this.imConfig = imConfig;
        this.userSigService = userSigService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // 公开 API
    // ============================================================

    /**
     * 导入 IM 账号
     *
     * <p>腾讯云 IM 发送消息前要求接收方账号已存在。SDK login() 会自动创建登录者账号，
     * 但从未登录过的用户（如从未打开小程序的卖家）需通过 REST API 导入。
     *
     * <p>同时设置 Nick 和 FaceUrl —— 避免 IM 服务端因缺少资料而自动生成占位昵称。
     *
     * @param userId  要导入的 UserID
     * @param nick    用户昵称（可选）
     * @param faceUrl 头像 URL（可选）
     * @return API 响应 Map
     */
    public Map<String, Object> importAccount(String userId, String nick, String faceUrl) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("UserID", userId);
        if (nick != null && !nick.isEmpty()) {
            body.put("Nick", nick);
        }
        if (faceUrl != null && !faceUrl.isEmpty()) {
            body.put("FaceUrl", faceUrl);
        }
        return callRestApi("/v4/im_open_login_svc/account_import", body);
    }

    /**
     * 发送单聊消息（系统通知）
     *
     * <p>从管理员账号发送自定义消息到指定用户。消息类型为 TIMCustomElem，
     * 前端解析 Data 中的 JSON 展示对应 UI。
     *
     * @param toUserId 接收方用户 ID
     * @param title    消息标题
     * @param content  消息内容
     * @param extra    扩展字段（如 order_id、report_id、跳转路径等），可为 null
     * @return API 响应 Map
     */
    public Map<String, Object> sendSystemMessage(String toUserId, String title, String content,
                                                  Map<String, Object> extra) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("type", "system");
        data.put("title", title);
        data.put("content", content);
        data.put("extra", extra != null ? extra : new java.util.HashMap<>());

        Map<String, Object> msgContent = new java.util.LinkedHashMap<>();
        msgContent.put("Data", toJson(data));

        Map<String, Object> msgElement = new java.util.LinkedHashMap<>();
        msgElement.put("MsgType", "TIMCustomElem");
        msgElement.put("MsgContent", msgContent);

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("From_Account", imConfig.getAdminAccount());
        body.put("To_Account", toUserId);
        body.put("MsgRandom", random.nextInt(Integer.MAX_VALUE));
        body.put("MsgBody", List.of(msgElement));

        return callRestApi("/v4/openim/sendmsg", body);
    }

    /**
     * 批量发送系统消息（群发通知）
     *
     * <h3>Node.js 差异</h3>
     * <p>Node.js 版本使用 {@code /v4/openim/sendmsg} + 数组 {@code To_Account} 实现群发
     * （腾讯云非标准用法，单聊端点传数组）。Java 版本改用腾讯云专用批量端点
     * {@code /v4/openim/batchsendmsg}，请求路径和响应结构与 Node.js 不完全一致：
     * <ul>
     *   <li>请求路径：{@code /v4/openim/batchsendmsg}（Node.js: {@code /v4/openim/sendmsg}）</li>
     *   <li>To_Account 类型：JSON 数组（Node.js: 同端点但行为依赖腾讯云隐式类型检测）</li>
     *   <li>成功时 ErrorCode=0 一致，失败时 ErrorInfo 文案可能不同</li>
     * </ul>
     * <p>调用的 Feign 客户端（core-service / admin-service）应将响应中的
     * {@code ErrorCode} 与 0 比较，不应依赖 ErrorInfo 文案做分支判断。
     *
     * <p>向多个用户发送同一条系统消息。单次最多 500 个用户。
     *
     * @param toUserIds 接收方用户 ID 列表
     * @param title     消息标题
     * @param content   消息内容
     * @param extra     扩展字段（如 order_id、report_id、跳转路径等），可为 null
     * @return API 响应 Map
     */
    public Map<String, Object> sendBatchSystemMessage(List<String> toUserIds, String title,
                                                       String content, Map<String, Object> extra) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("type", "system");
        data.put("title", title);
        data.put("content", content);
        data.put("extra", extra != null ? extra : new java.util.HashMap<>());

        Map<String, Object> msgContent = new java.util.LinkedHashMap<>();
        msgContent.put("Data", toJson(data));

        Map<String, Object> msgElement = new java.util.LinkedHashMap<>();
        msgElement.put("MsgType", "TIMCustomElem");
        msgElement.put("MsgContent", msgContent);

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("From_Account", imConfig.getAdminAccount());
        body.put("To_Account", toUserIds);
        body.put("MsgRandom", random.nextInt(Integer.MAX_VALUE));
        body.put("MsgBody", List.of(msgElement));

        return callRestApi("/v4/openim/batchsendmsg", body);
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /**
     * 构造 REST API 请求 URL（含签名参数）
     *
     * <p>URL 格式：
     * {@code https://console.tim.qq.com{path}?sdkappid={appId}&identifier={admin}&usersig={sig}&random={r}&contenttype=json}
     */
    private String buildRestUrl(String path) {
        String identifier = imConfig.getAdminAccount();
        String userSig = userSigService.generateUserSig(identifier);
        long r = (random.nextLong() & Long.MAX_VALUE) % RANDOM_MAX;

        return IM_REST_BASE + path
                + "?sdkappid=" + imConfig.getSdkAppId()
                + "&identifier=" + urlEncode(identifier)
                + "&usersig=" + urlEncode(userSig)
                + "&random=" + r
                + "&contenttype=json";
    }

    /**
     * 调用 IM REST API
     *
     * @param path API 路径
     * @param body 请求体
     * @return 解析后的响应 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callRestApi(String path, Map<String, Object> body) {
        String url = buildRestUrl(path);
        log.debug("IM REST API 调用: {}", path);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            Map<String, Object> resp = restTemplate.postForObject(url, request, Map.class);

            if (resp != null) {
                Object errorCode = resp.get("ErrorCode");
                if (errorCode instanceof Number && ((Number) errorCode).intValue() != 0) {
                    log.error("IM REST API 错误: path={}, ErrorCode={}, ErrorInfo={}",
                            path, errorCode, resp.get("ErrorInfo"));
                }
            }

            return resp != null ? resp : Map.of("ErrorCode", -1, "ErrorInfo", "empty response");
        } catch (RestClientException e) {
            log.error("IM REST API 调用失败: path={}, error={}", path, e.getMessage());
            return Map.of("ErrorCode", -1, "ErrorInfo", e.getMessage());
        }
    }

    /** URL 编码 */
    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** JSON 序列化（使用 Jackson ObjectMapper，处理所有 Java 类型） */
    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("IM 消息 JSON 序列化失败", e);
        }
    }
}
