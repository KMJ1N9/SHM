#!/bin/bash
# 校园二手交易 — 压力测试基线脚本
# 前提：服务已在 localhost:3000 运行
# 安装 ab（若未安装）：apt install apache2-utils

set -euo pipefail

BASE_URL="http://localhost:3000"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
OUTDIR="/tmp/bench-${TIMESTAMP}"
mkdir -p "$OUTDIR"

echo "===== 校园二手交易 — 压力测试基线 ====="
echo "目标: $BASE_URL"
echo "输出: $OUTDIR"
echo "开始时间: $(date)"
echo ""

# 1. 健康检查（高频轻量）
echo "=== [1/4] 健康检查 (5000 req / 200 concurrency) ==="
ab -n 5000 -c 200 "$BASE_URL/api/health" 2>&1 | tee "$OUTDIR/health.txt"
echo ""

# 2. 商品列表（高频分页）
echo "=== [2/4] 商品列表 (1000 req / 50 concurrency) ==="
ab -n 1000 -c 50 "$BASE_URL/api/products?page=1&pageSize=20" 2>&1 | tee "$OUTDIR/products-list.txt"
echo ""

# 3. 商品详情（主键查询）
echo "=== [3/4] 商品详情 (2000 req / 100 concurrency) ==="
ab -n 2000 -c 100 "$BASE_URL/api/products/1" 2>&1 | tee "$OUTDIR/product-detail.txt"
echo ""

# 4. 搜索（FULLTEXT）
echo "=== [4/4] 搜索 (500 req / 30 concurrency) ==="
ab -n 500 -c 30 "$BASE_URL/api/products?keyword=教材&page=1&pageSize=20" 2>&1 | tee "$OUTDIR/search.txt"
echo ""

echo "===== 完成 ====="
echo "结束时间: $(date)"
echo "结果文件: $OUTDIR"
echo ""
echo "关键指标提取："
for f in "$OUTDIR"/*.txt; do
  echo "--- $(basename "$f") ---"
  grep -E "Requests per second|Time per request.*mean|Failed requests|Percentage of the requests" "$f" || true
  echo ""
done
