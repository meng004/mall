#!/usr/bin/env bash
# 示例改前/改后对照：同一组测试先在基线（改前）运行，再在当前示例分支（改后）运行。
# 用法：SDC/compare.sh <示例编号>        例：SDC/compare.sh E3-01
# 参数取自 SDC/<实验>/outputs/<编号>/compare.env：
#   MODULE=mall-portal        被测 Maven 模块
#   TESTS=SDCE301Test          测试类，逗号分隔
#   EXPECT_BASE=fail           改前预期：fail 测试运行且失败；pass 测试通过；absent 被测功能尚不存在（编译失败）
# 改后必须通过。结果写入同目录 evidence/compare.txt；与预期不符时退出码为 1。
# 需要 JDK 17；默认离线（-o），设 MVN_OFFLINE= 允许下载依赖。
set -uo pipefail
ID=$1
DIR=SDC/${ID%%-*}/outputs/$ID
ROOT=$(git rev-parse --show-toplevel)
source "$ROOT/$DIR/compare.env"
BASE=baseline-dcaa93b3
# 记录最后一次改动代码的提交；补交证据不会改变它。
HEAD_SHA=$(git log -1 --format=%h -- . ':!SDC/*/outputs')
TMP=$(mktemp -d) WT=$(mktemp -d)/base
MVN=(mvn -B ${MVN_OFFLINE--o} -pl "$MODULE" -am -DskipTests=false -Dtest="$TESTS" -Dsurefire.failIfNoSpecifiedTests=false test)

git worktree add -q --detach "$WT" "$BASE"
# 只把示例新增或修改的测试文件带到基线；产品代码保持基线原样。实验环境配置也带过去。
git diff --name-only "$BASE" HEAD -- '*/src/test/*' | while read -r f; do
  mkdir -p "$WT/$(dirname "$f")" && cp "$ROOT/$f" "$WT/$f"
done
[ -d "$ROOT/SDC/environment" ] && mkdir -p "$WT/SDC" && cp -R "$ROOT/SDC/environment" "$WT/SDC/"

(cd "$WT" && "${MVN[@]}" >"$TMP/base.log" 2>&1); BASE_EXIT=$?
(cd "$ROOT" && "${MVN[@]}" >"$TMP/head.log" 2>&1); HEAD_EXIT=$?
git worktree remove --force "$WT"

# 结果：pass / fail（测试运行但失败）/ absent（编译失败）/ error（其他原因，如未找到测试）
outcome() {
  if [ "$2" = 0 ] && grep -qE "Tests run: [1-9]" "$1"; then echo pass
  elif grep -qE 'Tests run:.*(Failures|Errors): [1-9]' "$1"; then echo fail
  elif grep -q 'COMPILATION ERROR' "$1"; then echo absent
  else echo error; fi
}
summary() { grep -E 'Tests run:.*(Fail|Err)' "$1" | grep -v ' in ' | tail -1; }
details() { grep -E '^\[ERROR\] +[A-Za-z0-9]+Test\.|^\[ERROR\] /.*\.java:\[' "$1" | head -20; }
B=$(outcome "$TMP/base.log" $BASE_EXIT) H=$(outcome "$TMP/head.log" $HEAD_EXIT)
[ "$B" = "$EXPECT_BASE" ] && [ "$H" = pass ] && VERDICT=符合 || VERDICT=不符合

mkdir -p "$ROOT/$DIR/evidence"
{
  echo "示例：${ID}  模块：${MODULE}  测试：${TESTS}"
  echo "改前 ${BASE}：${B}（预期 ${EXPECT_BASE}）  $(summary "$TMP/base.log")"
  details "$TMP/base.log"
  echo "改后 ${HEAD_SHA}：${H}（预期 pass）  $(summary "$TMP/head.log")"
  details "$TMP/head.log"
  echo "结论：${VERDICT}"
} | tee "$ROOT/$DIR/evidence/compare.txt"
[ "$VERDICT" = 符合 ]
