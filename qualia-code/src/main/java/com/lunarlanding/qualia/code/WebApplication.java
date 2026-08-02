package com.lunarlanding.qualia.code;

import com.lunarlanding.qualia.code.service.WorkspaceHistory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot Web 应用
 */
@SpringBootApplication(scanBasePackages = "com.lunarlanding.qualia.code.web")
public class WebApplication {

    private static ConfigurableApplicationContext context;
    private static Path currentWorkspace;

    /**
     * 启动 Web 应用（阻塞，CLI/web 子命令使用：主线程保活直到 Ctrl+C）
     *
     * @param workspacePath 工作区路径
     * @param port          服务端口
     */
    public static void start(Path workspacePath, int port) {
        startAsync(workspacePath, port);

        // 打印启动信息
        System.out.println();
        System.out.println("Qualia Code Web 服务已启动");
        System.out.println("访问地址: http://localhost:" + port);
        System.out.println("按 Ctrl+C 停止服务");
        System.out.println();

        // 主线程保持运行
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            // 正常退出
        }
    }

    /**
     * 启动 Web 应用（非阻塞，桌面壳使用：启动后立即返回，调用方通过 isRunning 轮询就绪状态）
     *
     * @param workspacePath 工作区路径
     * @param port          服务端口
     */
    public static void startAsync(Path workspacePath, int port) {
        currentWorkspace = workspacePath;
        WorkspaceHistory.record(workspacePath);

        Map<String, Object> props = new HashMap<>();
        props.put("server.port", port);
        props.put("spring.web.resources.static-locations", "classpath:/static/");
        // 静态资源声明 no-cache：浏览器每次协商校验（304），文件更新后立即生效，无需手动维护 ?v= 版本号
        props.put("spring.web.resources.cache.cachecontrol.no-cache", "true");

        // 在新线程中启动 Spring Boot
        Thread springThread = new Thread(() -> {
            SpringApplicationBuilder builder = new SpringApplicationBuilder(WebApplication.class).properties(props).headless(false);
            context = builder.run();
        });

        springThread.setDaemon(false);
        springThread.start();
    }

    /**
     * 停止 Web 应用
     */
    public static void stop() {
        if (context != null) {
            SpringApplication.exit(context, () -> 0);
            context = null;
        }
    }

    /**
     * 获取当前工作区路径
     */
    public static Path getCurrentWorkspace() {
        return currentWorkspace;
    }

    /**
     * 运行期切换当前工作区（仅更新路径引用，服务重建由 ChatService.switchWorkspace 负责）
     */
    public static void setCurrentWorkspace(Path workspacePath) {
        currentWorkspace = workspacePath;
    }

    /**
     * 获取是否正在运行
     */
    public static boolean isRunning() {
        return context != null && context.isRunning();
    }
}
