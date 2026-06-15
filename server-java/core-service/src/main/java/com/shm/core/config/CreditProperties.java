package com.shm.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 信誉分配置（与 Node.js config/credit.js 一致，支持运行时调参）
 *
 * <p>所有阈值均可通过 application.yml 的 credit.* 覆盖。
 */
@ConfigurationProperties(prefix = "credit")
public class CreditProperties {

    /** 信誉分上限 */
    private int max = 200;

    /** 发布商品最低信誉分 */
    private int publishThreshold = 60;

    /** 参与交易最低信誉分 */
    private int tradeThreshold = 30;

    /** 交易完成奖励分 */
    private int rewardTransaction = 2;

    /** 好评奖励分 */
    private int rewardPositive = 1;

    /** 差评扣分 */
    private int deductNegative = -5;

    // ---- getters / setters ----

    public int getMax() { return max; }
    public void setMax(int max) { this.max = max; }

    public int getPublishThreshold() { return publishThreshold; }
    public void setPublishThreshold(int publishThreshold) { this.publishThreshold = publishThreshold; }

    public int getTradeThreshold() { return tradeThreshold; }
    public void setTradeThreshold(int tradeThreshold) { this.tradeThreshold = tradeThreshold; }

    public int getRewardTransaction() { return rewardTransaction; }
    public void setRewardTransaction(int rewardTransaction) { this.rewardTransaction = rewardTransaction; }

    public int getRewardPositive() { return rewardPositive; }
    public void setRewardPositive(int rewardPositive) { this.rewardPositive = rewardPositive; }

    public int getDeductNegative() { return deductNegative; }
    public void setDeductNegative(int deductNegative) { this.deductNegative = deductNegative; }
}
