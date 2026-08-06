#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# 查询模拟考勤打卡记录，支持按日期/部门筛选与迟到过滤
import json
import sys
from datetime import date


def query_attendance(query_date=None, department=None, late_only=False):
    """模拟考勤数据（固定数据集，日期仅用于回显）"""
    records = [
        {"name": "张三", "department": "技术部", "check_in": "09:02", "late": False},
        {"name": "李四", "department": "技术部", "check_in": "09:35", "late": True},
        {"name": "王五", "department": "产品部", "check_in": "08:50", "late": False},
        {"name": "孙七", "department": "市场部", "check_in": "09:12", "late": True},
        {"name": "周八", "department": "技术部", "check_in": "08:58", "late": False},
        {"name": "郑十", "department": "人事部", "check_in": "09:00", "late": False},
    ]

    filtered = records
    if department:
        filtered = [r for r in filtered if r["department"] == department]
    if late_only:
        filtered = [r for r in filtered if r["late"]]

    return {
        "date": query_date or date.today().isoformat(),
        "total": len(filtered),
        "late_count": sum(1 for r in filtered if r["late"]),
        "records": filtered,
        "query_params": {
            "department": department,
            "late_only": late_only
        }
    }


if __name__ == "__main__":
    query_date = None
    department = None
    late_only = False

    for i in range(1, len(sys.argv)):
        if sys.argv[i].startswith("--date="):
            query_date = sys.argv[i].split("=", 1)[1]
        elif sys.argv[i].startswith("--department="):
            department = sys.argv[i].split("=", 1)[1]
        elif sys.argv[i].startswith("--late_only="):
            late_only = sys.argv[i].split("=", 1)[1].lower() == "true"

    result = query_attendance(query_date, department, late_only)
    print(json.dumps(result, ensure_ascii=False, indent=2))
