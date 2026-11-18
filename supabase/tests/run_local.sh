#!/usr/bin/env bash
# 在本机 PostgreSQL 上模拟 Supabase（anon / authenticated / service_role、auth.uid()），
# 依次建表（schema → 002 → 003 → 004 三段），再跑 attack.sql：正常操作都应成功，攻击都应被挡住（结果里不应出现 "(bad)"）。
# 用法：sudo -u postgres bash supabase/tests/run_local.sh
set -euo pipefail
cd "$(dirname "$0")/.."
psql -q -c "select pg_terminate_backend(pid) from pg_stat_activity where datname='hwtest'" >/dev/null
psql -q -v ON_ERROR_STOP=1 -f tests/mock_supabase.sql
for f in schema.sql 002_public_preview.sql 003_trading.sql 004_parts/004_part1.sql 004_parts/004_part2.sql 004_parts/004_part3.sql; do
  psql -q -v ON_ERROR_STOP=1 -d hwtest -f "$f" >/dev/null
done
out=$(psql -q -d hwtest -f tests/attack.sql 2>&1 | grep " = ")
echo "$out"
if echo "$out" | grep -q "(bad)"; then echo "有攻击没挡住"; exit 1; fi
echo "全部通过"
