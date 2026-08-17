# 智能体扩展机制设计

## 设计思路

现有架构中，用户可通过 MCP 服务器接入远程工具、通过技能目录添加提示词模板和脚本。但缺少一种**原生 Java 扩展机制**，让用户以 JAR 插件形式编写自定义 `FunctionTool`，直接访问 Java 生态和 qualia-core 的完整工具抽象。

**不新增模块**，直接在 `qualia-core` 的 `core.tool.extension` 包内实现。与现有的 `addTool`、`addMcpClient`、`addKnowbase`、`addSubAgent` 完全对齐——给 `ReActAgent` 再加一个 `addExtension(Path jarPath)` 方法。扩展 SPI 与 `FunctionTool`、`McpToolAdapter` 等同处 tool 包下，扩展本身就是工具的另一种变体。

## 新增文件

```
qualia-core/src/main/java/com/lunarlanding/qualia/core/tool/extension/
├── Extension.java            # SPI 入口接口（扩展作者实现）
└── ExtensionManager.java     # JAR 加载器（内部使用）
```

## 核心接口设计

### Extension 接口（SPI 入口）

位于 `com.lunarlanding.qualia.core.tool.extension`，扩展作者实现此接口：

```java
/**
 * 扩展 API 版本。仅当 Extension 接口发生不兼容变更时才递增。
 * 框架加载时校验此值，不兼容则跳过并记录警告日志。
 */
int API_VERSION = 1;

public interface Extension {
    /** 扩展名称（日志标识） */
    String name();

    /** 扩展版本 */
    default String version() { return "1.0.0"; }

    /**
     * 返回此扩展提供的工具列表。
     * 扩展作者继承 FunctionTool 创建工具类，显式定义 name、description、parameters 和 execute。
     */
    default List<FunctionTool> getTools() {
        return List.of();
    }
}
```

单一方法，扩展作者直接继承 `FunctionTool` 创建工具，无注解扫描的隐式行为，契约清晰。

### 版本兼容性

扩展接口放在 core 包内，扩展 JAR 编译时依赖某个版本的 qualia-core，宿主产品运行时使用另一个版本。这种情况**不会产生冲突**，原因：

- 扩展 JAR 通过 `URLClassLoader` 加载，parent classloader 是宿主应用的 classloader
- Java 类加载委托机制**先问 parent**，因此 `FunctionTool`、`@AsFunctionTool`、`Extension` 等类**始终由宿主 qualia-core 提供**
- 即使扩展 JAR 内打包了 qualia-core 的类（fat JAR），也会被 parent 的类覆盖，不会加载

唯一风险：宿主升级后对 `Extension` 接口做了不兼容变更（改签名、删方法）。应对方式：
- `Extension` 接口定义 `API_VERSION` 常量，仅在不兼容变更时递增
- `ExtensionManager` 加载时检查 `API_VERSION`，不兼容则跳过并打印警告
- 默认方法（`default method`）的新增不影响二进制兼容性，属于兼容变更

### ExtensionManager（JAR 加载器）

内部工具类，负责单个 JAR 的加载：

```java
public class ExtensionManager {

    /**
     * 从指定 JAR 文件加载 Extension 实现。
     * 使用 URLClassLoader + ServiceLoader 发现并实例化。
     * parent classloader 为 ExtensionManager 自身的 classloader，
     * 保证扩展能看到 qualia-core 的 FunctionTool、@AsFunctionTool 等类。
     */
    public static List<Extension> loadFromJar(Path jarPath);
}
```

加载流程：
1. 创建 `URLClassLoader(jarUrl, ExtensionManager.class.getClassLoader())`
2. 使用 `ServiceLoader.load(Extension.class, classLoader)` 发现实现
3. 返回所有发现的 Extension 实例（单个 JAR 可包含多个实现）
4. 加载失败返回空列表并记录日志

## ReActAgent 集成

在 `ReActAgent` 中新增 `addExtension` 方法，与现有 `addMcpClient`、`addKnowbase`、`addSubAgent` 对齐：

```java
/**
 * 从 JAR 文件加载扩展并注册其工具。
 *
 * @param jarPath 扩展 JAR 文件路径
 */
public void addExtension(Path jarPath) {
    List<Extension> extensions = ExtensionManager.loadFromJar(jarPath);
    for (Extension ext : extensions) {
        for (FunctionTool tool : ext.getTools()) {
            addTool(tool);
        }
        logger.info("扩展 [{}] v{} 已加载", ext.name(), ext.version());
    }
}
```

## 产品集成

产品层负责确定扩展目录路径并扫描，agent 层负责单个 JAR 的加载注册。**扩展目录路径由产品定义**，不硬编码在 core 中。

### qualia-code: ChatService.initialize()

在 `connectMcpServers()` 之后追加：

```java
// 加载用户扩展（code 产品的扩展目录：~/.qualia/extensions）
loadExtensions(Path.of(System.getProperty("user.home"), ".qualia", "extensions"));
```

