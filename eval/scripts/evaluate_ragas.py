"""
步骤2：用 RAGAS 计算四个指标
================================================================
四个指标的分工（这是 RAG 评估的核心知识）：
  faithfulness             忠实度：答案中的每个陈述（claim）是否都能在
                           检索到的 context 里找到依据？→ 防幻觉指标
  answer_relevancy         答案相关性：答案是否真的回应了问题？
                           做法：让 judge 模型根据答案反向生成若干问题，
                           再算这些生成问题与原始问题的向量相似度
  context_precision        上下文精度：检索回来的 top-k 片段里，有用的
                           是否排在前面、噪声多不多？
  context_recall           上下文召回：标准答案（ground_truth）需要的信息
                           是否都被检索回来了？→ 查「检索是否漏了」

用法：
    python scripts/evaluate_ragas.py --experiment baseline
================================================================
"""
import argparse
import json
import os
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
EVAL_ROOT = Path(__file__).resolve().parents[1]

# ---------- 加载项目 .env ----------
try:
    from dotenv import load_dotenv
    load_dotenv(PROJECT_ROOT / ".env")
except ImportError:
    pass

# ---------- 兼容 RAGAS 新旧两代 API ----------
# 新版 0.2+（推荐）：EvaluationDataset + 类名指标
# 旧版 0.1.x：datasets.Dataset + 函数名指标
try:
    from ragas import EvaluationDataset, evaluate
    from ragas.llms import LangchainLLMWrapper
    from ragas.embeddings import LangchainEmbeddingsWrapper
    from ragas.metrics import (
        Faithfulness,
        ResponseRelevancy,
        LLMContextPrecisionWithReference,
        LLMContextRecall,
    )
    NEW_API = True
    print("[RAGAS] 新版 API (EvaluationDataset)")
except ImportError:
    from ragas import evaluate
    from ragas.metrics import faithfulness, answer_relevancy, context_precision, context_recall
    NEW_API = False
    print("[RAGAS] 旧版 API，建议升级: pip install -U ragas")

from langchain_openai import ChatOpenAI, OpenAIEmbeddings
import pandas as pd


def normalize_openai_base_url(url):
    """OpenAI 兼容端点路径补全。
    教学点：Spring AI 会在 base-url 后自动拼 /v1，而 langchain-openai
    假定 base_url 自带 /v1（OpenAI 官方格式），两者约定不同。
    同一个 .env 被 Java 后端与评估脚本共用，不能改 .env，
    只能在脚本侧补全：末尾没有 /v1 就补上。
    例：https://dashscope.aliyuncs.com/compatible-mode
      -> https://dashscope.aliyuncs.com/compatible-mode/v1
    """
    if not url:
        return url
    url = url.rstrip("/")
    if not url.endswith("/v1"):
        url += "/v1"
    return url


def build_judge_llm():
    """裁判模型：评估用的 LLM。
    教学点：judge 模型建议比生成模型更强（如 qwen-max / gpt-4o），
    且温度固定为 0 保证结果可复现。
    """
    model = os.getenv("EVAL_JUDGE_MODEL") or os.getenv("OPENAI_CHAT_MODEL", "gpt-4o")
    return ChatOpenAI(
        base_url=normalize_openai_base_url(os.getenv("OPENAI_BASE_URL")),
        api_key=os.getenv("OPENAI_API_KEY"),
        model=model,
        temperature=0,
    )


def build_judge_embeddings():
    """answer_relevancy 需要 embedding 模型计算相似度。
    教学点：DeepSeek 不支持 /v1/embeddings，所以必须用独立的
    OPENAI_EMBEDDING_* 配置（OpenAI 或 DashScope text-embedding-v4）。
    没有配置就跳过 answer_relevancy 而不是报错。
    """
    if not (os.getenv("OPENAI_EMBEDDING_API_KEY") and os.getenv("OPENAI_EMBEDDING_MODEL")):
        return None
    # check_embedding_ctx_length=False：langchain-openai 默认用 tiktoken 把文本
    # 编码成 token ID 列表发送，OpenAI 官方支持，但 DashScope 兼容层不支持
    # （报 contents is neither str nor list of str）。关闭后直接传字符串。
    return OpenAIEmbeddings(
        base_url=normalize_openai_base_url(os.getenv("OPENAI_EMBEDDING_BASE_URL")),
        api_key=os.getenv("OPENAI_EMBEDDING_API_KEY"),
        model=os.getenv("OPENAI_EMBEDDING_MODEL"),
        check_embedding_ctx_length=False,
    )


