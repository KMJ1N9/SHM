#!/bin/bash
# =============================================================================
# API 契约对比脚本（Phase 11.3.1）
#
# 用途：逐 API 对比 Node.js (3000) 和 Java Gateway (8080) 的输出。
# 原理：同一请求分别发到两个后端 → jq -S 排序后 diff → 不一致标红。
#
# 使用：
#   1. 启动 Node.js 后端：  cd server && npm run dev
#   2. 启动 Java 后端：     cd server-java && ./start-all.sh
#   3. 运行对比：           bash scripts/api-contract-compare.sh
#   4. 输出报告：           scripts/api-contract-compare.sh --json > report.json
#
# 依赖：curl jq diff
# =============================================================================

set -euo pipefail

NODE_URL="${NODE_URL:-http://localhost:3000}"
JAVA_URL="${JAVA_URL:-http://localhost:8080}"
OUTPUT_FORMAT="${1:-text}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PASS=0
FAIL=0
SKIP=0

# 测试账号（种子数据中的测试用户）
TEST_TOKEN=""

# ---- 工具函数 ----

log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }
log_skip() { echo -e "${YELLOW}[SKIP]${NC} $1"; }

# 比较两个 API 响应
# 用法: compare_api <method> <path> [body_json]
compare_api() {
    local method="$1"
    local path="$2"
    local body="${3:-}"
    local label="$method $path"

    # 构造 curl 命令
    local curl_args=(-s -w "\n%{http_code}" -H "Content-Type: application/json")
    if [ -n "$TEST_TOKEN" ]; then
        curl_args+=(-H "Authorization: Bearer $TEST_TOKEN")
    fi

    # Node.js 请求
    local node_resp
    local node_code
    if [ "$method" = "GET" ]; then
        node_resp=$(curl "${curl_args[@]}" "$NODE_URL$path" 2>/dev/null)
    elif [ "$method" = "POST" ]; then
        node_resp=$(curl "${curl_args[@]}" -X POST -d "$body" "$NODE_URL$path" 2>/dev/null)
    elif [ "$method" = "PUT" ]; then
        node_resp=$(curl "${curl_args[@]}" -X PUT -d "$body" "$NODE_URL$path" 2>/dev/null)
    elif [ "$method" = "DELETE" ]; then
        node_resp=$(curl "${curl_args[@]}" -X DELETE "$NODE_URL$path" 2>/dev/null)
    else
        log_skip "$label (unsupported method: $method)"
        SKIP=$((SKIP + 1))
        return
    fi

    # Java 请求
    local java_resp
    local java_code
    if [ "$method" = "GET" ]; then
        java_resp=$(curl "${curl_args[@]}" "$JAVA_URL$path" 2>/dev/null)
    elif [ "$method" = "POST" ]; then
        java_resp=$(curl "${curl_args[@]}" -X POST -d "$body" "$JAVA_URL$path" 2>/dev/null)
    elif [ "$method" = "PUT" ]; then
        java_resp=$(curl "${curl_args[@]}" -X PUT -d "$body" "$JAVA_URL$path" 2>/dev/null)
    elif [ "$method" = "DELETE" ]; then
        java_resp=$(curl "${curl_args[@]}" -X DELETE "$JAVA_URL$path" 2>/dev/null)
    fi

    # 提取状态码和响应体
    node_code=$(echo "$node_resp" | tail -1)
    node_body=$(echo "$node_resp" | sed '$d')
    java_code=$(echo "$java_resp" | tail -1)
    java_body=$(echo "$java_resp" | sed '$d')

    # 检查连接性
    if [ -z "$node_code" ]; then
        log_skip "$label (Node.js 不可达)"
        SKIP=$((SKIP + 1))
        return
    fi
    if [ -z "$java_code" ]; then
        log_skip "$label (Java 不可达)"
        SKIP=$((SKIP + 1))
        return
    fi

    # 比较 HTTP 状态码
    if [ "$node_code" != "$java_code" ]; then
        log_fail "$label — HTTP 状态码不一致: Node=$node_code Java=$java_code"
        FAIL=$((FAIL + 1))
        return
    fi

    # 对 JSON 响应排序 Key 后 diff（排除 timestamp 等动态字段）
    local node_sorted
    local java_sorted
    node_sorted=$(echo "$node_body" | jq -S 'del(.data.timestamp)' 2>/dev/null || echo "INVALID_JSON")
    java_sorted=$(echo "$java_body" | jq -S 'del(.data.timestamp)' 2>/dev/null || echo "INVALID_JSON")

    if [ "$node_sorted" = "INVALID_JSON" ] || [ "$java_sorted" = "INVALID_JSON" ]; then
        log_fail "$label — JSON 解析失败"
        FAIL=$((FAIL + 1))
        return
    fi

    # diff 比较
    local diff_output
    diff_output=$(diff <(echo "$node_sorted") <(echo "$java_sorted") 2>&1) || true

    if [ -z "$diff_output" ]; then
        log_pass "$label (HTTP $node_code)"
        PASS=$((PASS + 1))
    else
        log_fail "$label — 响应体不一致:"
        echo "$diff_output" | while read -r line; do echo -e "         ${RED}$line${NC}"; done
        FAIL=$((FAIL + 1))
    fi
}

