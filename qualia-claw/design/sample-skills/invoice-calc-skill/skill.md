---
description: '模拟发票计算技能：按金额与税率规则计算含税价、税额与折扣后金额'
---

# Invoice Calc

脚本 + 文档复合型技能：根据给定的金额、税率与折扣，计算含税总价与税额明细。

## 功能说明

### 1. 计算发票金额

使用 `calc_invoice.py` 脚本完成计算。

**参数说明**：
- `amount` (必填): 不含税金额，单位元
- `rate` (可选): 税率百分比，默认 6，可选 3 / 6 / 9 / 13
- `discount` (可选): 折扣比例（0-1 之间的小数），如 0.9 表示九折

**示例查询**：
- 基础计算：`--amount=1000`
- 指定税率：`--amount=1000 --rate=13`
- 九折优惠：`--amount=2000 --discount=0.9`

### 2. 返回数据格式

```json
{
  "amount": 1000.0,
  "discount": 1.0,
  "tax_rate": 0.06,
  "tax": 60.0,
  "total": 1060.0
}
```

### 3. 税率选择依据

不同业务类型适用税率见附属文档 `references/tax-rates.md`，不确定时先查阅再计算。

## 使用流程

1. 使用 `load_skill` 加载本技能
2. 根据业务类型在 `tax-rates.md` 中确认税率
3. 使用 `skill-script-runner` 执行 `calc_invoice.py` 并汇报结果
