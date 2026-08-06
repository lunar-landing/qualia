#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# 计算发票含税金额：支持税率选择与折扣，输出税额明细 JSON
import json
import sys

ALLOWED_RATES = {3, 6, 9, 13}


def calc_invoice(amount, rate=6, discount=1.0):
    if amount <= 0:
        raise ValueError("金额必须大于 0")
    if rate not in ALLOWED_RATES:
        raise ValueError("税率必须是 3 / 6 / 9 / 13 之一")
    if not 0 < discount <= 1:
        raise ValueError("折扣必须在 (0, 1] 区间内")

    base = round(amount * discount, 2)
    tax = round(base * rate / 100, 2)
    return {
        "amount": amount,
        "discount": discount,
        "base_after_discount": base,
        "tax_rate": rate / 100,
        "tax": tax,
        "total": round(base + tax, 2)
    }


if __name__ == "__main__":
    amount = None
    rate = 6
    discount = 1.0

    for i in range(1, len(sys.argv)):
        if sys.argv[i].startswith("--amount="):
            amount = float(sys.argv[i].split("=", 1)[1])
        elif sys.argv[i].startswith("--rate="):
            rate = int(sys.argv[i].split("=", 1)[1])
        elif sys.argv[i].startswith("--discount="):
            discount = float(sys.argv[i].split("=", 1)[1])

    if amount is None:
        print(json.dumps({"error": "缺少必填参数 --amount"}, ensure_ascii=False))
        sys.exit(1)

    try:
        result = calc_invoice(amount, rate, discount)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except ValueError as e:
        print(json.dumps({"error": str(e)}, ensure_ascii=False))
        sys.exit(1)
