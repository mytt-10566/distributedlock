package com.momo.distributed.zookeeper.curator;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.GetChildrenBuilder;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryNTimes;

import java.util.Arrays;
import java.util.List;

/**
 * @author: MQG
 * @date: 2018/12/17
 */
public class Test {

    private static final String url = "dev.tss.com:2181";
    private static int count = 10;

    public static void main(String[] args) throws Exception {
        test1();
    }
    
    public static void test0() {
        // 创建zookeeper的客户端
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        CuratorFramework client = CuratorFrameworkFactory.newClient(url, retryPolicy);
        client.start();

        // 创建分布式锁, 锁空间的根节点路径为/curator/lock
        InterProcessMutex mutex = new InterProcessMutex(client, "/curator/lock");
        
        try {
            mutex.acquire();
            // 获得了锁, 进行业务流程
            System.out.println("Enter mutex");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                // 完成业务流程, 释放锁
                mutex.release();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // 关闭客户端
                client.close();
            }
        }
    }

    public static void test1() {
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                CuratorFramework zk = CuratorFrameworkFactory.builder()
                        .sessionTimeoutMs(5000)
                        .retryPolicy(new RetryNTimes(3, 1000))
                        .connectionTimeoutMs(50000)
                        .connectString(url)
                        .build();
                zk.start();

                // 分布式锁
                InterProcessMutex lock = new InterProcessMutex(zk, "/root-curator-lock");
                try {
                    // 获取锁，永不超时
                    lock.acquire();
                    
                    zkInfo(zk);
                    
                    doService();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        // 释放锁
                        lock.release();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        zk.close();
                    }
                }
            }).start();
        }
    }

    public static void doService() {
        count--;
        System.out.println(count);
    }

    public static void zkInfo(CuratorFramework zk) {
        try {
            GetChildrenBuilder builder = zk.getChildren();
            List<String> children = builder.forPath("/root-curator-lock");
            System.out.println(children);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
}
