package com.yoruichi.locklock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Created by yoruichi on 17/8/21.
 */
@Service
public class LockService {
    @Autowired
    @Qualifier("lock")
    private RedisTemplate<String, String> redisTemplate;

    private Logger logger = LoggerFactory.getLogger(LockService.class);

    private static final String PREFIX_LOCK_KEY = "LOCK_";

    public void releaseAllLock() {
        Set<String> locks = redisTemplate.keys(PREFIX_LOCK_KEY + "*");
        locks.stream().forEach(k -> redisTemplate.delete(k));
    }

    public boolean getLockIfAbsent(final String key, final String value, long expire,
            TimeUnit timeUnit) {
        boolean succ = redisTemplate.opsForValue().setIfAbsent(PREFIX_LOCK_KEY + key, value);
        if (succ && expire > 0) {
            redisTemplate.expire(PREFIX_LOCK_KEY + key, expire, timeUnit);
        }
        return succ;
    }

    /**
     * Get lock for key.Quick failed.
     *
     * @param key
     * @param value
     * @return
     */
    public boolean getLockIfAbsent(final String key, final String value) {
        return getLockIfAbsent(key, value, -1, null);
    }

    /**
     * Get lock for key with timeout.If set @param timeout -1, means it will be block to wait for getting the lock.
     * And if set @param lockExpireTime -1 means the lock will be never expired.
     *
     * @param key
     * @param value
     * @param waitTimeout
     * @param waitTimeUnit
     * @param lockExpireTime
     * @param lockExpireTimeUnit
     * @return
     */
    public boolean getLockIfAbsent(final String key, final String value, long waitTimeout,
            TimeUnit waitTimeUnit, long lockExpireTime, TimeUnit lockExpireTimeUnit)
            throws TimeoutException, InterruptedException, ExecutionException {
        final ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            Callable<Boolean> call = () -> {
                String realKey = PREFIX_LOCK_KEY + key;
                boolean gotLock;
                while (!(gotLock =
                        redisTemplate.opsForValue().setIfAbsent(realKey, value))) {
                    Thread.sleep(5);
                }
                if (lockExpireTime > 0) {
                    redisTemplate.expire(realKey, lockExpireTime, lockExpireTimeUnit);
                }
                return gotLock;
            };
            Future<Boolean> future = exec.submit(call);
            if (waitTimeout < 0) return future.get();
            else return future.get(waitTimeout, waitTimeUnit);
        } catch (TimeoutException te) {
            logger.warn("Timeout to get lock with key {} and value {}", key, value);
            throw te;
        } catch (InterruptedException e) {
            throw e;
        } catch (ExecutionException e) {
            throw e;
        } finally {
            exec.shutdown();
        }
    }

    public boolean returnLock(final String key, final String value) {
        String realKey = PREFIX_LOCK_KEY + key;
        if (value != null && value.equals(redisTemplate.opsForValue().get(realKey))) {
            redisTemplate.delete(realKey);
            return true;
        }
        return false;
    }
}