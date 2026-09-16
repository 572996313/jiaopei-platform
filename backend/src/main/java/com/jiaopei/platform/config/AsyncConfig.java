package com.jiaopei.platform.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步执行支持。操作留痕经 @Async 异步落库,与业务主事务、接口响应解耦。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 操作留痕专用线程池:核心 2 / 最大 4 / 队列 200,避免占用业务 Tomcat 线程。 */
    @Bean("operationLogExecutor")
    public ThreadPoolTaskExecutor operationLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("oper-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}