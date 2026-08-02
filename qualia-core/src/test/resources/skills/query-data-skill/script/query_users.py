#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
查询系统用户数据脚本
"""
import json
import sys

def query_users(department=None, role=None, limit=10):
    """模拟查询系统用户数据"""
    # 模拟数据
    users = [
        {"id": 1, "name": "张三", "department": "技术部", "role": "开发工程师", "email": "zhangsan@example.com"},
        {"id": 2, "name": "李四", "department": "技术部", "role": "测试工程师", "email": "lisi@example.com"},
        {"id": 3, "name": "王五", "department": "产品部", "role": "产品经理", "email": "wangwu@example.com"},
        {"id": 4, "name": "赵六", "department": "技术部", "role": "架构师", "email": "zhaoliu@example.com"},
        {"id": 5, "name": "孙七", "department": "市场部", "role": "市场专员", "email": "sunqi@example.com"},
        {"id": 6, "name": "周八", "department": "技术部", "role": "前端工程师", "email": "zhouba@example.com"},
        {"id": 7, "name": "吴九", "department": "产品部", "role": "UI设计师", "email": "wujiu@example.com"},
        {"id": 8, "name": "郑十", "department": "人事部", "role": "HR专员", "email": "zhengshi@example.com"},
    ]
    
    # 过滤条件
    filtered = users
    if department:
        filtered = [u for u in filtered if u["department"] == department]
    if role:
        filtered = [u for u in filtered if role in u["role"]]
    
    # 限制数量
    result = filtered[:limit]
    
    return {
        "total": len(filtered),
        "count": len(result),
        "users": result,
        "query_params": {
            "department": department,
            "role": role,
            "limit": limit
        }
    }

if __name__ == "__main__":
    # 从命令行参数获取查询条件
    department = None
    role = None
    limit = 10
    
    # 解析参数
    for i in range(1, len(sys.argv)):
        if sys.argv[i].startswith("--department="):
            department = sys.argv[i].split("=")[1]
        elif sys.argv[i].startswith("--role="):
            role = sys.argv[i].split("=")[1]
        elif sys.argv[i].startswith("--limit="):
            limit = int(sys.argv[i].split("=")[1])
    
    result = query_users(department, role, limit)
    print(json.dumps(result, ensure_ascii=False, indent=2))
