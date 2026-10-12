---
title: 系统架构总览
author: 架构组
---

# 系统架构总览

本文描述「资料」功能的整体分层与关键设计，供新成员快速建立心智模型。

## 整体分层

### 接入层

前端为整栏覆盖层（列表页 ⇄ 详情页），通过 `/api/materials` REST 接口与后端交互，无独立状态服务。

### 服务层

`MaterialService` 承担库管理、树构建、上传转换与文章读写，单例运行，存储于用户配置目录。

## 模块划分

| 模块 | 职责 | 关键入口 |
| --- | --- | --- |
| 库管理 | 建/删/改资料库，统计文章数与字数 | `GET/POST/PATCH/DELETE /api/materials` |
| 文章树 | 目录与文章的树形组织 | `GET /{id}/tree` |
| 上传转换 | md/txt 直接入库，pdf/docx 抽全文 | `POST /{id}/files` |
| 阅读编辑 | frontmatter 剥离与原子保存 | `GET/PUT /{id}/article` |

## 关键设计

> 路径即 ID：库内以相对路径寻址，重命名只改文件名，链接与导航天然一致。

转换管线是同步完成的，秒级返回，不引入异步队列与轮询状态机：

```java
// 上传即成文：md 直接入库，pdf/docx 抽取全文后补 frontmatter
List<UploadResult> results = service.upload(id, parentPath, files);
```

### 存储模型

每个库一个目录：`kb.json` 记录元信息，`articles/` 存整篇 Markdown，`originals/` 留存上传原件。

### 扩展点

二期智能体阅读/写作只需在存储模型上加工具层，不动现有结构。
