package com.momo.distributedlock.redis;

public class Test {
    public static void main(String[] args) {
        Service service = new Service();
        Runnable runnable = () -> {
            service.doService();
        };


        for (int i = 0; i < 1000; i++) {
            Thread thread = new Thread(runnable);
            thread.start();
        }
    }
}