新增私有方法，**接收目录路径参数**：
```java
/**
 * 扫描指定目录下所有 .jar 扩展并注册到 agent
 */
private void loadExtensions(Path extDir) {
    if (!Files.exists(extDir)) return;
    try (var jars = Files.list(extDir)) {
        jars.filter(p -> p.toString().endsWith(".jar"))
            .forEach(jar -> {
                try {
                    agent.addExtension(jar);
                } catch (Exception e) {
                    logger.warn("扩展加载失败 [{}]: {}", jar.getFileName(), e.getMessage());
                }
            });
    } catch (IOException e) {
        logger.warn("扫描扩展目录失败: {}", e.getMessage());
    }
}
```

对应文件: `qualia-code/.../service/ChatService.java`

### qualia-claw: ClawAgentService.initialize()

claw 是多智能体模式，扩展加载需要两层过滤：

1. **全局层**：`ClawConfig.disabledExtensions` 控制全局启用/禁用
2. **智能体层**：`ClawAgentDefinition.extensions` 控制该智能体引用哪些已启用的扩展（null = 引用全部，空列表 = 不引用），与 `skills`、`mcpServers` 白名单语义对齐

在 `connectMcpServers()` 之后、`applyRolePrompt()` 之前追加：

```java
// 加载用户扩展（claw 产品的扩展目录：~/.qualia/extensions）
loadExtensions(Path.of(System.getProperty("user.home"), ".qualia", "extensions"));
```

新增私有方法，**接收目录路径参数，按智能体级白名单过滤**：
```java
/**
 * 扫描指定目录下所有 .jar 扩展，过滤全局禁用列表与智能体级白名单后注册到 agent
 */
private void loadExtensions(Path extDir) {
    if (!Files.exists(extDir)) return;

    Set<String> disabledExtensions = new HashSet<>(config.getDisabledExtensions());
    // 智能体级白名单：null = 引用全部（存量智能体），否则仅加载名单内的扩展
    List<String> refs = definition.getExtensions();
    Set<String> allowedExtensions = refs != null ? new HashSet<>(refs) : null;

    try (var jars = Files.list(extDir)) {
        jars.filter(p -> p.toString().endsWith(".jar"))
            .forEach(jar -> {
                try {
                    // 先加载获取扩展元信息
                    List<Extension> exts = ExtensionManager.loadFromJar(jar);
                    for (Extension ext : exts) {
                        if (disabledExtensions.contains(ext.name())) {
                            logger.info("扩展 [{}] 已全局禁用，跳过", ext.name());
                            continue;
                        }
                        if (allowedExtensions != null && !allowedExtensions.contains(ext.name())) {
                            logger.info("扩展 [{}] 未被智能体 [{}] 引用，跳过", ext.name(), definition.getName());
                            continue;
                        }
                        for (FunctionTool tool : ext.getTools()) {
                            agent.addTool(tool);
                        }
                        logger.info("扩展 [{}] v{} 已加载", ext.name(), ext.version());
                    }
                } catch (Exception e) {
                    logger.warn("扩展加载失败 [{}]: {}", jar.getFileName(), e.getMessage());
                }
            });
    } catch (IOException e) {
        logger.warn("扫描扩展目录失败: {}", e.getMessage());
    }
}
```

对应文件: `qualia-claw/.../service/ClawAgentService.java`

`ClawAgentDefinition` 新增 `extensions` 字段：
```java
/** 引用的全局扩展白名单（按名称）；null = 引用全部（存量智能体），空列表 = 不引用 */
private List<String> extensions;
```

对应文件: `qualia-claw/.../ClawAgentDefinition.java`

两个产品的禁用工具逻辑 (`disableTools`) 在扩展加载之前执行，因此扩展注册的工具也会受到 `disabledTools` 配置约束——这是预期行为。

## 插件管理

扩展加载解决了「接入」问题，但用户还需要查看、启用、禁用已安装的扩展。qualia-claw 作为多智能体产品，插件管理分两层：

1. **全局维护层**（插件管理面板）：查看已安装扩展、全局启用/禁用、删除——所有智能体共享
2. **智能体引用层**（智能体编辑面板）：每个智能体选择引用哪些扩展——与技能/MCP 白名单同构

qualia-code 是单智能体产品，只有全局维护层，所有已启用的扩展直接加载。

### 后端 API

两个产品的 ConfigController 各新增两个端点：

```
GET  /api/extensions         — 返回扩展列表（name、version、enabled、toolCount、fileName）
PUT  /api/extensions/toggle  — 启用/禁用指定扩展（body: { name, enabled }）
```

实现逻辑：
- `GET /api/extensions`：扫描扩展目录下所有 .jar，通过 ExtensionManager 加载获取元信息，与配置中的 `disabledExtensions` 列表比对生成启用状态
- `PUT /api/extensions/toggle`：更新配置中的 `disabledExtensions` 列表并触发 reloadConfig，下次初始化时生效

