"""
步骤1：跑测试集，采集 RAG 系统的回答与检索上下文
================================================================
原理：评估 RAG 需要三类数据——问题、系统生成的答案、检索到的上下文片段。
      RAGAS 的 faithfulness 只看「答案 vs 上下文」，context_recall 只看
      「上下文 vs 标准答案」。所以必须先让系统对每题真实跑一遍，把
      answer 和 contexts 存下来，再交给 RAGAS 打分。

用法：
    python scripts/collect_results.py --experiment baseline
    python scripts/collect_results.py --experiment hybrid_topk10

A/B 实验控制（通过环境变量，不改脚本）：
    EVAL_STRATEGY=VECTOR|HYBRID   检索策略（当前项目只有 VECTOR 生效）
    EVAL_TOP_K=5                  召回数量
    EVAL_RERANK=true|false        是否重排序
================================================================
"""
import argparse
import json
import os
import sys
import time
from pathlib import Path

import requests

# 项目根目录（读 .env）与 eval 目录（数据/结果）
PROJECT_ROOT = Path(__file__).resolve().parents[2]
EVAL_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT))

try:
    from dotenv import load_dotenv
    load_dotenv(PROJECT_ROOT / ".env")   # 复用项目 .env 中的配置
except ImportError:
    print("[WARN] 未安装 python-dotenv，跳过 .env 加载（不影响本脚本）")

RAG_API = os.getenv("RAG_API_BASE", "http://localhost:8089") + "/api/v1/rag/query"
STRATEGY = os.getenv("EVAL_STRATEGY", "VECTOR")
TOP_K = int(os.getenv("EVAL_TOP_K", "5"))
RERANK = os.getenv("EVAL_RERANK", "true").lower() == "true"


def collect_one(sample: dict) -> dict:
    """对单个问题调用项目 RAG 接口，返回评估样本"""
    resp = requests.post(
        RAG_API,
        json={
            "question": sample["question"],
            "retrievalParams": {
                "topK": TOP_K,
                "strategy": STRATEGY,
                "enableRerank": RERANK,
            },
        },
        timeout=240,
    )
    resp.raise_for_status()
    body = resp.json()
    if body.get("code") != 0:
        raise RuntimeError(f"接口返回错误: {body.get('message')}")

    data = body.get("data", {})

    # 关键点：contexts 必须是完整片段。
    # 后端改造后 sources[].content 是完整 chunk；改造前只能退回截断的 snippet（评估会偏低）
    contexts = []
    for src in data.get("sources", []):
        text = src.get("content") or src.get("snippet") or ""
        if text:
            contexts.append(text)

    return {
        "question": sample["question"],
        "ground_truth": sample["ground_truth"],
        "category": sample.get("category", "unknown"),
        "answer": data.get("answer", ""),
        "contexts": contexts,
        # 记录实验配置，保证 A/B 对比可追溯
        "config": {"strategy": STRATEGY, "topK": TOP_K, "enableRerank": RERANK},
    }


def main():
    parser = argparse.ArgumentParser(description="采集 RAG 评估数据")
    parser.add_argument("--experiment", required=True, help="实验名，如 baseline / hybrid_topk10")
    parser.add_argument("--dataset", default=str(EVAL_ROOT / "dataset" / "test_set.jsonl"))
    args = parser.parse_args()

    samples = [
        json.loads(line)
        for line in Path(args.dataset).read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    print(f"[Collect] 测试集共 {len(samples)} 题，策略={STRATEGY}, topK={TOP_K}, rerank={RERANK}")

    out_dir = EVAL_ROOT / "results" / args.experiment
    out_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    for i, sample in enumerate(samples, 1):
        try:
            row = collect_one(sample)
            rows.append(row)
            print(f"  [{i}/{len(samples)}] {sample['question'][:30]}... OK (contexts={len(row['contexts'])})")
        except Exception as e:
            print(f"  [{i}/{len(samples)}] {sample['question'][:30]}... FAILED: {e}")
        time.sleep(0.5)   # 避免打爆接口

    out_file = out_dir / "results.jsonl"
    with open(out_file, "w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(f"\n[Collect] 完成，{len(rows)}/{len(samples)} 条成功 -> {out_file}")


if __name__ == "__main__":
    main()
