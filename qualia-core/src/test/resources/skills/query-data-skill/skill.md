---
description: '系统数据查询技能'
---

# System Data Query

提供系统数据查询功能，支持查询用户、部门、角色等信息。

## 功能说明

本技能提供以下数据查询能力：

### 1. 查询用户数据

使用 `query_users.py` 脚本查询系统用户信息。

**参数说明**：
- `department` (可选): 按部门筛选，如"技术部"、"产品部"
- `role` (可选): 按角色筛选，如"开发工程师"、"产品经理"
- `limit` (可选): 返回结果数量限制，默认为10

**示例查询**：
- 查询所有用户：无参数
- 查询技术部用户：`--department=技术部`
- 查询所有工程师：`--role=工程师`
- 查询前5个用户：`--limit=5`

### 2. 返回数据格式

```json
{
  "total": 8,
  "count": 3,
  "users": [
    {
      "id": 1,
      "name": "张三",
      "department": "技术部",
      "role": "开发工程师",
      "email": "zhangsan@example.com"
    }
  ],
  "query_params": {
    "department": "技术部",
    "role": null,
    "limit": 10
  }
}
```

## 使用流程

1. 使用 `load_skill` 加载本技能
2. 使用 `skill-script-runner` 执行查询脚本
3. 解析返回的 JSON 数据并展示给用户
