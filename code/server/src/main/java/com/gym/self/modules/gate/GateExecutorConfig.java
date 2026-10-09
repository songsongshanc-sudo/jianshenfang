package com.gym.self.modules.gate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class GateExecutorConfig {

    @Bean(name = "gateExecutor")
    public Executor gateExecutor() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(200), runnable -> {
            Thread thread = new Thread(runnable, "gate-sync");
            thread.setDaemon(true);
            return thread;
        });
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return pool;
    }
}