配置文件中新增 `disabledExtensions` 字段（与 `disabledSkills`、`disabledTools` 对齐）：

```json
{
  "defaultModel": "...",
  "disabledExtensions": ["weather", "database"],
  ...
}
```

### qualia-claw：侧边栏插件管理面板（全局维护层）

遵循现有 MCP/技能/模型面板的同构设计：

- **侧边栏**：在 sidebar-footer 中新增「插件管理」菜单按钮（fa-puzzle-piece 图标），调用 `ExtensionPanel.toggle()`
- **视图切换**：点击菜单将整个聊天区替换为扩展卡片网格，与技能/MCP/模型面板互斥
- **新建** `js/extension-panel.js`（自包含模块，与 mcp-panel.js / skill-panel.js 同构）

卡片结构：
- 头部：插件图标 + name + version 徽章
- 内容：提供的工具标签列表
- 底部：删除按钮 + 启用/禁用开关

数据流：`GET /api/extensions` 获取列表 → 渲染卡片 → 切换开关时 `PUT /api/extensions/toggle` 即时保存

### qualia-claw：智能体编辑面板扩展引用（智能体引用层）

在现有智能体编辑弹窗的引用配置区（已有技能 Tab、MCP Tab、工具 Tab），新增「扩展」Tab：

- **agent-panel.js**：在引用配置面板新增扩展卡片网格，与技能/MCP 引用卡片同构
- 勾选 = 该智能体引用此扩展，未勾 = 不引用
- 收集勾选的扩展名称写入 `definition.extensions`，随智能体定义持久化到配置文件
- 数据来源：`GET /api/extensions` 获取已安装且全局启用的扩展列表

### qualia-code：设置对话框新增「插件」Tab

遵循现有 settings.js 的 tab 式弹窗设计：

- **settings.js**：在 models / mcp / skills / tools 之后新增「插件」Tab
- Tab 内容：扩展列表表格（名称、版本、工具数、状态），每行带启用/禁用开关
- 数据流：切换到此 Tab 时 `GET /api/extensions` 加载列表，切换开关后统一随「保存配置」提交

## 扩展开发示例

扩展作者创建一个 Maven 项目，依赖 `qualia-core`：

```java
public class WeatherExtension implements Extension {

    @Override
    public String name() { return "weather"; }

    @Override
    public List<FunctionTool> getTools() {
        return List.of(new GetWeatherTool());
    }
}

// 工具实现：继承 FunctionTool，显式定义元信息和执行逻辑
public class GetWeatherTool extends FunctionTool {

    public GetWeatherTool() {
        super("get_weather", "查询城市天气",
              new Parameter[]{ new Parameter("city", "string", "城市名称", true) });
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String city = (String) arguments.get("city");
        // 调用天气 API 并返回结果
        return "...";
    }
}
```

JAR 的 `META-INF/services/com.lunarlanding.qualia.core.tool.extension.Extension` 文件声明实现类：
```
com.example.WeatherExtension
```

编译后放入 `~/.qualia/extensions/weather.jar`，重启产品即自动加载。

## 文件清单

| 操作 | 文件 |
|------|------|
| 新建 | `qualia-core/.../core/tool/extension/Extension.java` |
| 新建 | `qualia-core/.../core/tool/extension/ExtensionManager.java` |
| 修改 | `ReActAgent.java` -- 新增 `addExtension(Path)` 方法 |
| 修改 | `ChatService.java` -- 新增 `loadExtensions(Path)` 并在 initialize 中调用 |
| 修改 | `ClawAgentService.java` -- 同上 |
| 修改 | `CodeAgentConfig.java` -- 新增 `disabledExtensions` 字段 |
| 修改 | `ClawConfig.java` -- 同上 |
| 修改 | `ClawAgentDefinition.java` -- 新增 `extensions` 白名单字段 |
| 修改 | qualia-code `ConfigController.java` -- 新增 `GET /api/extensions` 和 `PUT /api/extensions/toggle` |
| 修改 | qualia-claw `ConfigController.java` -- 同上 |
| 新建 | qualia-claw `js/extension-panel.js` -- 侧边栏插件管理面板（全局维护层） |
| 修改 | qualia-claw `index.html` -- 侧边栏添加插件管理菜单按钮 |
| 修改 | qualia-claw `js/agent-panel.js` -- 智能体编辑引用配置新增「扩展」Tab |
| 修改 | qualia-code `js/settings.js` -- 新增「插件」Tab |

无需修改根 pom.xml 或产品 pom.xml，无新增模块。

## 验证方式

1. `mvn compile` 全模块编译通过
2. 编写一个测试扩展 JAR，放入 `~/.qualia/extensions/`，验证产品启动后工具列表中出现扩展工具
3. 通过插件管理 UI 全局禁用扩展，重新初始化后确认工具被移除；启用后恢复
4. qualia-claw：智能体编辑面板中勾选/取消扩展引用，确认该智能体加载/不加载对应工具，其他智能体不受影响
