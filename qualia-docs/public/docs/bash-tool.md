# BashTool 文档

执行系统命令，包括 git、npm 等。

## 简介

`BashTool` 是 Qualia 框架内置的系统命令执行工具，用于在工作区内执行各种系统命令，如 git、npm、maven 等。

## 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `command` | String | 是 | 要执行的命令 |
| `working_directory` | String | 否 | 工作目录（相对于工作区，默认为工作区根目录） |
| `timeout` | Integer | 否 | 超时时间（秒），默认 30 |

## 安全限制

- 工作目录必须在工作区范围内
- Windows 系统使用 `cmd /c` 执行命令
- Linux/Mac 系统使用 `sh -c` 执行命令

## 示例

```java
// 执行 git 命令
Map<String, Object> args = Map.of("command", "git status");
String result = tool.execute(args);

// 执行 npm 命令并指定工作目录
Map<String, Object> args = Map.of("command", "npm install", "working_directory", "frontend");
String result = tool.execute(args);
```

## 使用场景

- 执行版本控制命令（git）
- 运行构建工具（maven、gradle、npm）
- 执行系统管理命令
- 运行测试脚本
- 文件系统操作（ls、dir、cp、mv）

## 注意事项

- 命令在工作区根目录下执行，除非指定 `working_directory`
- 超时时间默认为30秒，长时间运行的命令需要增加超时时间
- 命令输出会返回标准输出和标准错误内容
- 某些危险命令（如 rm -rf）可能被安全策略阻止

## 错误处理

- 命令执行失败：返回错误信息和退出码
- 超时：返回错误信息"命令执行超时"
- 路径越界：返回错误信息"工作目录超出工作区范围"
- 权限不足：返回错误信息"没有执行权限"

## 相关工具

- [ReadTool](./read-tool.md) - 读取文件内容
- [WriteTool](./write-tool.md) - 写入文件内容
- [EditTool](./edit-tool.md) - 编辑文件内容
- [GrepTool](./grep-tool.md) - 搜索文件内容
- [GlobTool](./glob-tool.md) - 搜索文件路径