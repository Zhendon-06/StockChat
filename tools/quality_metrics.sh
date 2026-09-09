#!/usr/bin/env bash
# 代码质量自检：函数长度、文件长度、KDoc 覆盖、硬编码颜色、危险模式
set -euo pipefail
cd "$(dirname "$0")/.."
SRC_LIST=$(mktemp)
trap 'rm -f "$SRC_LIST"' EXIT
find shared/src/commonMain/kotlin kuikly-chart/src/commonMain -name '*.kt' > "$SRC_LIST"

echo "== 函数 >= 100 行 =="
while read -r f; do
  awk -v F="$f" '
    /^[[:space:]]*(private |internal |public |override |suspend |inline |protected )*fun / && depth==0 { start=NR; name=$0; sub(/^[[:space:]]*/,"",name); sub(/\(.*/,"",name) }
    { o=gsub(/\{/,"{"); c=gsub(/\}/,"}"); depth+=o-c;
      if(start && depth==0 && (o||c)) { len=NR-start+1; if(len>=100) printf "%4d %s:%d %s\n", len, F, start, name; start=0 } }' "$f"
done < "$SRC_LIST" | sort -rn

echo "== 文件 > 500 行 =="
xargs wc -l < "$SRC_LIST" | awk '$1>500 && $2!="total"' | sort -rn

echo "== 顶层类型 KDoc 覆盖 =="
for dir in shared/src/commonMain/kotlin kuikly-chart/src/commonMain; do
  awk -v D="$dir" 'FNR==1{prev=""}
    /^(internal |public )?(sealed |data |abstract |open |enum |value )*(class|object|interface) / { total++; if (prev ~ /\*\//) doc++ }
    { if ($0 !~ /^[[:space:]]*$/) prev=$0 }
    END{ printf "%s: %d/%d\n", D, doc, total }' $(find "$dir" -name '*.kt')
done

echo "== ui 目录硬编码颜色（Theme 文件之外） =="
grep -rnoE '0x[Ff]{2}[0-9A-Fa-f]{6}' shared/src/commonMain/kotlin/com/guet/liang/stockchat/ui \
  | grep -vE 'StockChatTheme\.kt|SettingsTheme\.kt' | wc -l || true

echo "== 危险模式 =="
echo "!!            : $(xargs cat < "$SRC_LIST" | grep -c '!!' || true)"
echo "catch Throwable: $(xargs grep -nE 'catch \(\w+: (Throwable|Exception)\)' < "$SRC_LIST" | wc -l || true)"
echo "println       : $(xargs grep -n 'println(' < "$SRC_LIST" | wc -l || true)"
echo "TODO/FIXME    : $(xargs grep -nE 'TODO|FIXME|HACK' < "$SRC_LIST" | wc -l || true)"

echo "== ui 直接 import data 层的文件数 =="
grep -rl 'import com.guet.liang.stockchat.data' shared/src/commonMain/kotlin/com/guet/liang/stockchat/ui | wc -l || true
