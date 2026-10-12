package cn.lunarlanding.qualia.code;

import cn.lunarlanding.qualia.code.service.ChatService;
import cn.lunarlanding.qualia.code.service.WorkspaceHistory;
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
@SpringBootApplication(scanBasePackages = {"cn.lunarlanding.qualia.code.web", "cn.lunarlanding.qualia.code.service"})
public class WebApplication {

    private static ConfigurableApplicationContext context;

    /** 启动参数工作区（仅作首个会话激活前的镜像兜底；会话激活后镜像跟随活跃会话绑定） */
    private static Path startupWorkspace;

    /**
     * 启动 Web 应用（阻塞，CLI/web 子命令使用：主线程保活直到 Ctrl+C）
     *
     * @param workspacePath 工作区路径，null 表示启动时未指定（由前端强制选择）
     * @param port          服务端口
     */
    public static void start(Path workspacePath, int port) {
        startAsync(workspacePath, port);

        // 打印启动信息
        System.out.println();
        System.out.println("Qualia Code Web 服务已启动");
        System.out.println("访问地址: http://localhost:" + port + (workspacePath == null ? "（未指定工作区，请在页面中选择）" : ""));
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
     * @param workspacePath 工作区路径，null 表示启动时未指定，前端检测到后强制弹出选择弹窗
     * @param port          服务端口
     */
    public static void startAsync(Path workspacePath, int port) {
        startupWorkspace = workspacePath;
        if (workspacePath != null) {
            WorkspaceHistory.record(workspacePath);
        }

        Map<String, Object> props = new HashMap<>();
        props.put("server.port", port);
        props.put("spring.web.resources.static-locations", "classpath:/static/");
        // 静态资源声明 no-cache：浏览器每次协商校验（304），文件更新后立即生效，无需手动维护 ?v= 版本号
        props.put("spring.web.resources.cache.cachecontrol.no-cache", "true");
        // 附件上传上限：默认仅 1MB，需覆盖业务上限（图片 5MB / 文档 20MB）；单请求单文件，请求总量留表单字段余量
        props.put("spring.servlet.multipart.max-file-size", "20MB");
        props.put("spring.servlet.multipart.max-request-size", "25MB");
        // 请求行+头总限额：Tomcat 默认 8KB，长消息/大 Cookie 会触发 431；前端长消息已降级 POST，此为兜底
        props.put("server.max-http-request-header-size", "64KB");

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
     * 获取当前工作区路径（镜像语义）：优先返回 ChatService 活跃实例的 Agent 根目录
     * （跟随活跃会话绑定）；无活跃实例时回落启动参数工作区，均无则 null。
     * 终端 cwd / 附件旧图回显 / 配置读取等调用点零改动，自动跟随会话激活。
     */
    public static Path getCurrentWorkspace() {
        Path active = ChatService.activeRoot();
        return active != null ? active : startupWorkspace;
    }

    /**
     * 获取是否正在运行
     */
    public static boolean isRunning() {
        return context != null && context.isRunning();
    }
}
