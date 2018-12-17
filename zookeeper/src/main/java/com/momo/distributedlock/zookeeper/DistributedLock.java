package com.momo.distributedlock.zookeeper;


import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 在zookeeper指定节点（locks）下创建临时顺序节点node_n
 * 获取locks下所有子节点children
 * 对子节点按节点自增序号从小到大排序
 * 判断本节点是不是第一个子节点，若是，则获取锁；若不是，则监听比该节点小的那个节点的删除事件
 * 若监听事件生效，则回到第二步重新进行判断，直到获取到锁
 */
public class DistributedLock implements Lock, Watcher {

    private static ZooKeeper zk = null;
    // 根节点
    private String ROOT_LOCK = "/locks";
    // 分隔符
    private String splitStr = "_lock_";
    // 竞争的资源
    private String lockPrefix;
    // 等待的前一个锁
    private String WAIT_LOCK;
    // 当前锁
    private String CURRENT_LOCK;
    // 等待时间，毫秒
    private long waitTime;
    // 计数器
    private CountDownLatch countDownLatch;
    private List<Exception> exceptionList = new ArrayList<>();

    /**
     * 配置分布式锁
     *
     * @param lockPrefix 前缀
     */
    public DistributedLock(String lockPrefix, long waitTime) {
        this.lockPrefix = lockPrefix;
        this.waitTime = waitTime;
    }
    
    public static void createParentNode(String connectString, int sessionTimeout, String rootPath) {
        try {
            // 连接zookeeper
            zk = new ZooKeeper(connectString, sessionTimeout, null);
            Stat stat = zk.exists(rootPath, false);
            if (stat == null) {
                // 如果根节点不存在，则创建根节点
                zk.create(rootPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void lock() {
        if (exceptionList.size() > 0) {
            throw new LockException(exceptionList.get(0));
        }
        try {
            if (this.tryLock()) {
                System.out.println(Thread.currentThread().getName() + " " + lockPrefix + "获得了锁");
                return;
            } else {
                // 等待锁
                waitForLock(WAIT_LOCK, waitTime);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        this.lock();
    }

    @Override
    public boolean tryLock() {
        try {
            
            if (lockPrefix.contains(splitStr)) {
                throw new LockException("锁名有误");
            }
            // 创建临时有序节点
            this.createNode();
            if (CURRENT_LOCK == null || CURRENT_LOCK.length() == 0) {
                return false;
            }
            
            // 取所有子节点
            List<String> subNodes = zk.getChildren(ROOT_LOCK, false);
            // 取出所有lockName的锁
            List<String> lockObjects = new ArrayList<>();
            for (String node : subNodes) {
                String _node = node.split(splitStr)[0];
                if (_node.equals(lockPrefix)) {
                    lockObjects.add(node);
                }
            }
            Collections.sort(lockObjects);
            System.out.println(Thread.currentThread().getName() + " 的锁是 " + CURRENT_LOCK);
            // 若当前节点为最小节点，则获取锁成功
            if (CURRENT_LOCK.equals(ROOT_LOCK + "/" + lockObjects.get(0))) {
                return true;
            }

            // 若不是最小节点，则找到自己的前一个节点
            String prevNode = CURRENT_LOCK.substring(CURRENT_LOCK.lastIndexOf("/") + 1);
            WAIT_LOCK = lockObjects.get(Collections.binarySearch(lockObjects, prevNode) - 1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    private void createNode() {
        try {
            CURRENT_LOCK = zk.create(ROOT_LOCK + "/" + lockPrefix + splitStr, new byte[0],
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            System.out.println(CURRENT_LOCK + " 已经创建");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) throws InterruptedException {
        try {
            if (this.tryLock()) {
                return true;
            }
            return waitForLock(WAIT_LOCK, waitTime);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // 等待锁
    private boolean waitForLock(String prev, long waitTime) throws KeeperException, InterruptedException {
        Stat stat = zk.exists(ROOT_LOCK + "/" + prev, true);

        if (stat != null) {
            System.out.println(Thread.currentThread().getName() + "等待锁 " + ROOT_LOCK + "/" + prev);
            this.countDownLatch = new CountDownLatch(1);
            // 计数等待，若等到前一个节点消失，则precess中进行countDown，停止等待，获取锁
            this.countDownLatch.await(waitTime, TimeUnit.MILLISECONDS);
            this.countDownLatch = null;
            System.out.println(Thread.currentThread().getName() + " 等到了锁");
        }
        return true;
    }

    @Override
    public void unlock() {
        try {
            System.out.println("释放锁 " + CURRENT_LOCK);
            zk.delete(CURRENT_LOCK, -1);
            CURRENT_LOCK = null;
            zk.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Condition newCondition() {
        return null;
    }

    // 节点监视器
    @Override
    public void process(WatchedEvent watchedEvent) {
        if (this.countDownLatch != null) {
            this.countDownLatch.countDown();
        }
    }
}
