package cn.lunarlanding.qualia.code.desktop;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 应用主窗口：承载内嵌浏览器，负责窗口尺寸位置记忆与关闭生命周期。
 *
 * <p>窗口状态（尺寸、位置、是否最大化）持久化到 {@code ~/.qualia/code/desktop.json}，
 * 下次启动还原；跨平台一致。
 */
final class MainWindow {

    /** 窗口状态文件（产品级，与其他产品隔离） */
    private static final Path STATE_FILE =
            Path.of(System.getProperty("user.home"), ".qualia", "code", "desktop.json");

    /** 旧版状态文件（产品目录隔离改造前的路径，首启一次性迁移） */
    private static final Path LEGACY_STATE_FILE =
            Path.of(System.getProperty("user.home"), ".qualia", "desktop.json");

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 800;

    private final Display display;
    private final Shell shell;

    MainWindow(Display display, String baseUrl) {
        this.display = display;
        this.shell = new Shell(display);
        // 不显示系统标题栏的 logo 与标题文字，保留纯净的着色标题栏
        shell.setText("");
        shell.setLayout(new FillLayout());
        migrateLegacyState();
        restoreState();

        // 系统标题栏默认深色（与应用默认深色主题一致），随后由网页主题回调实时校正
        TitleBarTheme.apply(shell, true);
        BrowserPanel.create(shell, baseUrl, isLight ->
                // BrowserFunction 回调运行在 UI 线程，可直接操作 Shell
                TitleBarTheme.apply(shell, !isLight));

        // 关闭前保存窗口状态
        shell.addDisposeListener(e -> saveState());
    }

    /**
     * 打开窗口并运行 SWT 事件循环，直到窗口关闭。
     */
    void openAndLoop() {
        shell.open();
        // 窗口已创建，移除标题栏左上角图标（含系统默认图标）
        TitleBarTheme.hideTitleBarIcon(shell);
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    /**
     * 旧版状态文件迁移：新路径不存在且旧文件存在时复制过来（幂等，旧文件保留）
     */
    private static void migrateLegacyState() {
        try {
            if (!Files.exists(STATE_FILE) && Files.exists(LEGACY_STATE_FILE)) {
                Files.createDirectories(STATE_FILE.getParent());
                Files.copy(LEGACY_STATE_FILE, STATE_FILE);
            }
        } catch (Exception e) {
            // 迁移失败不影响启动，退回默认窗口尺寸即可
        }
    }

    /**
     * 还原上次窗口状态；无记录时居中显示默认尺寸。
     */
    private void restoreState() {
        JSONObject state = readState();
        if (state != null && state.containsKey("width") && state.containsKey("height")) {
            int width = state.getIntValue("width");
            int height = state.getIntValue("height");
            shell.setSize(Math.max(width, 640), Math.max(height, 480));
            if (state.containsKey("x") && state.containsKey("y")) {
                shell.setLocation(state.getIntValue("x"), state.getIntValue("y"));
            } else {
                centerOnScreen();
            }
            if (state.getBooleanValue("maximized")) {
                shell.setMaximized(true);
            }
        } else {
            shell.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            centerOnScreen();
        }
    }

    private void centerOnScreen() {
        Rectangle screen = display.getPrimaryMonitor().getClientArea();
        Rectangle win = shell.getBounds();
        shell.setLocation(
                screen.x + (screen.width - win.width) / 2,
                screen.y + (screen.height - win.height) / 2);
    }

    /**
     * 保存窗口状态（最大化时仅记录标记，尺寸沿用上次非最大化值以获得更自然的还原体验）。
     */
    private void saveState() {
        try {
            JSONObject state = new JSONObject();
            boolean maximized = shell.getMaximized();
            state.put("maximized", maximized);
            if (!maximized) {
                Rectangle bounds = shell.getBounds();
                state.put("x", bounds.x);
                state.put("y", bounds.y);
                state.put("width", bounds.width);
                state.put("height", bounds.height);
            } else {
                // 保留上次的非最大化尺寸，避免下次还原成最大化时丢失窗口大小
                JSONObject prev = readState();
                if (prev != null) {
                    for (String key : new String[]{"x", "y", "width", "height"}) {
                        if (prev.containsKey(key)) {
                            state.put(key, prev.getIntValue(key));
                        }
                    }
                }
            }
            Files.createDirectories(STATE_FILE.getParent());
            Files.writeString(STATE_FILE, JSON.toJSONString(state, true), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 状态保存失败不影响退出
        }
    }

    private JSONObject readState() {
        try {
            if (Files.exists(STATE_FILE)) {
                return JSON.parseObject(Files.readString(STATE_FILE, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            // 忽略损坏的状态文件
        }
        return null;
    }
}