def main():
    parser = argparse.ArgumentParser(description="RAGAS 评估")
    parser.add_argument("--experiment", required=True, help="对应步骤1的实验名")
    args = parser.parse_args()

    exp_dir = EVAL_ROOT / "results" / args.experiment
    results_file = exp_dir / "results.jsonl"
    if not results_file.exists():
        sys.exit(f"[ERROR] 未找到 {results_file}，请先运行 collect_results.py")

    rows = [
        json.loads(line)
        for line in results_file.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    if not rows:
        sys.exit("[ERROR] 采集结果为空，先检查 RAG 服务是否启动")

    # ---------- 组装评估数据集 ----------
    # 新版字段：user_input / retrieved_contexts / response / reference
    samples = [
        {
            "user_input": r["question"],
            "retrieved_contexts": r["contexts"],
            "response": r["answer"],
            "reference": r["ground_truth"],
        }
        for r in rows
    ]

    judge_llm = build_judge_llm()
    judge_embeddings = build_judge_embeddings()

    print(f"[Eval] 样本数={len(samples)}, judge_model={judge_llm.model_name}")
    if judge_embeddings is None:
        print("[WARN] 未配置 OPENAI_EMBEDDING_*，answer_relevancy 将跳过")

    # ---------- 执行评估 ----------
    if NEW_API:
        dataset = EvaluationDataset.from_list(samples)
        metrics = [Faithfulness(), LLMContextRecall()]
        if judge_embeddings is not None:
            # strictness=1：qwen 系列（DashScope 兼容模式）不支持 n>1 多候选生成，
            # 默认 strictness=3 会导致 LLM 降级返回 1 个、embedding 收到非法输入而报 400。
            # 教学点：评估配置必须适配 judge 模型能力，而不是照搬默认值。
            metrics.append(ResponseRelevancy(strictness=1))
        metrics.append(LLMContextPrecisionWithReference())
        # context_precision 用 WithReference 版本：用 ground_truth 做参照，更准确
        result = evaluate(
            dataset=dataset,
            metrics=metrics,
            llm=LangchainLLMWrapper(judge_llm),
            embeddings=LangchainEmbeddingsWrapper(judge_embeddings) if judge_embeddings else None,
        )
    else:
        # 旧版兜底：指标从环境变量读默认配置
        metrics = [faithfulness, context_recall]
        if judge_embeddings is not None:
            metrics.append(answer_relevancy)
        metrics.append(context_precision)
        from datasets import Dataset
        dataset = Dataset.from_dict({
            "question": [s["user_input"] for s in samples],
            "answer": [s["response"] for s in samples],
            "contexts": [s["retrieved_contexts"] for s in samples],
            "ground_truth": [s["reference"] for s in samples],
        })
        result = evaluate(dataset, metrics=metrics)

    # ---------- 输出 ----------
    df = result.to_pandas()
    # 把 question 加回结果表，方便步骤3按题对比
    df.insert(0, "question", [r["question"] for r in rows])
    df.insert(1, "category", [r["category"] for r in rows])

    per_sample_file = exp_dir / "per_sample_scores.csv"
    df.to_csv(per_sample_file, index=False, encoding="utf-8-sig")

    agg = {col: round(float(df[col].mean()), 4)
           for col in df.columns
           if col not in ("question", "category") and pd.api.types.is_numeric_dtype(df[col])}
    if not agg:
        sys.exit("[ERROR] 所有指标均无有效分数，请检查 judge LLM / embedding 配置与网络")
    agg_file = exp_dir / "aggregate.json"
    agg_file.write_text(json.dumps(agg, ensure_ascii=False, indent=2), encoding="utf-8")

    print("\n========== 平均分（0~1，越高越好） ==========")
    for name, score in agg.items():
        print(f"  {name:<28} {score:.4f}")
    print(f"\n[Eval] 每题分数 -> {per_sample_file}")
    print(f"[Eval] 平均分   -> {agg_file}")


if __name__ == "__main__":
    main()
