package cn.lunarlanding.qualia.code.desktop;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.eclipse.swt.widgets.Shell;

import java.lang.reflect.Field;

/**
 * 让系统标题栏与窗口边框跟随应用日/夜主题（深色/浅色）。
 *
 * <p>Windows 10/11 通过 DWM 的 {@code DWMWA_USE_IMMERSIVE_DARK_MODE} 属性切换标题栏明暗；
 * 其他平台（如 macOS 由系统外观自动接管）为无操作，不影响运行。
 *
 * <p>为保证在 macOS 上也能通过编译，这里不直接引用 win32 专用的 {@code Shell.handle} 字段，
 * 而是运行期反射取 HWND，且仅在 Windows 上执行；任何异常都被吞掉，绝不影响主流程。
 */
final class TitleBarTheme {

    /** Win10 20H1(build 19041)+ 使用的深色标题栏属性号 */
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    /** Win10 早期版本(1809~1909)使用的旧属性号，作兜底一并尝试 */
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19;

    // 以下三项需 Win11(build 22000+)；旧系统调用会返回非 0 但无副作用，自动回落到黑/白模式
    /** 窗口边框颜色 */
    private static final int DWMWA_BORDER_COLOR = 34;
    /** 标题栏背景颜色 */
    private static final int DWMWA_CAPTION_COLOR = 35;
    /** 标题栏文字颜色 */
    private static final int DWMWA_TEXT_COLOR = 36;

    // 与应用顶栏(.topbar 取 --bg-sidebar)贴合的配色，0xRRGGBB
    private static final int DARK_CAPTION = 0x0D0F15;
    private static final int DARK_TEXT = 0xE4E9F2;
    private static final int DARK_BORDER = 0x1B2130;
    private static final int LIGHT_CAPTION = 0xFFFFFF;
    private static final int LIGHT_TEXT = 0x1E232E;
    private static final int LIGHT_BORDER = 0xE2E6EC;

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    // —— 隐藏标题栏图标所需的 Win32 常量 ——
    /** WM_SETICON 消息 */
    private static final int WM_SETICON = 0x0080;
    private static final int ICON_SMALL = 0;
    private static final int ICON_BIG = 1;
    // SetWindowPos 标志：保持位置/尺寸/层级不变，仅重算窗口框架
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_FRAMECHANGED = 0x0020;

    // 16x16 全透明单色图标的位掩码：AND 全 1 + XOR 全 0 = 每个像素透明（各 32 字节）
    private static final byte[] ICON_AND_BITS = new byte[32];
    private static final byte[] ICON_XOR_BITS = new byte[32];

    static {
        java.util.Arrays.fill(ICON_AND_BITS, (byte) 0xFF);
    }

    /** dwmapi.dll 的最小 JNA 映射（仅声明用到的一个函数） */
    private interface Dwmapi extends Library {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class);

