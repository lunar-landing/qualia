package cn.lunarlanding.qualia.claw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot Web 应用
 *
 * 与 qualia-code 的差异：无全局当前工作区状态，
 * 工作区归属各智能体定义（见 AgentRegistry）
 */
@SpringBootApplication(scanBasePackages = "cn.lunarlanding.qualia.claw.web")
public class ClawWebApplication {

    private static ConfigurableApplicationContext context;

    /**
     * 启动 Web 应用（阻塞：主线程保活直到 Ctrl+C）
     *
     * @param port 服务端口
     */
    public static void start(int port) {
        startAsync(port);

        // 打印启动信息
        System.out.println();
        System.out.println("Qualia Claw Web 服务已启动");
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
     * 启动 Web 应用（非阻塞，立即返回，调用方通过 isRunning 轮询就绪状态）
     *
     * @param port 服务端口
     */
    public static void startAsync(int port) {
        Map<String, Object> props = new HashMap<>();
        props.put("server.port", port);
        props.put("spring.web.resources.static-locations", "classpath:/static/");
        // 静态资源声明 no-cache：浏览器每次协商校验（304），文件更新后立即生效，无需手动维护 ?v= 版本号
        props.put("spring.web.resources.cache.cachecontrol.no-cache", "true");

        // 在新线程中启动 Spring Boot
        Thread springThread = new Thread(() -> {
            SpringApplicationBuilder builder = new SpringApplicationBuilder(ClawWebApplication.class).properties(props).headless(false);
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
     * 获取是否正在运行
     */
    public static boolean isRunning() {
        return context != null && context.isRunning();
    }
}
