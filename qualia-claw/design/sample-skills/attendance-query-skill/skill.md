---
description: '模拟考勤查询技能：按日期/部门查询员工打卡记录与迟到统计'
---

# Attendance Query

模拟考勤数据查询技能，支持按日期和部门查询员工打卡记录，并输出迟到统计。

## 功能说明

### 1. 查询打卡记录

使用 `query_attendance.py` 脚本查询模拟考勤数据。

**参数说明**：
- `date` (可选): 查询日期，格式 YYYY-MM-DD，默认今天
- `department` (可选): 按部门筛选，如"技术部"、"市场部"
- `late_only` (可选): 传 `true` 时只返回迟到记录

**示例查询**：
- 查询今天全部打卡：无参数
- 查询技术部某天考勤：`--date=2026-08-05 --department=技术部`
- 只看迟到记录：`--late_only=true`

### 2. 返回数据格式

```json
{
  "date": "2026-08-05",
  "total": 5,
  "late_count": 1,
  "records": [
    {"name": "张三", "department": "技术部", "check_in": "09:02", "late": false}
  ]
}
```

## 使用流程

1. 使用 `load_skill` 加载本技能
2. 使用 `skill-script-runner` 执行 `query_attendance.py`
3. 解析 JSON 结果，向用户汇总迟到人数与明细
