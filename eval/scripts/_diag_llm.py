# 临时诊断9：对比 baseline vs topk10 的 contexts 数量分布
import json
from collections import Counter
from pathlib import Path

EVAL_ROOT = Path(__file__).resolve().parents[1]

for exp in ["baseline", "topk10"]:
    f = EVAL_ROOT / "results" / exp / "results.jsonl"
    if not f.exists():
        print(f"{exp}: 文件不存在")
        continue
    rows = [json.loads(l) for l in f.read_text(encoding="utf-8").splitlines() if l.strip()]
    dist = Counter(len(r["contexts"]) for r in rows)
    zero = sum(1 for r in rows if not r["contexts"])
    print(f"[{exp}] 题数={len(rows)}, contexts=0 的题={zero}, 分布={dict(sorted(dist.items()))}")
    print(f"      平均 contexts/题 = {sum(len(r['contexts']) for r in rows) / len(rows):.1f}")
    print()
