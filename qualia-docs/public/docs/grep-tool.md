# GrepTool 文档

搜索文件内容，支持正则表达式匹配。

## 简介

`GrepTool` 是 Qualia 框架内置的文件内容搜索工具，用于在工作区内搜索包含特定模式的文件内容。支持正则表达式匹配。

## 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pattern` | String | 是 | 正则表达式模式 |
| `path` | String | 否 | 搜索路径（相对于工作区，默认为工作区根目录） |
| `glob` | String | 否 | 文件过滤模式（如 `*.java`） |
| `max_results` | Integer | 否 | 最大结果数，默认 100 |

## 输出格式

```
找到 3 个匹配:
src/Main.java:15: public class Main {
src/Main.java:20: public static void main(String[] args) {
src/Utils.java:8: public class Utils {
```

## 示例

```java
// 搜索包含 "TODO" 的代码
Map<String, Object> args = Map.of("pattern", "TODO");
String result = tool.execute(args);

// 在 Java 文件中搜索
Map<String, Object> args = Map.of("pattern", "class \\w+", "glob", "*.java");
String result = tool.execute(args);
```

## 使用场景

- 查找代码中的特定模式
- 搜索包含特定文本的文件
- 查找函数或类的定义
- 搜索配置文件中的特定设置
- 查找日志文件中的错误信息

## 注意事项

- 正则表达式使用 Java 正则语法
- 搜索结果包含文件名、行号和匹配内容
- 可以使用 `glob` 参数限制搜索的文件类型
- 默认最大结果数为100，可以通过 `max_results` 调整

## 错误处理

- 正则表达式语法错误：返回错误信息"正则表达式语法错误"
- 路径不存在：返回错误信息"搜索路径不存在"
- 路径越界：返回错误信息"路径超出工作区范围"
- 权限不足：返回错误信息"没有读取权限"

## 相关工具

- [ReadTool](./read-tool.md) - 读取文件内容
- [WriteTool](./write-tool.md) - 写入文件内容
- [EditTool](./edit-tool.md) - 编辑文件内容
- [GlobTool](./glob-tool.md) - 搜索文件路径
- [BashTool](./bash-tool.md) - 执行系统命令