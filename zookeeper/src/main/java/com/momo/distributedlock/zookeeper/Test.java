package com.momo.distributedlock.zookeeper;

public class Test {
    static int n = 500;

    public static void main(String[] args) {
        DistributedLock.createParentNode("dev.tss.com:2181", 50000, "/locks");

        Runnable runnable = () -> {
            if (n <= 0) {
                return;
            }
            
            DistributedLock lock = null;
            try {
                lock = new DistributedLock("dev.tss.com:2181", 50000,"momo", 20000);
                lock.lock();
                doService();
            } finally {
                if (lock != null) {
                    lock.unlock();
                }
            }
        };

        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(runnable);
            t.start();
        }
    }

    public static void doService() {
        System.out.println(Thread.currentThread().getName() + "正在运行");
        if (n > 0) {
            System.out.println(--n);
        }
    }
}
