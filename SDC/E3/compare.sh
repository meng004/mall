#!/usr/bin/env bash
# 同一组测试分别在基线（改前）和当前示例分支（改后）上运行，输出对照。
# 用法：SDC/E3/compare.sh <模块> <测试类,逗号分隔> [输出文件]
# 例：  SDC/E3/compare.sh mall-portal SDCE301Test SDC/E3/outputs/E3-01/evidence/compare.txt
# 需要 JDK 17：export JAVA_HOME=/opt/homebrew/opt/openjdk@17
set -uo pipefail
MODULE=$1 TESTS=$2 OUT=${3:-/dev/stdout}
BASE=baseline-dcaa93b3
ROOT=$(git rev-parse --show-toplevel)
HEAD_SHA=$(git rev-parse --short HEAD)
WT=$(mktemp -d)/base
MVN=(mvn -o -pl "$MODULE" -am -DskipTests=false -Dtest="$TESTS" -Dsurefire.failIfNoSpecifiedTests=false test)

git worktree add -q --detach "$WT" "$BASE"
# 只把示例新增或修改的测试文件带到基线；产品代码保持基线原样。
git diff --name-only "$BASE" HEAD -- '*/src/test/*' | while read -r f; do
  mkdir -p "$WT/$(dirname "$f")" && cp "$ROOT/$f" "$WT/$f"
done
# 实验环境配置不是产品代码，基线运行也需要它。
[ -d "$ROOT/SDC/environment" ] && mkdir -p "$WT/SDC" && cp -R "$ROOT/SDC/environment" "$WT/SDC/"

summary() { grep -E 'Tests run:.*(Fail|Err)' "$1" | grep -v ' in ' | tail -1; }
(cd "$WT" && "${MVN[@]}" >"$WT/../base.log" 2>&1); BASE_EXIT=$?
(cd "$ROOT" && "${MVN[@]}" >"$WT/../head.log" 2>&1); HEAD_EXIT=$?

{
  echo "测试：$MODULE $TESTS"
  echo "改前 $BASE exit=$BASE_EXIT  $(summary "$WT/../base.log")"
  grep -E '^\[ERROR\] +[A-Za-z0-9]+Test\.|expected:' "$WT/../base.log" | head -20
  echo "改后 $HEAD_SHA exit=$HEAD_EXIT  $(summary "$WT/../head.log")"
  grep -E '^\[ERROR\] +[A-Za-z0-9]+Test\.|expected:' "$WT/../head.log" | head -20
} | tee "$OUT"

git worktree remove --force "$WT"
