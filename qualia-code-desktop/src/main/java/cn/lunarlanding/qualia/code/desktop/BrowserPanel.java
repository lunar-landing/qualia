package cn.lunarlanding.qualia.code.desktop;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;

import java.util.function.Consumer;

/**
 * 内嵌浏览器封装：隔离浏览器内核选型，便于将来替换（如切换到 JCEF）。
 *
 * <p>Windows 使用 Edge(WebView2) 后端，macOS 使用 WKWebView 后端。
 * 外部链接（非本地服务地址）交由系统默认浏览器打开，避免在应用窗口内跳走。
 *
 * <p>通过 {@link BrowserFunction} 架设 JS→Java 桥，并在每次页面加载完成后注入
 * {@code MutationObserver} 监听网页 {@code body.light-theme} 类的变化，
 * 使外壳（系统标题栏配色）实时跟随网页日/夜主题，且无需改动前端代码。
 */
final class BrowserPanel {

    /** 网页侧回调 Java 的函数名（注入脚本中调用） */
    private static final String THEME_BRIDGE = "__qualiaThemeChanged";

    private BrowserPanel() {
    }

    /**
     * 在父容器中创建内嵌浏览器并加载指定地址。
     *
     * @param parent          父容器
     * @param baseUrl         本地服务基础地址（如 http://127.0.0.1:port）
     * @param onLightThemed   主题变化回调：参数 true=网页当前为浅色主题，false=深色
     * @return 创建的 Browser 控件
     */
    static Browser create(Composite parent, String baseUrl, Consumer<Boolean> onLightThemed) {
        Browser browser = new Browser(parent, resolveStyle());
        browser.addLocationListener(new ExternalLinkHandler(baseUrl));

        // JS→Java 桥：网页主题变化时被调用
        new BrowserFunction(browser, THEME_BRIDGE) {
            @Override
            public Object function(Object[] arguments) {
                boolean isLight = arguments != null && arguments.length > 0
                        && Boolean.TRUE.equals(arguments[0]);
                if (onLightThemed != null) {
                    onLightThemed.accept(isLight);
                }
                return null;
            }
        };

        // 每次页面加载完成后注入观察脚本（SWT 会在导航后自动重注册 BrowserFunction）
        browser.addProgressListener(new ProgressAdapter() {
            @Override
            public void completed(ProgressEvent event) {
                browser.execute(themeObserverScript());
            }
        });

        browser.setUrl(baseUrl);
        return browser;
    }

    /**
     * 注入脚本：立即上报当前主题，并监听 body class 变化持续上报。
     * 判定依据与前端一致：body 含 {@code light-theme} 即浅色。
     */
    private static String themeObserverScript() {
        return "(function(){"
                + "function report(){try{" + THEME_BRIDGE
                + "(document.body.classList.contains('light-theme'));}catch(e){}}"
                + "report();"
                + "if(window.__qualiaThemeObs){window.__qualiaThemeObs.disconnect();}"
                + "window.__qualiaThemeObs=new MutationObserver(report);"
                + "window.__qualiaThemeObs.observe(document.body,{attributes:true,attributeFilter:['class']});"
                + "})();";
    }

    /**
     * 按平台选择浏览器后端：Windows 显式指定 EDGE（否则回落到旧 IE 后端），
     * macOS/其他使用默认（WebKit/WKWebView）。
     */
    private static int resolveStyle() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return SWT.EDGE;
        }
        return SWT.NONE;
    }

    /**
     * 拦截外部链接：仅本地服务地址在窗口内导航，其余用系统浏览器打开。
     */
    private static final class ExternalLinkHandler implements LocationListener {

        private final String baseUrl;

        ExternalLinkHandler(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        public void changing(LocationEvent event) {
            String location = event.location;
            if (location == null || location.startsWith("about:") || isLocal(location)) {
                return;
            }
            // 外部链接：取消窗口内导航，转交系统浏览器
            event.doit = false;
            Program.launch(location);
        }

        @Override
        public void changed(LocationEvent event) {
            // 无需处理
        }

        private boolean isLocal(String location) {
            return location.startsWith(baseUrl)
                    || location.startsWith("http://127.0.0.1")
                    || location.startsWith("http://localhost");
        }
    }
}
