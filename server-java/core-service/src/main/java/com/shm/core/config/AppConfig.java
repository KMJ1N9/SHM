package com.shm.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用级配置 — 微信小程序参数（与 Node.js config/wx 一致）
 */
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    private final WeChat wechat = new WeChat();

    public WeChat getWechat() {
        return wechat;
    }

    public static class WeChat {
        /** 微信小程序 AppID */
        private String appId;
        /** 微信小程序 AppSecret */
        private String appSecret;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    }
}
