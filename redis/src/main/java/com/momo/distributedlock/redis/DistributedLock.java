package com.momo.distributedlock.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DistributedLock {

    private static final String LOCK_SUCCESS = "OK";
    private static final String SET_IF_NOT_EXIST = "NX";
    private static final String SET_WITH_EXPIRE_TIME = "PX";
    private static final Long RELEASE_SUCCESS = 1L;

    /*
        实现思想：
        获取锁的时候，使用setnx加锁，并使用expire命令为锁添加一个超时时间，超过该时间则自动释放锁，锁的value值为一个随机生成的UUID，通过此在释放锁的时候进行判断。
        获取锁的时候还设置一个获取的超时时间，若超过这个时间则放弃获取锁。
        释放锁的时候，通过UUID判断是不是该锁，若是该锁，则执行delete进行锁释放。
    * */

    /**
     * 加锁
     *
     * @param lockKey       锁的key
     * @param acquireTimeout 获取超时时间，毫秒
     * @param expireTime     锁的过期时间，毫秒
     * @return 锁标识
     */
    public String tryGetDistributedLock(Jedis jedis, String lockKey, long acquireTimeout, long expireTime) {
        try {
            // 随机生成一个value
            String identifier = UUID.randomUUID().toString();

            // 获取锁的超时时间，超过这个时间则放弃获取锁
            long end = System.currentTimeMillis() + acquireTimeout;
            while (System.currentTimeMillis() < end) {
                // 锁不存在，保存成功，设置过期时间，返回OK
                // 锁存在，不执行任何操作
                String result = jedis.set(lockKey, identifier, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, expireTime);
                if (LOCK_SUCCESS.equals(result)) {
                    return identifier;
                } else {
                    try {
                        Thread.sleep(1000);
                        System.out.println(Thread.currentThread().getName() + "循环");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (JedisException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 释放锁
     *
     * @param lockKey   锁的key
     * @param identifier 释放锁的标识
     * @return
     */
    public boolean releaseLock(Jedis jedis, String lockKey, String identifier) {
        boolean retFlag = false;
        try {
            while (true) {
                // 监视lock，准备开始事务
                jedis.watch(lockKey);
                // 通过前面返回的value值判断是不是该锁，若是该锁，则删除，释放锁
                if (identifier.equals(jedis.get(lockKey))) {
                    Transaction transaction = jedis.multi();
                    transaction.del(lockKey);
                    List<Object> results = transaction.exec();
                    if (results == null) {
                        continue;
                    }
                    retFlag = true;
                }
                jedis.unwatch();
                break;
            }
        } catch (JedisException e) {
            e.printStackTrace();
        }
        return retFlag;
    }

    public boolean releaseDistributedLock(Jedis jedis, String lockKey, String identifier) {
        try {
            // 首先获取锁对应的value值，检查是否与identifier相等，如果相等则删除锁（解锁）
            // 使用Lua语言来实现，
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            Object result = jedis.eval(script, Collections.singletonList(lockKey), Collections.singletonList(identifier));
            return RELEASE_SUCCESS.equals(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