        int DwmSetWindowAttribute(Pointer hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);
    }

    /** user32.dll 的最小 JNA 映射（仅声明隐藏图标用到的函数） */
    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);

        Pointer CreateIcon(Pointer hInstance, int nWidth, int nHeight,
                           byte cPlanes, byte cBitsPixel, byte[] lpbANDbits, byte[] lpbXORbits);

        long SendMessageW(Pointer hWnd, int msg, long wParam, long lParam);

        boolean SetWindowPos(Pointer hWnd, Pointer hWndInsertAfter, int x, int y, int cx, int cy, int uFlags);
    }

    private TitleBarTheme() {
    }

    /**
     * 将窗口标题栏/边框设置为深色或浅色。仅 Windows 生效，失败静默。
     *
     * @param shell 目标窗口
     * @param dark  true=深色标题栏，false=浅色标题栏
     */
    static void apply(Shell shell, boolean dark) {
        if (!IS_WINDOWS || shell == null || shell.isDisposed()) {
            return;
        }
        try {
            long hwnd = nativeHandle(shell);
            if (hwnd == 0L) {
                return;
            }
            Pointer hwndPtr = new Pointer(hwnd);
            // 1) 明暗模式标记：Win10 兜底 + Win11 系统菜单等随之切换；老属性号一并兜底
            int rc = setAttr(hwndPtr, DWMWA_USE_IMMERSIVE_DARK_MODE, dark ? 1 : 0);
            if (rc != 0) {
                setAttr(hwndPtr, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, dark ? 1 : 0);
            }
            // 2) Win11：显式指定标题栏/文字/边框颜色，贴合应用顶栏配色（旧系统返回非 0 无副作用）
            if (dark) {
                setAttr(hwndPtr, DWMWA_CAPTION_COLOR, toColorRef(DARK_CAPTION));
                setAttr(hwndPtr, DWMWA_TEXT_COLOR, toColorRef(DARK_TEXT));
                setAttr(hwndPtr, DWMWA_BORDER_COLOR, toColorRef(DARK_BORDER));
            } else {
                setAttr(hwndPtr, DWMWA_CAPTION_COLOR, toColorRef(LIGHT_CAPTION));
                setAttr(hwndPtr, DWMWA_TEXT_COLOR, toColorRef(LIGHT_TEXT));
                setAttr(hwndPtr, DWMWA_BORDER_COLOR, toColorRef(LIGHT_BORDER));
            }
            // DWM 属性改变后，标题栏需一次非客户区重绘才会刷新，用极小的尺寸抖动触发
            nudgeRepaint(shell);
        } catch (Throwable ignored) {
            // 主题着色是增强特性，任何失败都不应影响应用运行
        }
    }

    /**
     * 移除系统标题栏左上角的窗口图标。仅 Windows 生效，失败静默。
     *
     * <p>做法：创建一个 16×16 全透明单色图标，用 {@code WM_SETICON} 覆盖窗口的大/小图标
     * （从而盖掉系统默认图标与 exe 图标），最后触发一次框架重算立即生效。
     * 相比 {@code WS_EX_DLGMODALFRAME} 技巧，该方式在 Win10/11 各版本上表现一致。
     *
     * @param shell 目标窗口
     */
    static void hideTitleBarIcon(Shell shell) {
        if (!IS_WINDOWS || shell == null || shell.isDisposed()) {
            return;
        }
        try {
            long hwnd = nativeHandle(shell);
            if (hwnd == 0L) {
                return;
            }
            Pointer hwndPtr = new Pointer(hwnd);
            Pointer blankIcon = User32.INSTANCE.CreateIcon(
                    null, 16, 16, (byte) 1, (byte) 1, ICON_AND_BITS, ICON_XOR_BITS);
            if (blankIcon == null) {
                return;
            }
            long iconValue = Pointer.nativeValue(blankIcon);
            User32.INSTANCE.SendMessageW(hwndPtr, WM_SETICON, ICON_SMALL, iconValue);
            User32.INSTANCE.SendMessageW(hwndPtr, WM_SETICON, ICON_BIG, iconValue);
            User32.INSTANCE.SetWindowPos(hwndPtr, null, 0, 0, 0, 0,
                    SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED);
        } catch (Throwable ignored) {
            // 隐藏图标是增强特性，任何失败都不应影响应用运行
        }
    }

    /**
     * 设置一个 DWM 窗口属性（值为 4 字节整型）。
     *
     * @return DwmSetWindowAttribute 的返回码，0 表示成功
     */
    private static int setAttr(Pointer hwnd, int attribute, int value) {
        return Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, new IntByReference(value), 4);
    }

    /**
     * 将 0xRRGGBB 转为 Win32 COLORREF（0x00BBGGRR，R 在低字节）。
     */
    private static int toColorRef(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (b << 16) | (g << 8) | r;
    }

    /**
     * 反射取 SWT 控件的原生句柄（win32 端为 {@code long handle}）。
     */
    private static long nativeHandle(Shell shell) {
        try {
            Field field = shell.getClass().getField("handle");
            Object v = field.get(shell);
            if (v instanceof Long) {
                return (Long) v;
            }
        } catch (Throwable ignored) {
            // 非 win32 平台或字段不可用
        }
        return 0L;
    }

    /**
     * 触发标题栏重绘：临时缩放 1px 再还原，代价极小且用户无感。
     */
    private static void nudgeRepaint(Shell shell) {
        if (shell.getMaximized()) {
            // 最大化窗口不便抖动尺寸，改用 redraw
            shell.redraw();
            return;
        }
        org.eclipse.swt.graphics.Point size = shell.getSize();
        shell.setSize(size.x, size.y + 1);
        shell.setSize(size.x, size.y);
    }
}
