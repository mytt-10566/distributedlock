package com.momo.distributedlock.zookeeper;

public class Test {
    static int n = 500;

    public static void secskill() {
        System.out.println(--n);
    }

    public static void main(String[] args) {
        DistributedLock.createParentNode("dev.tss.com:2181", 30000, "/locks");

        Runnable runnable = () -> {
            DistributedLock lock = null;
            try {
                lock = new DistributedLock("dev.tss.com:2181", 20000);
                lock.lock();
                secskill();
                System.out.println(Thread.currentThread().getName() + "正在运行");
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
}
