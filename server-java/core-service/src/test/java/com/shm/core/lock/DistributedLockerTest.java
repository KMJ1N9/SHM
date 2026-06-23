package com.shm.core.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DistributedLocker 单元测试（Phase 16 16.2.6）
 *
 * <p>Mock RedissonClient + RLock，验证锁获取/释放/异常处理逻辑。
 * <p>可重入/WatchDog/公平锁 FIFO 等 Redisson 内置能力由 Redisson 集成测试覆盖。
 */
@ExtendWith(MockitoExtension.class)
class DistributedLockerTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;

    private DistributedLocker distributedLocker;

    @BeforeEach
    void setUp() {
        distributedLocker = new DistributedLocker(redissonClient);
        lenient().when(rLock.getName()).thenReturn("shm:lock:test:1");
    }

    // ============================================================
    // getLock — 获取锁实例
    // ============================================================

    @Test
    void getLock_shouldReturnLockFromRedisson() {
        when(redissonClient.getLock("shm:lock:order:1:100")).thenReturn(rLock);

        RLock result = distributedLocker.getLock("shm:lock:order:1:100");

        assertSame(rLock, result);
        verify(redissonClient).getLock("shm:lock:order:1:100");
    }

    @Test
    void getFairLock_shouldReturnFairLockFromRedisson() {
        when(redissonClient.getFairLock("shm:lock:product:50")).thenReturn(rLock);

        RLock result = distributedLocker.getFairLock("shm:lock:product:50");

        assertSame(rLock, result);
        verify(redissonClient).getFairLock("shm:lock:product:50");
    }

    // ============================================================
    // tryAcquire — 尝试获取锁
    // ============================================================

    @Test
    void tryAcquire_success_shouldReturnLock() throws InterruptedException {
        when(redissonClient.getLock("shm:lock:test:1")).thenReturn(rLock);
        when(rLock.tryLock(0, 30, TimeUnit.SECONDS)).thenReturn(true);

        RLock result = distributedLocker.tryAcquire("shm:lock:test:1", 30, TimeUnit.SECONDS);

        assertSame(rLock, result);
        verify(rLock).tryLock(0, 30, TimeUnit.SECONDS);
    }

    @Test
    void tryAcquire_failure_shouldReturnNull() throws InterruptedException {
        when(redissonClient.getLock("shm:lock:test:2")).thenReturn(rLock);
        when(rLock.tryLock(0, 30, TimeUnit.SECONDS)).thenReturn(false);

        RLock result = distributedLocker.tryAcquire("shm:lock:test:2", 30, TimeUnit.SECONDS);

        assertNull(result);
    }

    @Test
    void tryAcquire_withWaitTime_shouldPassWaitTimeToRedisson() throws InterruptedException {
        when(redissonClient.getLock("shm:lock:test:3")).thenReturn(rLock);
        when(rLock.tryLock(5, 60, TimeUnit.SECONDS)).thenReturn(true);

        RLock result = distributedLocker.tryAcquire("shm:lock:test:3", 5, 60, TimeUnit.SECONDS);

        assertSame(rLock, result);
        verify(rLock).tryLock(5, 60, TimeUnit.SECONDS);
    }

    @Test
    void tryAcquire_interrupted_shouldReturnNullAndRestoreFlag() throws InterruptedException {
        when(redissonClient.getLock("shm:lock:test:4")).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any())).thenThrow(new InterruptedException());

        RLock result = distributedLocker.tryAcquire("shm:lock:test:4", 30, TimeUnit.SECONDS);

        assertNull(result);
        assertTrue(Thread.currentThread().isInterrupted(),
                "中断标志应在 InterruptedException 后被恢复");
        // 清除中断标志以免影响后续测试
        Thread.interrupted();
    }

    // ============================================================
    // release — 安全释放锁
    // ============================================================

    @Test
    void release_heldByCurrentThread_shouldUnlock() {
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        distributedLocker.release(rLock);

        verify(rLock).unlock();
    }

    @Test
    void release_notHeldByCurrentThread_shouldNotUnlock() {
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        distributedLocker.release(rLock);

        verify(rLock, never()).unlock();
    }

    @Test
    void release_nullLock_shouldNotThrow() {
        assertDoesNotThrow(() -> distributedLocker.release(null));
    }

    @Test
    void release_unlockThrowsException_shouldNotPropagate() {
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(new RuntimeException("Redis 连接断开")).when(rLock).unlock();

        // 不应向外抛出异常（静默降级）
        assertDoesNotThrow(() -> distributedLocker.release(rLock));
    }

    // ============================================================
    // tryAcquire — 边界条件
    // ============================================================

    @Test
    void tryAcquire_zeroLeaseTime_usesWatchDog() throws InterruptedException {
        // leaseTime = -1 时 Redisson 启用 WatchDog 自动续期
        when(redissonClient.getLock("shm:lock:test:wd")).thenReturn(rLock);
        when(rLock.tryLock(0, -1, TimeUnit.SECONDS)).thenReturn(true);

        RLock result = distributedLocker.tryAcquire("shm:lock:test:wd", 0, -1, TimeUnit.SECONDS);

        assertSame(rLock, result);
        verify(rLock).tryLock(0, -1, TimeUnit.SECONDS);
    }
}
