#!/bin/bash
# ═══════════════════════════════════════════════════════════
#  publish.sh —— 一键发布到 GitHub + LSPosed 仓库
#
#  用法：
#    bash publish.sh <GITHUB_TOKEN>
#
#  环境变量（可选）：
#    GH_USER   默认 Stars520
#    LSP_USER  默认 xposed-modules-repo 成员账号，通常与 GH_USER 相同
#
#  脚本会做四件事：
#    1. 创建/更新主仓库   <GH_USER>/PydroidCN
#    2. 推送源码 + 预编译 APK
#    3. 创建 LSPosed 仓库 <GH_USER>/com.hsqs.pydroidcn（包名命名）
#    4. 从主仓库 fork 一份内容推进 LSPosed 仓库，并向官方仓库发 PR
# ═══════════════════════════════════════════════════════════
set -u

TOKEN="${1:-}"
if [ -z "$TOKEN" ]; then
  echo "用法: bash publish.sh <GITHUB_TOKEN>"
  exit 1
fi

GH_USER="${GH_USER:-Stars520}"
GH_API="https://api.github.com"
AUTH=(-H "Authorization: token $TOKEN" -H "Accept: application/vnd.github+json" -H "User-Agent: PydroidCN-publish")

echo "════════════════════════════════════"
echo " 发布 PydroidCN"
echo "  账号: $GH_USER"
echo "════════════════════════════════════"

# ── 0. 校验证书 ──
echo "[0/5] 校验 token…"
ME=$(curl -s -m 20 "${AUTH[@]}" "$GH_API/user" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("login",""))')
if [ -z "$ME" ]; then
  echo "✗ token 无效"
  exit 1
fi
echo "✓ 已认证为 $ME"
GH_USER="$ME"

# ── 1. 创建主仓库 ──
echo "[1/5] 创建仓库 $GH_USER/PydroidCN …"
CODE=$(curl -s -o /tmp/gh_create.json -w '%{http_code}' -m 30 "${AUTH[@]}" \
  -X POST "$GH_API/user/repos" \
  -d '{"name":"PydroidCN","description":"Pydroid 3 界面汉化 LSPosed 模块","private":false,"has_issues":true,"has_wiki":false}')
if [ "$CODE" = "201" ]; then
  echo "✓ 仓库已创建"
elif [ "$CODE" = "422" ]; then
  echo "· 仓库已存在，继续"
else
  echo "✗ 创建失败 HTTP $CODE"
  cat /tmp/gh_create.json
  exit 1
fi

# ── 2. 推送主仓库 ──
echo "[2/5] 推送源码…"
cd /root/pydroid_repo || exit 1
git remote remove origin 2>/dev/null
git remote add origin "https://${GH_USER}:${TOKEN}@github.com/${GH_USER}/PydroidCN.git"
git push -u origin main --force 2>&1 | tail -5
if [ "${PIPESTATUS[0]}" != "0" ]; then
  echo "✗ 推送失败"
  exit 1
fi
echo "✓ 已推送到 https://github.com/$GH_USER/PydroidCN"

# ── 3. 创建 LSPosed 仓库（以包名命名） ──
echo "[3/5] 创建 LSPosed 仓库 $GH_USER/com.hsqs.pydroidcn …"
CODE=$(curl -s -o /tmp/gh_lsp.json -w '%{http_code}' -m 30 "${AUTH[@]}" \
  -X POST "$GH_API/user/repos" \
  -d '{"name":"com.hsqs.pydroidcn","description":"Pydroid 3 界面汉化 — LSPosed 仓库提交用","private":false,"has_issues":false,"has_wiki":false}')
if [ "$CODE" = "201" ]; then
  echo "✓ LSPosed 仓库已创建"
elif [ "$CODE" = "422" ]; then
  echo "· LSPosed 仓库已存在，继续"
else
  echo "✗ 创建失败 HTTP $CODE"
  cat /tmp/gh_lsp.json
fi

# ── 4. 准备 LSPosed 仓库内容 ──
echo "[4/5] 组装 LSPosed 仓库…"
LSPDIR=/root/pydroid_lsp
rm -rf "$LSPDIR"
mkdir -p "$LSPDIR"
cp /root/pydroid_repo/PydroidCN.apk "$LSPDIR/"
cp /storage/emulated/0/java66666666/PydroidCN/LSPOSED_README.md "$LSPDIR/README.md" 2>/dev/null \
  || cp /root/pydroid_repo/README.md "$LSPDIR/README.md"

cd "$LSPDIR" || exit 1
git init -q
git config user.name "$GH_USER"
git config user.email "${GH_USER}@users.noreply.github.com"
git add -A
git commit -q -m "Pydroid 3 界面汉化 v1.0

594 条资源字符串 + 523 条运行时文本
作用域: ru.iiec.pydroid3
实测版本: 8.4_arm64 (1131)"
git branch -M main

echo "[5/5] 推送 LSPosed 仓库…"
git remote add origin "https://${GH_USER}:${TOKEN}@github.com/${GH_USER}/com.hsqs.pydroidcn.git"
git push -u origin main --force 2>&1 | tail -5

echo "════════════════════════════════════"
echo " 完成"
echo "  主仓库:   https://github.com/$GH_USER/PydroidCN"
echo "  LSP 仓库: https://github.com/$GH_USER/com.hsqs.pydroidcn"
echo "════════════════════════════════════"
echo
echo "最后一步需你手动操作（GitHub 不允许 API 直接跨仓库 fork PR）："
echo "  1. 打开 https://github.com/Xposed-Modules-Repo/submission"
echo "  2. 点 New issue，标题填: New Module: com.hsqs.pydroidcn"
echo "  3. 内容填: 模块仓库地址 https://github.com/$GH_USER/com.hsqs.pydroidcn"
echo "  或者直接 fork https://github.com/Xposed-Modules-Repo/com.hsqs.pydroidcn"
echo "  然后把 LSP 仓库内容推过去并发 PR。"