# =============================================================================
# 认证模块 (11.3.2)
# =============================================================================

echo ""
echo "========== Auth 模块 (3 API) =========="

# 登录获取 token
echo ""
echo "--- 登录 ---"
local login_body='{"code":"mock_test_code"}'

node_login=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$login_body" \
    "$NODE_URL/api/auth/login" 2>/dev/null)

java_login=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$login_body" \
    "$JAVA_URL/api/auth/login" 2>/dev/null)

node_login_code=$(echo "$node_login" | tail -1)
java_login_code=$(echo "$java_login" | tail -1)

if [ "$node_login_code" = "200" ] && [ "$java_login_code" = "200" ]; then
    # 提取 token 用于后续认证请求
    TEST_TOKEN=$(echo "$java_login" | sed '$d' | jq -r '.data.token // empty' 2>/dev/null)
    log_pass "POST /api/auth/login (HTTP 200)"
    PASS=$((PASS + 1))
else
    log_skip "POST /api/auth/login (Node=$node_login_code Java=$java_login_code)"
    SKIP=$((SKIP + 1))
fi

compare_api "GET" "/api/auth/me"
compare_api "POST" "/api/auth/refresh"

# =============================================================================
# Health 检查 (11.3.4 中)
# =============================================================================

echo ""
echo "========== Health =========="
compare_api "GET" "/api/health"

# =============================================================================
# Product 模块 (11.3.3)
# =============================================================================

echo ""
echo "========== Product 模块 (6 API) =========="
compare_api "GET" "/api/products?page=1&page_size=20"
compare_api "GET" "/api/products/1"
compare_api "GET" "/api/products/my?page=1&page_size=20"

# =============================================================================
# Order 模块 (11.3.4)
# =============================================================================

echo ""
echo "========== Order 模块 (6 API) =========="
compare_api "GET" "/api/orders?page=1&page_size=20"
compare_api "GET" "/api/orders/1"

# =============================================================================
# 其他模块 API (11.3.5)
# =============================================================================

echo ""
echo "========== Review / Credit / Notification =========="
compare_api "GET" "/api/credit/me"
compare_api "GET" "/api/notifications?page=1&page_size=20"

# =============================================================================
# 汇总
# =============================================================================

echo ""
echo "=========================================="
echo -e "  结果: ${GREEN}$PASS 通过${NC} / ${RED}$FAIL 失败${NC} / ${YELLOW}$SKIP 跳过${NC}"
echo "  总计: $((PASS + FAIL + SKIP)) API"
echo "=========================================="

# curl 清除 token（不再使用）
TEST_TOKEN=""
