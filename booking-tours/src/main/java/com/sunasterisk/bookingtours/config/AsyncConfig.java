package com.sunasterisk.bookingtours.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Cấu hình thread pool cho xử lý bất đồng bộ.
 * notificationExecutor: dùng cho save/push notification, không block HTTP response.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread pool cho notification async — dùng bởi {@code @Async("notificationExecutor")}.
     * Tách riêng khỏi common pool để notification spike không ảnh hưởng request chính.
     */
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);       // 3 thread luôn sẵn sàng cho tải ổn định
        executor.setMaxPoolSize(5);        // tối đa 5; notification I/O-light nên 5 là đủ
        executor.setQueueCapacity(100);    // buffer burst ngắn, tránh reject task
        executor.setThreadNamePrefix("notif-async-"); // xuất hiện trong log để trace dễ
        executor.setWaitForTasksToCompleteOnShutdown(true); // chờ các task đang chạy hoàn thành trước khi đóng thread pool
        executor.setAwaitTerminationSeconds(10);            // sau 10 giây, dù còn task chưa xong, executor vẫn force-shutdown
        executor.initialize();
        return executor;
    }
}
