package com.momo.distributedlock.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class Service {

    private int n = 500;
    private String lockKey = "distributed-lock";
    DistributedLock lock = new DistributedLock();
    private static JedisPool jedisPool = null;

    static {
        JedisPoolConfig config = new JedisPoolConfig();
        // 设置最大连接数
        config.setMaxTotal(200);
        // 设置最大空闲数
        config.setMaxIdle(8);
        // 设置最大等待时间
        config.setMaxWaitMillis(1000 * 100);
        // 在borrow一个jedis实例时，是否需要验证，若为true，则所有jedis实例均是可用的
        config.setTestOnBorrow(true);
        jedisPool = new JedisPool(config, "dev.tss.com", 6379, 3000, "njittss");
    }


    public void doService() {
        Jedis jedis = null;
        try {
            // 获取连接
            jedis = jedisPool.getResource();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }


        if (jedis != null) {
            // 返回锁的value值，供释放锁时候进行判断
            String indentifier = lock.tryGetDistributedLock(jedis, lockKey, 5000, 1000);
            if (indentifier != null) {
                System.out.println(Thread.currentThread().getName() + "获得了锁, indentifier=" + indentifier);
                if (n > 0) {
                    System.out.println(--n);
                } else {
                    System.out.println(Thread.currentThread().getName() + "无资源, n=" + n);
                }
                lock.releaseDistributedLock(jedis, lockKey, indentifier);
            }
        }

        try {
            // 获取连接
            if (jedis != null) {
                jedis.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
