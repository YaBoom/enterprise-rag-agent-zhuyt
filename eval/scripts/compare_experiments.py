"""
步骤3：A/B 对比两个实验，生成对比报告
================================================================
教学点（实验设计铁律）：
  1. 一次只改一个变量：比如 baseline(topK=5) vs topK=10，
     改完检索参数后只重跑 collect + evaluate，其他都不动；
  2. 同一测试集、同一 judge 模型，否则结果不可比；
  3. 指标变化要结合 bad case 看：平均分提升 0.02，但某类题
     大幅退化，可能是过拟合了某一类问题。

用法：
    python scripts/compare_experiments.py --baseline baseline --candidate topk10
    报告输出到 results/compare_baseline_vs_topk10.md
================================================================
"""
import argparse
import json
import sys
from pathlib import Path

import pandas as pd

EVAL_ROOT = Path(__file__).resolve().parents[1]


def load_agg(exp: str) -> dict:
    f = EVAL_ROOT / "results" / exp / "aggregate.json"
    if not f.exists():
        sys.exit(f"[ERROR] 缺少 {f}，先运行 evaluate_ragas.py")
    return json.loads(f.read_text(encoding="utf-8"))


def load_per_sample(exp: str) -> pd.DataFrame:
    f = EVAL_ROOT / "results" / exp / "per_sample_scores.csv"
    return pd.read_csv(f)


def main():
    parser = argparse.ArgumentParser(description="A/B 对比")
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--candidate", required=True)
    args = parser.parse_args()

    b_agg, c_agg = load_agg(args.baseline), load_agg(args.candidate)
    b_df, c_df = load_per_sample(args.baseline), load_per_sample(args.candidate)

    # ---------- 1. 平均分对比 ----------
    lines = [f"# A/B 对比：{args.baseline} vs {args.candidate}", ""]
    lines.append("## 平均分对比")
    lines.append("| 指标 | baseline | candidate | Δ | 结论 |")
    lines.append("|---|---|---|---|---|")
    for metric in b_agg:
        delta = c_agg.get(metric, 0) - b_agg[metric]
        verdict = "提升" if delta > 0.01 else ("退化" if delta < -0.01 else "基本持平")
        lines.append(f"| {metric} | {b_agg[metric]:.4f} | {c_agg.get(metric, 0):.4f} | {delta:+.4f} | {verdict} |")

    # ---------- 2. 每题级对比（bad case 分析） ----------
    metric_cols = [c for c in b_df.columns if c not in ("question", "category")]
    merged = b_df[["question", "category"] + metric_cols].merge(
        c_df[["question"] + metric_cols],
        on="question",
        suffixes=(f"_{args.baseline}", f"_{args.candidate}"),
    )
    merged["faithfulness_delta"] = merged[f"faithfulness_{args.candidate}"] - merged[f"faithfulness_{args.baseline}"]

    lines.append("")
    lines.append("## 变化最大的题目（按 faithfulness Δ 排序）")
    lines.append("### 提升 Top 5")
    lines.append("| 问题 | 类别 | baseline | candidate | Δ |")
    lines.append("|---|---|---|---|---|")
    for _, row in merged.nlargest(5, "faithfulness_delta").iterrows():
        lines.append(f"| {row['question'][:40]} | {row['category']} | "
                     f"{row[f'faithfulness_{args.baseline}']:.2f} | {row[f'faithfulness_{args.candidate}']:.2f} | {row['faithfulness_delta']:+.2f} |")

    lines.append("")
    lines.append("### 退化 Top 5（优先排查）")
    lines.append("| 问题 | 类别 | baseline | candidate | Δ |")
    lines.append("|---|---|---|---|---|")
    for _, row in merged.nsmallest(5, "faithfulness_delta").iterrows():
        lines.append(f"| {row['question'][:40]} | {row['category']} | "
                     f"{row[f'faithfulness_{args.baseline}']:.2f} | {row[f'faithfulness_{args.candidate}']:.2f} | {row['faithfulness_delta']:+.2f} |")

    # ---------- 3. 按类别汇总 ----------
    lines.append("")
    lines.append("## 按题目类别汇总")
    lines.append("| 类别 | 题目数 | baseline avg | candidate avg |")
    lines.append("|---|---|---|---|")
    for cat, grp in merged.groupby("category"):
        b_avg = grp[f"faithfulness_{args.baseline}"].mean()
        c_avg = grp[f"faithfulness_{args.candidate}"].mean()
        lines.append(f"| {cat} | {len(grp)} | {b_avg:.3f} | {c_avg:.3f} |")

    report = "\n".join(lines)
    out_file = EVAL_ROOT / "results" / f"compare_{args.baseline}_vs_{args.candidate}.md"
    out_file.write_text(report, encoding="utf-8")
    print(report)
    print(f"\n[Compare] 报告已生成 -> {out_file}")


if __name__ == "__main__":
    main()
