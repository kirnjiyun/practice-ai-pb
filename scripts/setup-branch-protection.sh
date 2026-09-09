#!/usr/bin/env bash
# =============================================================================
# GitHub 브랜치 보호 규칙 설정
#
# 사전 조건:
#   brew install gh
#   gh auth login          # ← 본인이 직접 인증해야 합니다
#
# 실행:
#   ./scripts/setup-branch-protection.sh
#
# 멱등합니다. 여러 번 실행해도 같은 상태로 수렴합니다.
# =============================================================================
set -euo pipefail

REPO="${REPO:-kirnjiyun/practice-ai-pb}"

# 항상 실행되는 유일한 워크플로. 다른 CI 는 path 필터가 걸려 있어
# 특정 PR 에서 아예 실행되지 않으므로 required check 로 쓰면 영원히 pending 이 된다.
REQUIRED_CHECK="PR Guard"

# 1인 프로젝트이므로 승인 수는 0. 본인 PR 은 스스로 승인할 수 없어
# 1 로 두면 admin 우회 없이는 머지가 불가능해진다.
# 협업자가 생기면 아래 값을 1 로 올린다.
REQUIRED_APPROVALS=0

# 관리자(저장소 소유자)에게도 규칙을 강제한다.
# false 로 두면 소유자의 push 가 "Bypassed rule violations" 로 그냥 통과해
# 보호 규칙이 경고 문구에 그친다.
# 긴급 상황에서는 아래로 잠시 껐다가 되돌린다:
#   gh api -X DELETE repos/$REPO/branches/main/protection/enforce_admins
#   gh api -X POST   repos/$REPO/branches/main/protection/enforce_admins
ENFORCE_ADMINS=true

command -v gh >/dev/null 2>&1 || {
  echo "gh CLI 가 필요합니다:  brew install gh && gh auth login" >&2
  exit 1
}
gh auth status >/dev/null 2>&1 || {
  echo "GitHub 인증이 필요합니다:  gh auth login" >&2
  exit 1
}

echo "대상 저장소: $REPO"
echo

# ── main: 가장 엄격 ──────────────────────────────────────────────────────────
echo "[1/3] main 브랜치 보호 설정"
gh api -X PUT "repos/$REPO/branches/main/protection" \
  -H "Accept: application/vnd.github+json" \
  --input - <<JSON
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["$REQUIRED_CHECK"]
  },
  "enforce_admins": $ENFORCE_ADMINS,
  "required_pull_request_reviews": {
    "required_approving_review_count": $REQUIRED_APPROVALS,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "require_last_push_approval": false
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true,
  "lock_branch": false,
  "allow_fork_syncing": false
}
JSON
echo "  ✓ main"

# ── develop: 통합 브랜치. main 보다 한 단계 완화 ─────────────────────────────
echo "[2/3] develop 브랜치 보호 설정"
gh api -X PUT "repos/$REPO/branches/develop/protection" \
  -H "Accept: application/vnd.github+json" \
  --input - <<JSON
{
  "required_status_checks": {
    "strict": false,
    "contexts": ["$REQUIRED_CHECK"]
  },
  "enforce_admins": $ENFORCE_ADMINS,
  "required_pull_request_reviews": {
    "required_approving_review_count": 0,
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false,
    "require_last_push_approval": false
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": false,
  "lock_branch": false,
  "allow_fork_syncing": false
}
JSON
echo "  ✓ develop"

# ── 저장소 머지 정책: Squash 만 허용 (히스토리 선형 유지) ────────────────────
echo "[3/3] 저장소 머지 정책"
gh api -X PATCH "repos/$REPO" \
  -H "Accept: application/vnd.github+json" \
  -F allow_squash_merge=true \
  -F allow_merge_commit=false \
  -F allow_rebase_merge=false \
  -F delete_branch_on_merge=true \
  -F allow_auto_merge=true \
  -f squash_merge_commit_title=PR_TITLE \
  -f squash_merge_commit_message=PR_BODY \
  >/dev/null
echo "  ✓ Squash only · 머지 후 브랜치 자동 삭제"

echo
echo "완료. 현재 설정 확인:"
gh api "repos/$REPO/branches/main/protection" \
  --jq '{
    required_checks: .required_status_checks.contexts,
    strict: .required_status_checks.strict,
    approvals: .required_pull_request_reviews.required_approving_review_count,
    enforce_admins: .enforce_admins.enabled,
    linear_history: .required_linear_history.enabled,
    force_push: .allow_force_pushes.enabled,
    conversation_resolution: .required_conversation_resolution.enabled
  }'
