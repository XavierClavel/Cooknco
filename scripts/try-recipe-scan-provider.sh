#!/usr/bin/env bash
#
# Sends recipe photos to a vision provider exactly as the backend's recipe scan does, so a
# provider and a model can be judged before one is configured (docs/pending-setup.md, section 5).
#
#   BASE_URL=https://api.scaleway.ai/v1 API_KEY=... MODEL=... scripts/try-recipe-scan-provider.sh page.jpg [page2.jpg ...]
#
# The prompt is read out of RecipePhotoReader.kt rather than copied here, so this always tests
# the prompt that ships. Pages are scaled to 1600 px on the long edge, as the app sends them
# (macOS `sips`; HEIC is fine). Prints the time taken, the tokens billed, and the model's answer.
# DRY_RUN=1 prints the request without sending it. Needs jq.
set -euo pipefail
: "${BASE_URL:?set BASE_URL, up to and including /v1}" "${API_KEY:?set API_KEY}" "${MODEL:?set MODEL}"
[ $# -ge 1 ] || { echo "usage: $0 page.jpg [page2.jpg ...]" >&2; exit 1; }

root=$(cd "$(dirname "$0")/.." && pwd)
source_file="$root/backend/src/main/kotlin/com/xavierclavel/services/RecipePhotoReader.kt"
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT

sed -n '/val PROMPT = """/,/""".trimIndent()/p' "$source_file" | sed '1d;$d' | sed 's/^            //' > "$work/prompt.txt"
[ -s "$work/prompt.txt" ] || { echo "could not read the prompt out of $source_file" >&2; exit 1; }

images='[]'
for page in "$@"; do
  sips -s format jpeg -Z 1600 "$page" --out "$work/page.jpg" >/dev/null
  base64 -i "$work/page.jpg" | tr -d '\n' > "$work/page.b64"
  images=$(jq --rawfile b "$work/page.b64" \
    '. + [{type: "image_url", image_url: {url: ("data:image/jpeg;base64," + $b)}}]' <<<"$images")
done
intro=$([ $# -eq 1 ] && echo "The recipe:" || echo "The recipe, in $# pages:")

jq -n --arg model "$MODEL" --rawfile prompt "$work/prompt.txt" --arg intro "$intro" --argjson images "$images" \
  --argjson json_mode "${JSON_MODE:-true}" '{
    model: $model, temperature: 0, max_tokens: 4096,
    messages: [
      {role: "system", content: ($prompt | rtrimstr("\n"))},
      {role: "user", content: ([{type: "text", text: $intro}] + $images)}
    ]} + (if $json_mode then {response_format: {type: "json_object"}} else {} end)' > "$work/body.json"

if [ -n "${DRY_RUN:-}" ]; then
  jq '.messages[1].content |= map(if .type == "image_url" then "<image>" else . end)' "$work/body.json"
  exit 0
fi

start=$(date +%s)
curl -sS "${BASE_URL%/}/chat/completions" \
  -H "Authorization: Bearer $API_KEY" -H "Content-Type: application/json" \
  -d @"$work/body.json" > "$work/answer.json"
echo "seconds: $(( $(date +%s) - start ))"
jq -c '.usage // "no usage reported"' "$work/answer.json"
jq -r '.choices[0].message.content // .' "$work/answer.json"
