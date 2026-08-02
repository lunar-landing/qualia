# EditTool 文档

基于文本匹配替换文件内容，支持唯一匹配和全局替换。

## 简介

`EditTool`（也称为 `ReplaceTool`）是 Qualia 框架内置的文件编辑工具，用于基于文本匹配替换文件内容。支持唯一匹配和全局替换两种模式。

## 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | String | 是 | 文件路径（相对于工作区） |
| `old_text` | String | 是 | 要替换的原文文本 |
| `new_text` | String | 是 | 替换后的文本 |
| `replace_all` | Boolean | 否 | 是否替换所有匹配项，默认 `false` |

## 唯一性检查

默认情况下，`old_text` 必须在文件中唯一匹配。如果存在多处匹配，会返回错误提示。设置 `replace_all=true` 可替换所有匹配项。

## 示例

```java
// 替换唯一匹配
Map<String, Object> args = Map.of(
    "path", "config.json",
    "old_text", "\"version\": \"1.0\"",
    "new_text", "\"version\": \"2.0\""
);
String result = tool.execute(args);

// 替换所有匹配
Map<String, Object> args = Map.of(
    "path", "styles.css",
    "old_text", "color: red",
    "new_text", "color: blue",
    "replace_all", true
);
String result = tool.execute(args);
```

## 使用场景

- 修改配置文件中的特定值
- 更新代码中的变量名
- 替换文档中的占位符
- 批量修改文件内容

## 注意事项

- 文件路径必须相对于工作区根目录
- 默认模式下，`old_text` 必须在文件中唯一匹配
- 如果需要替换多处匹配，请设置 `replace_all=true`
- 替换操作是精确文本匹配，不是正则表达式

## 错误处理

- 文件不存在：返回错误信息"文件不存在"
- 无匹配项：返回错误信息"未找到匹配的文本"
- 多处匹配（未设置replace_all）：返回错误信息"找到多处匹配，请设置replace_all=true"
- 路径越界：返回错误信息"路径超出工作区范围"
- 权限不足：返回错误信息"没有写入权限"

## 相关工具

- [ReadTool](./read-tool.md) - 读取文件内容
- [WriteTool](./write-tool.md) - 写入文件内容
- [GrepTool](./grep-tool.md) - 搜索文件内容
- [GlobTool](./glob-tool.md) - 搜索文件路径
- [BashTool](./bash-tool.md) - 执行系统命令