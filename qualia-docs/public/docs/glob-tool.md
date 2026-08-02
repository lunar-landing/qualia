# GlobTool 文档

搜索文件路径，支持 glob 模式匹配。

## 简介

`GlobTool` 是 Qualia 框架内置的文件路径搜索工具，用于在工作区内搜索符合特定模式的文件路径。支持标准的 glob 模式匹配。

## 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pattern` | String | 是 | glob 模式（如 `**/*.java`） |
| `path` | String | 否 | 搜索路径（相对于工作区，默认为工作区根目录） |

## 常用 glob 模式

| 模式 | 说明 |
|------|------|
| `*.java` | 当前目录下所有 Java 文件 |
| `**/*.java` | 递归查找所有 Java 文件 |
| `src/**/*.js` | src 目录下所有 JS 文件 |
| `**/*.{java,kt}` | 所有 Java 和 Kotlin 文件 |

## 示例

```java
// 查找所有 Java 文件
Map<String, Object> args = Map.of("pattern", "**/*.java");
String result = tool.execute(args);

// 查找配置文件
Map<String, Object> args = Map.of("pattern", "**/*.{json,yaml,yml}");
String result = tool.execute(args);
```

## 使用场景

- 查找特定类型的文件
- 查找特定目录下的文件
- 查找符合命名规范的文件
- 查找配置文件
- 查找资源文件

## 注意事项

- glob 模式使用标准的 glob 语法
- `*` 匹配任意字符（除了路径分隔符）
- `**` 匹配任意数量的路径层级
- `?` 匹配单个字符
- `{a,b}` 匹配多个模式中的任意一个

## 错误处理

- 路径不存在：返回错误信息"搜索路径不存在"
- 路径越界：返回错误信息"路径超出工作区范围"
- 权限不足：返回错误信息"没有读取权限"

## 相关工具

- [ReadTool](./read-tool.md) - 读取文件内容
- [WriteTool](./write-tool.md) - 写入文件内容
- [EditTool](./edit-tool.md) - 编辑文件内容
- [GrepTool](./grep-tool.md) - 搜索文件内容
- [BashTool](./bash-tool.md) - 执行系统命令