package com.shm.core.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁工具类（Phase 16 — 替代 {@code SETNX} 基础锁）
 *
 * <h3>与旧方案对比</h3>
 * <table>
 *   <tr><th>能力</th><th>旧 SETNX</th><th>新 Redisson</th></tr>
 *   <tr><td>WatchDog 自动续期</td><td>❌ 锁超时后丢失</td><td>✅ 每 10s 续期</td></tr>
 *   <tr><td>可重入</td><td>❌ 同线程死锁</td><td>✅ 计数重入</td></tr>
 *   <tr><td>公平锁</td><td>❌ 不支持</td><td>✅ FIFO 队列</td></tr>
 *   <tr><td>锁等待超时</td><td>❌ 不支持</td><td>✅ tryLock(waitTime)</td></tr>
 * </table>
 *
 * <h3>使用模式</h3>
 * <pre>{@code
 *   RLock lock = distributedLocker.tryAcquire("lock:order:1:100", 0, 30, TimeUnit.SECONDS);
 *   if (lock == null) {
 *       throw new BusinessException(ErrorCode.RATE_LIMITED);
 *   }
 *   try {
 *       // 临界区代码
 *   } finally {
 *       distributedLocker.release(lock);
 *   }
 * }</pre>
 *
 * <h3>锁监控</h3>
 * <p>每次 acquire 记录耗时，等待超过 5s 输出 WARN 日志，便于排查锁竞争热点。
 */
@Component
public class DistributedLocker {

    private static final Logger log = LoggerFactory.getLogger(DistributedLocker.class);

    /** 锁等待告警阈值（毫秒） */
    private static final long SLOW_LOCK_THRESHOLD_MS = 5000;

    private final RedissonClient redissonClient;

    public DistributedLocker(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    // ============================================================
    // 获取锁
    // ============================================================

    /**
     * 获取普通可重入锁（非公平，Redisson 默认）
     *
     * @param key 锁键（建议使用 {@code shm:lock:<module>:<id>} 命名）
     * @return RLock 实例（无论是否获取成功）
     */
    public RLock getLock(String key) {
        return redissonClient.getLock(key);
    }

    /**
     * 获取公平锁（FIFO 队列，先到先得）
     *
     * <p>适用于多个用户同时抢同一资源的场景（如热门商品抢购）。
     *
     * @param key 锁键
     * @return RLock 公平锁实例
     */
    public RLock getFairLock(String key) {
        return redissonClient.getFairLock(key);
    }

    // ============================================================
    // 尝试获取锁（带监控）
    // ============================================================

    /**
     * 尝试获取锁并返回（成功返回 lock，失败返回 null）
     *
     * <p>内置锁监控：记录等待耗时，超过 5s 输出 WARN 日志。
     *
     * @param key       锁键
     * @param waitTime  最大等待时间（0 = 不等待，立即返回）
     * @param leaseTime 锁持有时间（-1 = WatchDog 自动续期）
     * @param unit      时间单位
     * @return 获取成功返回 RLock，失败返回 null
     */
    public RLock tryAcquire(String key, long waitTime, long leaseTime, TimeUnit unit) {
        RLock lock = redissonClient.getLock(key);
        long start = System.currentTimeMillis();
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
            long elapsed = System.currentTimeMillis() - start;

            if (acquired) {
                if (elapsed > SLOW_LOCK_THRESHOLD_MS) {
                    log.warn("[LockSlow] key={}, waitTime={}ms, threshold={}ms", key, elapsed, SLOW_LOCK_THRESHOLD_MS);
                } else {
                    log.debug("[LockAcquired] key={}, waitTime={}ms", key, elapsed);
                }
                return lock;
            } else {
                log.debug("[LockFailed] key={}, waitTime={}ms", key, elapsed);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LockInterrupted] key={}", key);
            return null;
        }
    }

    /**
     * 尝试获取锁（WatchDog 自动续期，不等待）
     *
     * @param key       锁键
     * @param leaseTime 锁持有时间
     * @param unit      时间单位
     * @return 获取成功返回 RLock，失败返回 null
     */
    public RLock tryAcquire(String key, long leaseTime, TimeUnit unit) {
        return tryAcquire(key, 0, leaseTime, unit);
    }

    // ============================================================
    // 释放锁
    // ============================================================

    /**
     * 安全释放锁
     *
     * <p>仅在当前线程持有时才释放，防止误删其他线程的锁。
     *
     * @param lock 要释放的锁（可为 null，静默忽略）
     */
    public void release(RLock lock) {
        if (lock == null) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[LockReleased] key={}", lock.getName());
            }
        } catch (Exception e) {
            log.warn("[LockReleaseFailed] key={}, error={}", lock.getName(), e.getMessage());
        }
    }
}
