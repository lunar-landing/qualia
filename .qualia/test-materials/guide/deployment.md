# 部署指南

## 环境要求

| 组件 | 最低版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | 后端构建与运行 |
| Node.js | 20 | 前端构建 |
| 操作系统 | Windows 10+ / macOS | 桌面端打包见 packaging 目录 |

## 部署步骤

1. 构建前端产物，输出到后端静态目录
2. 打包后端 fat jar
3. 启动并验证健康接口

```bash
npm run build
mvn -pl qualia-code -am package
java -jar qualia-code/target/qualia-code-*.jar
```

## 回滚

保留上一版本的 jar 与 `~/.qualia` 数据目录即可；资料内容全部在用户目录下，升级不迁移、回滚不丢数据。
