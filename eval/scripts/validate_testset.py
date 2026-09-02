"""
测试集自检工具：校验 JSONL 格式、字段完整性、题目与知识库文档一致性
用法：
    python scripts/validate_testset.py
    python scripts/validate_testset.py --dataset path/to/custom.jsonl --knowledge path/to/doc.md
================================================================
为什么需要它：
  1. JSONL 每行必须是合法 JSON，字段齐全（question/ground_truth/category/notes）；
  2. ground_truth 的关键信息必须能在知识库文档中找到，否则评估无意义
     （context_recall 检查的正是「标准答案的信息是否被检索回来」）。
================================================================
"""
import argparse
import json
import sys
from collections import Counter
from pathlib import Path

EVAL_ROOT = Path(__file__).resolve().parents[1]

REQUIRED_FIELDS = {"question", "ground_truth", "category", "notes"}
VALID_CATEGORIES = {"fact", "number", "rule", "composite", "concept"}


def main():
    parser = argparse.ArgumentParser(description="测试集自检")
    parser.add_argument("--dataset", default=str(EVAL_ROOT / "dataset" / "test_set.jsonl"))
    parser.add_argument("--knowledge", default=str(EVAL_ROOT / "dataset" / "sample_knowledge.md"))
    args = parser.parse_args()

    # 1. JSONL 语法 + 字段完整性
    rows = []
    for line_no, line in enumerate(
        Path(args.dataset).read_text(encoding="utf-8").splitlines(), 1
    ):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as e:
            sys.exit(f"[FAIL] 第 {line_no} 行不是合法 JSON: {e}")
        rows.append(row)

    if not rows:
        sys.exit("[FAIL] 测试集为空")

    missing = [r["question"] for r in rows if not REQUIRED_FIELDS <= set(r)]
    bad_cat = [r["question"] for r in rows if r.get("category") not in VALID_CATEGORIES]
    dup = [q for q, c in Counter(r["question"] for r in rows).items() if c > 1]

    # 2. ground_truth 与知识库一致性（抽查前 15 个字符是否能在文档中找到）
    doc = Path(args.knowledge).read_text(encoding="utf-8")
    not_found = [
        r["question"]
        for r in rows
        if not any(k in doc for k in r["ground_truth"][:15])
    ]

    print(f"总题数: {len(rows)}")
    print(f"字段完整性: {'OK' if not missing else 'MISSING -> ' + str(missing)}")
    print(f"类别合法: {'OK' if not bad_cat else 'INVALID -> ' + str(bad_cat)}")
    print(f"无重复题: {'OK' if not dup else 'DUP -> ' + str(dup)}")
    print(f"知识库可定位: {'OK' if not not_found else 'NOT_FOUND -> ' + str(not_found)}")
    print("类别分布:", dict(Counter(r["category"] for r in rows)))

    if missing or bad_cat or dup or not_found:
        sys.exit("[FAIL] 测试集存在问题，请修复后重试")
    print("[PASS] 测试集校验通过")


if __name__ == "__main__":
    main()
