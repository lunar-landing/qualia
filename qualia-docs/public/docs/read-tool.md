# ReadTool 文档

读取文件内容，支持指定行范围。

## 简介

`ReadTool` 是 Qualia 框架内置的文件读取工具，用于读取工作区内的文件内容。支持读取整个文件或指定行范围的内容。

## 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `path` | String | 是 | 文件路径（相对于工作区） |
| `begin` | Integer | 否 | 起始行号（从1开始） |
| `end` | Integer | 否 | 结束行号（包含该行） |

## 示例

```java
// 读取整个文件
Map<String, Object> args = Map.of("path", "src/Main.java");
String result = tool.execute(args);

// 读取指定行范围
Map<String, Object> args = Map.of("path", "src/Main.java", "begin", 10, "end", 20);
String result = tool.execute(args);
```

## 输出格式

```
    10→public class Main {
    11→    public static void main(String[] args) {
    12→        System.out.println("Hello");
    13→    }
    14→}
```

## 使用场景

- 查看代码文件内容
- 检查配置文件
- 读取日志文件
- 查看文档文件

## 注意事项

- 文件路径必须相对于工作区根目录
- 行号从1开始计数
- 如果指定行范围超出文件实际行数，会自动调整到文件末尾

## 错误处理

- 文件不存在：返回错误信息"文件不存在"
- 路径越界：返回错误信息"路径超出工作区范围"
- 权限不足：返回错误信息"没有读取权限"

## 相关工具

- [WriteTool](./write-tool.md) - 写入文件内容
- [EditTool](./edit-tool.md) - 编辑文件内容
- [GrepTool](./grep-tool.md) - 搜索文件内容
- [GlobTool](./glob-tool.md) - 搜索文件路径
- [BashTool](./bash-tool.md) - 执行系统命令