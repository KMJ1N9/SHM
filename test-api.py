#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
校园二手交易平台 — API 自动化测试脚本
========================================
一套脚本同时验证 Node.js (端口 3002) 和 Java (端口 8080) 两个后端。

用法:
  python test-api.py              # 默认测试 Java 后端 (:8080)
  python test-api.py node         # 测试 Node.js 后端 (:3002)
  python test-api.py java         # 测试 Java 后端 (:8080)
  python test-api.py --all        # 两个后端都测

依赖: pip install requests
"""

import sys
import json
import time
import random
import requests
from dataclasses import dataclass, field
from typing import Optional, List, Dict, Any


# ============================================================
# 配置
# ============================================================

@dataclass
class Config:
    name: str
    base_url: str


TARGETS = {
    "java": Config(name="Java", base_url="http://localhost:8080/api"),
    "node": Config(name="Node.js", base_url="http://localhost:3002/api"),
}

COLORS = {
    "PASS": "\033[92m",
    "FAIL": "\033[91m",
    "WARN": "\033[93m",
    "INFO": "\033[94m",
    "RESET": "\033[0m",
    "BOLD": "\033[1m",
}


# ============================================================
# 测试框架
# ============================================================

class TestRunner:
    def __init__(self, config: Config):
        self.cfg = config
        self.passed = 0
        self.failed = 0
        self.skipped = 0
        self.warnings = 0
        # 测试之间共享的状态
        self.access_token: Optional[str] = None
        self.refresh_token: Optional[str] = None
        self.user_id: Optional[int] = None
        self.product_id: Optional[int] = None
        self.order_id: Optional[int] = None
        self.test_phone: str = ""

    def log(self, level: str, msg: str):
        color = COLORS.get(level, "")
        print(f"  {color}[{level}]{COLORS['RESET']} {msg}")

    def __call__(self, name: str, fn):
        """装饰器风格的测试用例注册"""
        print(f"\n{COLORS['BOLD']}--- {name} ---{COLORS['RESET']}")
        try:
            fn(self)
            self.passed += 1
            self.log("PASS", "通过")
        except AssertionError as e:
            self.failed += 1
            self.log("FAIL", str(e))
        except requests.exceptions.ConnectionError:
            self.failed += 1
            self.log("FAIL", f"连接失败 — {self.cfg.name} 后端未启动？")
            raise SystemExit(1)
        except Exception as e:
            self.failed += 1
            self.log("FAIL", f"未预期错误: {type(e).__name__}: {e}")

    def assert_status(self, resp: requests.Response, expected: int):
        assert resp.status_code == expected, (
            f"HTTP {resp.status_code} != {expected}\n"
            f"  URL: {resp.request.method} {resp.request.url}\n"
            f"  Body: {resp.text[:300]}"
        )

    def assert_code(self, resp: requests.Response, expected_code: int = 0):
        body = resp.json()
        actual = body.get("code", -1)
        assert actual == expected_code, (
            f"code={actual} != {expected_code}\n"
            f"  message: {body.get('message', 'N/A')}\n"
            f"  URL: {resp.request.method} {resp.request.url}"
        )
        return body

    def assert_ok(self, resp: requests.Response):
        return self.assert_code(resp, 0)

    def api(self, method: str, path: str, **kwargs) -> requests.Response:
        url = f"{self.cfg.base_url}{path}"
        headers = kwargs.pop("headers", {})
        if self.access_token and not kwargs.get("skip_auth"):
            headers["Authorization"] = f"Bearer {self.access_token}"
        return requests.request(method, url, headers=headers, timeout=10, **kwargs)

    def summary(self):
        total = self.passed + self.failed + self.skipped
        print(f"\n{'='*50}")
        print(f"{COLORS['BOLD']}{self.cfg.name} 后端测试结果:{COLORS['RESET']}")
        print(f"  总计: {total}")
        print(f"  {COLORS['PASS']}通过: {self.passed}{COLORS['RESET']}")
        print(f"  {COLORS['FAIL']}失败: {self.failed}{COLORS['RESET']}")
        if self.skipped:
            print(f"  {COLORS['WARN']}跳过: {self.skipped}{COLORS['RESET']}")
        print(f"{'='*50}")
        return self.failed == 0


# ============================================================
# 测试用例
# ============================================================

def test_health(r: TestRunner):
    """健康检查"""
    resp = r.api("GET", "/health")
    r.assert_ok(resp)
    data = resp.json()["data"]
    assert data["status"] == "UP", f"服务状态: {data.get('status')}"
    components = data.get("components", {})
    r.log("INFO", f"组件: {', '.join(f'{k}={v}' for k, v in sorted(components.items()))}")


def test_login(r: TestRunner):
    """Mock 登录"""
    phone = f"138{random.randint(10000000, 99999999):d}"
    r.test_phone = phone
    resp = r.api("POST", "/auth/login",
                 json={"code": f"mock_{phone}"}, skip_auth=True)
    body = r.assert_ok(resp)
    data = body["data"]
    assert "accessToken" in data, "缺少 accessToken"
    assert "refreshToken" in data, "缺少 refreshToken"
    assert "user" in data, "缺少 user 字段"
    r.access_token = data["accessToken"]
    r.refresh_token = data["refreshToken"]
    r.user_id = data["user"]["id"]
    r.log("INFO", f"用户 ID={r.user_id}, 手机号={phone}, "
           f"新用户={'是' if data.get('isNewUser') else '否'}")


def test_me(r: TestRunner):
    """获取当前用户信息"""
    resp = r.api("GET", "/auth/me")
    body = r.assert_ok(resp)
    user = body["data"]
    assert user["id"] == r.user_id, "用户 ID 不匹配"
    r.log("INFO", f"昵称={user.get('nickname')}, "
           f"信誉分={user.get('creditScore')}, 角色={user.get('role')}")


def test_refresh_token(r: TestRunner):
    """刷新 Token"""
    resp = r.api("POST", "/auth/refresh",
                 json={"refresh_token": r.refresh_token}, skip_auth=True)
    body = r.assert_ok(resp)
    data = body["data"]
    assert "accessToken" in data, "缺少新 accessToken"
    r.access_token = data["accessToken"]
    r.log("INFO", "Token 刷新成功")


def test_product_list(r: TestRunner):
    """商品列表"""
    resp = r.api("GET", "/products")
    body = r.assert_ok(resp)
    data = body["data"]
    assert "list" in data, "缺少 list 字段"
    r.log("INFO", f"商品总数: {data.get('total', 'N/A')}")
    items = data["list"]
    if items:
        r.product_id = items[0]["id"]
        r.log("INFO", f"首条商品: ID={items[0]['id']}, {items[0].get('title', 'N/A')[:30]}")
    else:
        r.log("WARN", "无商品数据，跳过后续订单测试")


def test_product_detail(r: TestRunner):
    """商品详情"""
    if not r.product_id:
        r.skipped += 1
        r.log("WARN", "无可用商品 ID，跳过")
        return
    resp = r.api("GET", f"/products/{r.product_id}")
    body = r.assert_ok(resp)
    data = body["data"]
    assert data["id"] == r.product_id
    assert "title" in data, "缺少 title"
    assert "sellerInfo" in data, "缺少 sellerInfo"
    r.log("INFO", f"标题={data.get('title', 'N/A')[:30]}, "
           f"售价=¥{data.get('price')}")


def test_product_search(r: TestRunner):
    """商品搜索"""
    resp = r.api("GET", "/products", params={"keyword": "书"})
    r.assert_ok(resp)
    items = resp.json()["data"]["list"]
    r.log("INFO", f"搜索'书'结果: {len(items)} 条")


def test_product_filter(r: TestRunner):
    """商品分类筛选"""
    resp = r.api("GET", "/products", params={"category": "电子产品"})
    r.assert_ok(resp)
    items = resp.json()["data"]["list"]
    r.log("INFO", f"电子产品分类: {len(items)} 条")


def test_create_order(r: TestRunner):
    """创建订单"""
    if not r.product_id:
        r.skipped += 1
        r.log("WARN", "无可用商品 ID，跳过")
        return
    resp = r.api("POST", "/orders",
                 json={"product_id": r.product_id})
    body = r.assert_ok(resp)
    data = body["data"]
    assert "order" in data, "缺少 order 字段"
    if data["order"]:
        r.order_id = data["order"]["id"]
        r.log("INFO", f"订单创建: ID={r.order_id}, 状态={data['order'].get('status')}")
    else:
        r.log("WARN", "订单已存在或商品不可购买（可能为自己的商品）")


def test_order_list(r: TestRunner):
    """订单列表"""
    resp = r.api("GET", "/orders")
    body = r.assert_ok(resp)
    data = body["data"]
    assert "list" in data, "缺少 list"
    r.log("INFO", f"订单总数: {data.get('total', 'N/A')}")


def test_order_detail(r: TestRunner):
    """订单详情"""
    if not r.order_id:
        r.skipped += 1
        r.log("WARN", "无可用订单 ID，跳过")
        return
    resp = r.api("GET", f"/orders/{r.order_id}")
    body = r.assert_ok(resp)
    data = body["data"]
    assert data["id"] == r.order_id
    r.log("INFO", f"状态={data.get('status')}, "
           f"买家={data.get('buyer_nickname', 'N/A')}")


def test_initiate_met(r: TestRunner):
    """发起面交确认"""
    if not r.order_id:
        r.skipped += 1
        return
    resp = r.api("PUT", f"/orders/{r.order_id}/met/initiate")
    body = r.assert_ok(resp)
    r.log("INFO", f"状态: {body['data'].get('status')}")


def test_order_status_filter(r: TestRunner):
    """订单状态筛选"""
    resp = r.api("GET", "/orders", params={"status": "pending"})
    r.assert_ok(resp)
    r.log("INFO", f"pending 订单: {resp.json()['data'].get('total', 'N/A')} 条")


def test_my_products(r: TestRunner):
    """我的发布"""
    resp = r.api("GET", "/products/my")
    r.assert_ok(resp)
    items = resp.json()["data"].get("list", [])
    r.log("INFO", f"我的发布: {len(items)} 条")



# ============================================================
# 测试用例注册表 (按顺序执行)
# ============================================================

TEST_SUITE = [
    ("健康检查", test_health),
    ("Mock 登录", test_login),
    ("获取当前用户", test_me),
    ("刷新 Token", test_refresh_token),
    ("商品列表", test_product_list),
    ("商品搜索", test_product_search),
    ("商品分类筛选", test_product_filter),
    ("商品详情", test_product_detail),
    ("创建订单", test_create_order),
    ("订单列表", test_order_list),
    ("订单状态筛选", test_order_status_filter),
    ("订单详情", test_order_detail),
    ("发起面交", test_initiate_met),
    ("我的发布", test_my_products),
]


# ============================================================
# 入口
# ============================================================

def run_tests(config: Config) -> bool:
    print(f"\n{'='*50}")
    print(f"{COLORS['BOLD']}[目标] {config.name} ({config.base_url}){COLORS['RESET']}")
    print(f"{'='*50}")

    # 先确认后端可达
    try:
        r = requests.get(f"{config.base_url}/health", timeout=5)
        if r.status_code != 200:
            print(f"{COLORS['FAIL']}ERR 后端不可达 (HTTP {r.status_code}){COLORS['RESET']}")
            return False
        print(f"{COLORS['PASS']}OK 后端可达{COLORS['RESET']}")
    except requests.exceptions.ConnectionError:
        print(f"{COLORS['FAIL']}ERR 无法连接 {config.base_url} — 后端未启动？{COLORS['RESET']}")
        return False

    runner = TestRunner(config)
    for name, fn in TEST_SUITE:
        runner(name, fn)

    return runner.summary()


def main():
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    args = [a.lower() for a in sys.argv[1:]]

    if "--all" in args:
        targets = list(TARGETS.values())
    elif "node" in args:
        targets = [TARGETS["node"]]
    elif "java" in args:
        targets = [TARGETS["java"]]
    else:
        # 默认 Java（当前活跃后端）
        targets = [TARGETS["java"]]

    all_pass = True
    for cfg in targets:
        if not run_tests(cfg):
            all_pass = False

    # 双后端对比（如果两个都测了）
    if len(targets) == 2:
        print(f"\n{COLORS['BOLD']}[双后端 API 契约一致性]{COLORS['RESET']}")
        print("  两个后端返回格式、路径、错误码应当完全一致。")
        print("  如果上面两个测试结果有差异，说明 API 契约不匹配。")

    sys.exit(0 if all_pass else 1)


if __name__ == "__main__":
    main()
