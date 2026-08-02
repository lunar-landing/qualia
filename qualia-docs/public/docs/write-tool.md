# WriteTool 文档

写入文件内容，支持覆盖、追加和插入三种模式。

## 简介

`WriteTool` 是 Qualia 框架内置的文件写入工具，用于创建新文件或修改现有文件内容。支持三种写入模式：覆盖、追加和插入。

## 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | String | 是 | 文件路径（相对于工作区） |
| `content` | String | 是 | 要写入的内容 |
| `mode` | String | 否 | 写入模式：`overwrite`（覆盖）、`append`（追加）、`insert`（插入），默认 `overwrite` |
| `line` | Integer | 否 | 插入行号（仅 `insert` 模式有效，从1开始） |

## 示例

```java
// 覆盖写入
Map<String, Object> args = Map.of("path", "output.txt", "content", "Hello World");
String result = tool.execute(args);

// 追加内容
Map<String, Object> args = Map.of("path", "log.txt", "content", "New log entry", "mode", "append");
String result = tool.execute(args);

// 插入到指定行
Map<String, Object> args = Map.of("path", "config.xml", "content", "<new-tag/>", "mode", "insert", "line", 5);
String result = tool.execute(args);
```

## 使用场景

- 创建新文件
- 修改配置文件
- 添加日志条目
- 插入代码片段
- 生成报告文件

## 注意事项

- 文件路径必须相对于工作区根目录
- 覆盖模式会完全替换文件内容
- 追加模式会在文件末尾添加内容
- 插入模式需要指定行号，行号从1开始计数

## 错误处理

- 文件不存在（覆盖/追加模式）：自动创建新文件
- 文件不存在（插入模式）：返回错误信息"文件不存在"
- 路径越界：返回错误信息"路径超出工作区范围"
- 权限不足：返回错误信息"没有写入权限"

## 相关工具

- [ReadTool](./read-tool.md) - 读取文件内容
- [EditTool](./edit-tool.md) - 编辑文件内容
- [GrepTool](./grep-tool.md) - 搜索文件内容
- [GlobTool](./glob-tool.md) - 搜索文件路径
- [BashTool](./bash-tool.md) - 执行系统命令