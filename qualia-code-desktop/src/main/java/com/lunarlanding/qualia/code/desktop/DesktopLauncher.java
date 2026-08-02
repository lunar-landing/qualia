package com.lunarlanding.qualia.code.desktop;

import com.lunarlanding.qualia.code.WebApplication;
import com.lunarlanding.qualia.code.service.WorkspaceHistory;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

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
import java.util.List;
import java.util.Map;

/**
 * 桌面应用入口。
 *
 * <p>启动流程：单实例检查 → 选空闲端口 → 确定工作区 → 非阻塞启动内嵌 Web 服务
 * → 打开原生窗口加载本地界面 → 窗口关闭后停止服务并退出。
 *
 * <p>检测不到系统 WebView（如缺失 WebView2 运行时）时降级为打开系统默认浏览器。
 */
public final class DesktopLauncher {

    /** 单实例锁文件 */
    private static final Path LOCK_FILE =
            Path.of(System.getProperty("user.home"), ".qualia", "desktop.lock");

    /** Web 服务启动就绪等待上限（毫秒） */
    private static final long READY_TIMEOUT_MS = 30_000;

    /** 持有单实例锁的通道；关闭即释放锁 */
    private static FileChannel lockChannel;

    /**
     * 将启动崩溃堆栈写入 ~/.qualia/desktop-error.log（GUI 模式无控制台时的唯一排查依据）。
     */
    private static void writeCrashLog(Throwable t) {
        try {
            Path log = Path.of(System.getProperty("user.home"), ".qualia", "desktop-error.log");
            Files.createDirectories(log.getParent());
            StringWriter sw = new StringWriter();
            sw.write("[" + java.time.Instant.now() + "] Qualia Code 启动失败\n");
            t.printStackTrace(new PrintWriter(sw));
            sw.write("\n");
            Files.writeString(log, sw.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // 日志写入失败不再处理
        }
    }

    private DesktopLauncher() {
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
            System.err.println("Qualia Code 已在运行");
            return;
        }

        Display display = new Display();
        try {
            // 2. 确定工作区（可能弹出目录选择对话框，需在 Display 就绪后进行）
            Path workspace = resolveWorkspace(display);
            if (workspace == null) {
                // 用户取消选择，退出
                return;
            }

            // 3. 选空闲端口并非阻塞启动 Web 服务
            int port = pickFreePort();
            WebApplication.startAsync(workspace, port);
            String baseUrl = "http://127.0.0.1:" + port;

            if (!waitForReady()) {
                System.err.println("Web 服务启动超时");
                return;
            }

            // 4. 打开原生窗口；WebView 不可用时降级到系统浏览器
            try {
                new MainWindow(display, baseUrl).openAndLoop();
            } catch (SWTError e) {
                System.err.println("内嵌浏览器不可用，改用系统默认浏览器: " + e.getMessage());
                Program.launch(baseUrl);
                keepAliveHeadless();
            }
        } finally {
            // 5. 清理：停止服务、释放显示资源与单实例锁
            WebApplication.stop();
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
     * 确定工作区：优先取最近使用且仍存在的目录，否则弹出目录选择对话框。
     *
     * @return 工作区路径；用户取消选择时返回 null
     */
    private static Path resolveWorkspace(Display display) {
        List<Map<String, Object>> recent = WorkspaceHistory.list();
        for (Map<String, Object> entry : recent) {
            if (Boolean.TRUE.equals(entry.get("exists"))) {
                return Path.of((String) entry.get("path"));
            }
        }
        return chooseDirectory(display);
    }

    /**
     * 弹出目录选择对话框选取工作区。
     */
    private static Path chooseDirectory(Display display) {
        Shell dialogShell = new Shell(display);
        try {
            DirectoryDialog dialog = new DirectoryDialog(dialogShell);
            dialog.setText("选择工作区目录");
            String selected = dialog.open();
            return selected != null ? Path.of(selected) : null;
        } finally {
            dialogShell.dispose();
        }
    }

    /**
     * 轮询等待 Web 服务就绪。
     */
    private static boolean waitForReady() {
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (WebApplication.isRunning()) {
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
