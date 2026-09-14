#!/usr/bin/env bash
# 按时间窗口清理旧 Release：
#   不到 1 个月      全部保留（对应「近一个月保留每次编译」）
#   1 ~ 2 个月       每个自然周只保留该周最后一个
#   2 ~ 12 个月      每个自然月只保留该月最后一个
#   超过 12 个月     删除
#
# 环境变量：
#   REPO      目标仓库，默认 $GITHUB_REPOSITORY
#   DRY_RUN   设为 1 只打印不删除
set -euo pipefail

REPO="${REPO:-${GITHUB_REPOSITORY:-}}"
DRY_RUN="${DRY_RUN:-0}"

if [ -z "$REPO" ]; then
  echo "❌ 需要 REPO 或 GITHUB_REPOSITORY" >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "📦 仓库: $REPO  (DRY_RUN=$DRY_RUN)"

gh release list --repo "$REPO" --limit 1000 \
  --json tagName,publishedAt,createdAt \
  --jq '.[] | [.tagName, (.publishedAt // .createdAt)] | @tsv' > "$TMP/all.tsv" || true

if [ ! -s "$TMP/all.tsv" ]; then
  echo "没有找到任何 Release，结束。"
  exit 0
fi

# 按发布时间从旧到新排序
sort -k2 "$TMP/all.tsv" > "$TMP/sorted.tsv"

now="$(date -u +%s)"
: > "$TMP/classify.tsv"

while IFS=$'\t' read -r tag ts; do
  [ -z "${tag:-}" ] && continue
  epoch="$(date -u -d "$ts" +%s 2>/dev/null || echo 0)"
  age=$(( (now - epoch) / 86400 ))
  if [ "$age" -lt 30 ]; then
    printf '%s\tALL\t-\n' "$tag" >> "$TMP/classify.tsv"
  elif [ "$age" -lt 60 ]; then
    printf '%s\tWEEK\t%s\n' "$tag" "$(date -u -d "$ts" +%G%V)" >> "$TMP/classify.tsv"
  elif [ "$age" -lt 365 ]; then
    printf '%s\tMONTH\t%s\n' "$tag" "$(date -u -d "$ts" +%Y%m)" >> "$TMP/classify.tsv"
  else
    printf '%s\tOLD\t-\n' "$tag" >> "$TMP/classify.tsv"
  fi
done < "$TMP/sorted.tsv"

# 同一周/同一月里只保留最后出现（最新）的那一个；ALL 全部保留；OLD 全部删除
awk -F'\t' '
  $2=="ALL"   { print $1 > "'"$TMP"'/keep.txt" }
  $2=="WEEK"  { wk[$3]=$1 }
  $2=="MONTH" { mo[$3]=$1 }
  $2=="OLD"   { print $1 > "'"$TMP"'/del.txt" }
  END { for (k in wk) print wk[k] >> "'"$TMP"'/keep.txt"
        for (k in mo) print mo[k] >> "'"$TMP"'/keep.txt" }
' "$TMP/classify.tsv"

sort -u "$TMP/keep.txt" 2>/dev/null > "$TMP/keep_sorted.txt" || : > "$TMP/keep_sorted.txt"
sort -u "$TMP/del.txt"  2>/dev/null > "$TMP/del_sorted.txt"  || : > "$TMP/del_sorted.txt"

# 双重保险：绝不删除保留名单里的 tag
if [ -s "$TMP/keep_sorted.txt" ]; then
  comm -23 "$TMP/del_sorted.txt" "$TMP/keep_sorted.txt" > "$TMP/del_final.txt"
else
  cp "$TMP/del_sorted.txt" "$TMP/del_final.txt"
fi

keep_n=$(grep -c . "$TMP/keep_sorted.txt" 2>/dev/null || echo 0)
del_n=$(grep -c . "$TMP/del_final.txt" 2>/dev/null || echo 0)
echo "✅ 计划保留 $keep_n 个，删除 $del_n 个"

if [ "$del_n" -gt 0 ]; then
  while IFS= read -r tag; do
    [ -z "$tag" ] && continue
    if [ "$DRY_RUN" = "1" ]; then
      echo "   [dry-run] 将删除 $tag"
    else
      echo "   🗑️ 删除 $tag"
      gh release delete "$tag" --repo "$REPO" --yes --cleanup-tag || echo "   （删除 $tag 失败，跳过）"
    fi
  done < "$TMP/del_final.txt"
fi

echo "完成。"
