# MCP 服务端

## 概述

MCP 服务端允许您将本地工具暴露为 MCP 服务，供远程 MCP 客户端调用。通过 `@McpTool` 注解，您可以快速将普通 Java 方法转换为 MCP 工具。

### 设计理念

MCP 服务端采用**注解驱动、声明式配置**的设计，开发者只需在方法上添加注解即可注册工具，框架自动处理参数解析、类型转换和结果序列化。

### 核心组件

| 组件 | 说明 |
|------|------|
| `McpServer` | 服务器核心类，管理生命周期和工具注册 |
| `McpServerParameters` | 服务器配置参数（Builder 模式） |
| `McpToolRegistrar` | 工具注册器，扫描注解并转换为 MCP 工具规格 |
| `@McpTool` | 方法注解，标记方法为 MCP 工具 |
| `@McpToolParam` | 参数注解，标记方法参数为工具参数 |

## 快速开始

### 定义工具

```java
import com.lunarlanding.qualia.core.mcp.server.annotation.McpTool;
import com.lunarlanding.qualia.core.mcp.server.annotation.McpToolParam;

public class MyToolService {

    @McpTool(description = "查询天气信息")
    public String getWeather(
            @McpToolParam(description = "城市名称") String city,
            @McpToolParam(description = "温度单位", required = false) String unit) {
        // 业务逻辑
        return city + " 天气晴朗，25°C";
    }
}
```

### 启动服务器

```java
import com.lunarlanding.qualia.core.mcp.server.McpServer;
import com.lunarlanding.qualia.core.mcp.server.McpServerParameters;

// 1. 配置服务器参数
McpServerParameters params = McpServerParameters.create("my-mcp-server", "1.0.0")
    .withEndpoint("/mcp")
    .withInstructions("提供天气查询服务");

// 2. 创建并启动服务器
McpServer server = new McpServer(params, new MyToolService());
server.start();
```

## 注解详解

### @McpTool

标记方法为 MCP 工具。

```java
@McpTool(
    name = "custom_tool_name",  // 可选，默认使用方法名的 snake_case 形式
    title = "工具标题",         // 可选
    description = "工具描述"    // 必填
)
public String myMethod() { ... }
```

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | String | 否 | 工具名称，默认使用方法名的 snake_case 形式 |
| `title` | String | 否 | 工具标题 |
| `description` | String | 是 | 工具描述 |

### @McpToolParam

标记方法参数为 MCP 工具参数。

```java
public String myMethod(
    @McpToolParam(description = "参数描述", required = true) String param1,
    @McpToolParam(description = "可选参数", required = false) Integer param2
) { ... }
```

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `description` | String | 否 | 参数描述 |
| `required` | boolean | 否 | 是否必填，默认 `true` |

### 类型映射

框架自动将 Java 类型映射为 JSON Schema 类型：

| Java 类型 | JSON Schema 类型 |
|-----------|------------------|
| `String` | `string` |
| `Integer`, `int`, `Long`, `long` | `integer` |
| `Double`, `double`, `Float`, `float`, `BigDecimal` | `number` |
| `Boolean`, `boolean` | `boolean` |

## 服务器配置

### McpServerParameters

```java
McpServerParameters params = McpServerParameters.create("server-name", "1.0.0")
    .withEndpoint("/mcp")                    // HTTP 端点路径，默认 "/mcp"
    .withInstructions("服务器说明")           // 可选，服务器说明
    .withKeepAliveInterval(Duration.ofSeconds(30));  // Keep-Alive 间隔
```

| 方法 | 说明 | 默认值 |
|------|------|--------|
| `create(name, version)` | 创建参数实例 | - |
| `withEndpoint(endpoint)` | 设置 HTTP 端点 | `/mcp` |
| `withInstructions(instructions)` | 设置服务器说明 | null |
| `withKeepAliveInterval(interval)` | 设置 Keep-Alive 间隔 | 30 秒 |

## Spring Boot 集成

### 配置类

```java
@Configuration
public class McpServerConfig {

    @Bean
    public McpServer mcpServer(MyToolService toolService) {
        McpServerParameters params = McpServerParameters.create("my-server", "1.0.0")
            .withEndpoint("/mcp")
            .withInstructions("提供工具服务");

        McpServer server = new McpServer(params, toolService);
        server.start();
        return server;
    }

    @Bean
    public ServletRegistrationBean<?> mcpServlet(McpServer server) {
        HttpServletStreamableServerTransportProvider transportProvider = server.getTransportProvider();
        ServletRegistrationBean<?> bean = new ServletRegistrationBean<>(
            transportProvider, 
            server.getParams().getEndpoint()
        );
        bean.setName("mcpServlet");
        bean.setLoadOnStartup(1);
        bean.setAsyncSupported(true);
        return bean;
    }
}
```

### application.yml 配置

```yaml
mcp:
  server:
    name: my-mcp-server
    version: 1.0.0
    endpoint: /mcp
    instructions: 提供工具服务
```

## 多工具注册

```java
McpServer server = new McpServer(params,
    new WeatherToolService(),
    new SearchToolService(),
    new DatabaseToolService()
);
server.start();
```

## 完整示例

```java
import com.lunarlanding.qualia.core.mcp.server.McpServer;
import com.lunarlanding.qualia.core.mcp.server.McpServerParameters;
import com.lunarlanding.qualia.core.mcp.server.annotation.McpTool;
import com.lunarlanding.qualia.core.mcp.server.annotation.McpToolParam;

// 1. 定义工具服务
public class MathToolService {

    @McpTool(description = "计算两数之和")
    public int add(
            @McpToolParam(description = "第一个数") int a,
            @McpToolParam(description = "第二个数") int b) {
        return a + b;
    }

    @McpTool(name = "multiply", description = "计算两数之积")
    public int multiply(
            @McpToolParam(description = "第一个数") int a,
            @McpToolParam(description = "第二个数") int b) {
        return a * b;
    }
}

// 2. 启动服务器
public class MathServer {
    public static void main(String[] args) {
        McpServerParameters params = McpServerParameters.create("math-server", "1.0.0")
            .withEndpoint("/mcp")
            .withInstructions("提供数学计算服务");

        try (McpServer server = new McpServer(params, new MathToolService())) {
            server.start();
            System.out.println("MCP Server 已启动");
            Thread.currentThread().join();  // 保持服务运行
        }
    }
}
```

## 生命周期管理

```java
// 启动服务器
server.start();

// 检查状态
if (server.isStarted()) {
    System.out.println("服务器正在运行");
}

// 获取配置
McpServerParameters params = server.getParams();
System.out.println("服务器名称: " + params.getName());

// 关闭服务器（实现 AutoCloseable）
server.close();
```

## 最佳实践

1. **使用 try-with-resources**：确保服务器正确关闭
2. **工具描述清晰**：为每个工具提供清晰的 description
3. **参数必填标记**：合理设置 `required` 属性
4. **错误处理**：工具方法抛出的异常会被框架捕获并返回错误信息
5. **依赖管理**：使用 Spring Boot 时，确保 `spring-context` 依赖

## 相关文档

- [MCP 客户端](/docs/mcp-client) - 连接远程 MCP 服务器
- [工具系统](/docs/function-tool) - FunctionTool 与参数声明
