package com.lunarlanding.qualia.claw.desktop;

import com.lunarlanding.qualia.claw.ClawWebApplication;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 桌面应用入口。
 *
 * <p>启动流程：单实例检查 → 非阻塞启动内嵌 Web 服务 → 打开原生窗口加载本地界面
 * → 窗口关闭后停止服务并退出。
 *
 * <p>与 qualia-code-desktop 的差异：claw 无全局工作区概念
 * （工作区归属各智能体定义），启动流程省去工作区确定环节。
 *
 * <p>单实例锁与崩溃日志放在 ~/.qualia/claw/ 产品目录下，
 * 与 qualia-code 隔离，两个产品可同时运行。
 *
 * <p>检测不到系统 WebView（如缺失 WebView2 运行时）时降级为打开系统默认浏览器。
 */
public final class ClawDesktopLauncher {

    /** 产品目录（锁文件与崩溃日志的存放根） */
    private static final Path PRODUCT_DIR =
            Path.of(System.getProperty("user.home"), ".qualia", "claw");

    /** 单实例锁文件 */
    private static final Path LOCK_FILE = PRODUCT_DIR.resolve("desktop.lock");

    /** Web 服务启动就绪等待上限（毫秒） */
    private static final long READY_TIMEOUT_MS = 30_000;

    /** 持有单实例锁的通道；关闭即释放锁 */
    private static FileChannel lockChannel;

    /**
     * 将启动崩溃堆栈写入 ~/.qualia/claw/desktop-error.log（GUI 模式无控制台时的唯一排查依据）。
     */
    private static void writeCrashLog(Throwable t) {
        try {
            Path log = PRODUCT_DIR.resolve("desktop-error.log");
            Files.createDirectories(log.getParent());
            StringWriter sw = new StringWriter();
            sw.write("[" + java.time.Instant.now() + "] Qualia Claw 启动失败\n");
            t.printStackTrace(new PrintWriter(sw));
            sw.write("\n");
            Files.writeString(log, sw.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // 日志写入失败不再处理
        }
    }

    private ClawDesktopLauncher() {
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            // GUI 模式无控制台，异常会被吞掉，落盘到崩溃日志便于排查
            writeCrashLog(t);
            throw t;
        }
    }

    private static void run() {
        // 1. 单实例检查：确有另一实例运行时直接退出
        if (!acquireSingleInstanceLock()) {
            System.err.println("Qualia Claw 已在运行");
            return;
        }

        Display display = new Display();
        try {
            // 2. 选空闲端口并非阻塞启动 Web 服务（智能体工作区各自归属，无需启动前确定）
            int port = pickFreePort();
            ClawWebApplication.startAsync(port);
            String baseUrl = "http://127.0.0.1:" + port;

            if (!waitForReady()) {
                System.err.println("Web 服务启动超时");
                return;
            }

            // 3. 打开原生窗口；WebView 不可用时降级到系统浏览器
            try {
                new MainWindow(display, baseUrl).openAndLoop();
            } catch (SWTError e) {
                System.err.println("内嵌浏览器不可用，改用系统默认浏览器: " + e.getMessage());
                Program.launch(baseUrl);
                keepAliveHeadless();
            }
        } finally {
            // 4. 清理：停止服务、释放显示资源与单实例锁
            ClawWebApplication.stop();
            if (!display.isDisposed()) {
                display.dispose();
            }
            releaseLock();
            System.exit(0);
        }
    }

    /**
     * 尝试获取单实例锁。
     *
     * @return {@code true} 表示成功持锁、或因获锁异常放弃单实例保护但允许继续启动；
     *         {@code false} 表示确有另一实例已持锁，应退出。
     */
    private static boolean acquireSingleInstanceLock() {
        try {
            Files.createDirectories(LOCK_FILE.getParent());
            FileChannel channel = new RandomAccessFile(LOCK_FILE.toFile(), "rw").getChannel();
            FileLock lock = channel.tryLock();
            if (lock == null) {
                // 另一实例已持有锁，确属“已在运行”
                channel.close();
                return false;
            }
            lockChannel = channel;
            return true;
        } catch (Exception e) {
            // 获取锁本身出错（权限/IO 等）不应阻止启动，仅放弃单实例保护
            writeCrashLog(e);
            return true;
        }
    }

    private static void releaseLock() {
        try {
            if (lockChannel != null) {
                // 关闭通道即释放该 JVM 持有的文件锁
                lockChannel.close();
                lockChannel = null;
            }
        } catch (IOException ignored) {
            // 退出阶段忽略
        }
    }

    /**
     * 选取一个空闲端口（不固定 8080，避免与其他服务或多开冲突）。
     */
    private static int pickFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            // 极端情况回退到默认端口
            return 8080;
        }
    }

    /**
     * 轮询等待 Web 服务就绪。
     */
    private static boolean waitForReady() {
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (ClawWebApplication.isRunning()) {
                return true;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 降级到系统浏览器时，主线程保活直到进程被终止。
     */
    private static void keepAliveHeadless() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
