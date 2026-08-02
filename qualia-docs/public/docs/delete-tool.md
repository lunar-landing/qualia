# DeleteTool 文档

删除工作区内的文件，仅限普通文件，不能删除目录。

## 简介

`DeleteTool` 是 Qualia 框架内置的文件删除工具，用于删除工作区内不再需要的文件。相比通过 `BashTool` 执行删除命令，`DeleteTool` 的删除操作会被前端变更面板捕捉，可在会话的文件变更记录中追溯。

## 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | String | 是 | 文件路径（相对于工作区） |

## 示例

```java
// 删除文件
Map<String, Object> args = Map.of("path", "temp/output.txt");
String result = tool.execute(args);
```

## 使用场景

- 清理临时文件、生成产物
- 重构时移除废弃的源码文件
- 删除错误创建的文件

## 注意事项

- 文件路径必须相对于工作区根目录，路径经过 normalize 校验，禁止越界访问工作区外的文件
- 仅支持删除普通文件，目录不能删除（防止误删大范围内容）
- 删除操作不可恢复，模型调用前应确认文件确实不再需要

## 错误处理

- 文件不存在：返回错误信息"文件不存在"
- 路径是目录：返回错误信息"路径不是普通文件，不能删除目录"
- 路径越界：返回错误信息"路径超出工作区范围"
- 权限不足：返回错误信息"删除文件失败"

## 相关工具

- [ReadTool](./read-tool.md) - 读取文件内容
- [WriteTool](./write-tool.md) - 写入文件内容
- [EditTool](./edit-tool.md) - 编辑文件内容
- [GrepTool](./grep-tool.md) - 搜索文件内容
- [GlobTool](./glob-tool.md) - 搜索文件路径
- [BashTool](./bash-tool.md) - 执行系统命令
