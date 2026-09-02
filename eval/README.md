# RAG 评估体系 SOP — 测试集 + RAGAS + A/B 对比

> 目标：用数据回答「我的 RAG 到底好不好」，以及「这次优化到底有没有用」。

---

## 一、30 秒看懂：四个指标是什么

RAG = 检索（Retrieval）+ 生成（Generation）。评估也必须拆成两半，
否则「答案错了」你不知道是没检索到，还是检索到了但模型没用好。

| 指标 | 英文 | 评估对象 | 回答的问题 | 需要标准答案？ |
|---|---|---|---|---|
| 忠实度 | Faithfulness | 生成 | 答案有没有编造？（幻觉检测） | 否 |
| 答案相关性 | Answer Relevancy | 生成 | 答案有没有答非所问？ | 否 |
| 上下文精度 | Context Precision | 检索 | 召回的结果里噪声多不多、有用的排前面吗？ | 是 |
| 上下文召回 | Context Recall | 检索 | 该召回的信息漏了没有？ | 是 |

一句话记忆：**recall 管「找没找全」，precision 管「找没找对」，
faithfulness 管「说没说错」，relevancy 管「答没答偏」**。

---

## 二、环境准备（一次性的）

```powershell
cd D:\workspaceForZhu\ai-code-pro\enterprise-rag-agent-zhuyt\eval
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

说明：
- RAGAS 是 Python 生态（业界事实标准），你的 RAG 是 Java——没关系，
  评估体系用 HTTP 调你的接口，语言不绑定。
- 评估需要一个「裁判 LLM」（judge）。脚本复用项目 .env 里的
  `OPENAI_BASE_URL / OPENAI_API_KEY / OPENAI_CHAT_MODEL`。
  建议用 `EVAL_JUDGE_MODEL` 环境变量指定更强的模型当裁判
  （如 qwen-max / gpt-4o），比生成模型强，判得才准。

---

## 三、三步执行 SOP

### Step 0：准备（5 分钟）
1. 重启后端（已做 SourceReference.content 改造）。
2. 把 `dataset/sample_knowledge.md` 上传到 RAG 系统（页面上传即可）。
3. 确认测试集存在：`dataset/test_set.jsonl`（30 题）。

### Step 1：采集 baseline（跑一遍测试集）
```powershell
python scripts/collect_results.py --experiment baseline
```
产出：`results/baseline/results.jsonl`（每题：question、answer、contexts、ground_truth）

### Step 2：评估 baseline
```powershell
python scripts/evaluate_ragas.py --experiment baseline
```
产出：
- `results/baseline/aggregate.json`（平均分）→ 这就是你的「体检报告」
- `results/baseline/per_sample_scores.csv`（每题分数）→ bad case 分析

### Step 3：做优化 → 重跑 → 对比
以「调大 topK」为例（控制变量：只改这一个参数）：
```powershell
$env:EVAL_TOP_K = "10"
python scripts/collect_results.py --experiment topk10
python scripts/evaluate_ragas.py --experiment topk10
python scripts/compare_experiments.py --baseline baseline --candidate topk10
```
产出：`results/compare_baseline_vs_topk10.md`（平均分对比 + 提升/退化 Top5 + 类别汇总）

> ⚠️ 记住改回环境变量，避免影响后续实验：
> `Remove-Item Env:EVAL_TOP_K`

### 后续优化实验（换变量，重跑 Step 1-3）
| 实验名 | 改什么 | 预期影响 |
|---|---|---|
| `rerank_off` | `EVAL_RERANK=false` | context_precision 可能下降 |
| `topk10` | `EVAL_TOP_K=10` | recall 升、precision 可能降（经典权衡）|
| `hybrid` | 实现混合检索后 `EVAL_STRATEGY=HYBRID` | recall/precision 双升 |
| `query_rewrite` | 实现 Query 改写后 | recall 升 |
| `chunk500` | 切块改 500 后重建知识库 | 看细粒度 chunk 的影响 |

---

## 四、指标原理

### 1. Faithfulness（忠实度）—— 拆 claim + 逐条验证
1. 把答案拆成若干个独立陈述（claim），如「星云成立于2016年，总部在杭州」→ 2 个 claim；
2. 对每个 claim，问 judge LLM：**这个说法能否被检索到的 context 支持？**
3. 得分 = 被支持的 claim 数 / 总 claim 数。
- 得分低 = 模型在编造 → 排查：prompt 约束、chunk 相关性、模型能力。

### 2. Answer Relevancy（答案相关性）—— 反向生成问题 + 向量相似度
1. judge LLM 根据答案反向生成 N 个「可能的问题」；
2. 把这些生成问题与原始问题分别算 embedding 相似度；
3. 得分 = 平均相似度。
- 需要 embedding 模型 → 这正是 DeepSeek 的坑：它没有 /v1/embeddings，
  脚本用你独立的 `OPENAI_EMBEDDING_*` 配置（OpenAI 或 DashScope text-embedding-v4）。
- 得分低 = 答非所问 → 排查：生成阶段 prompt、检索内容与问题的匹配度。

### 3. Context Precision（上下文精度）—— 逐位截断 + 加权
1. 对检索结果的第 k 个片段，问 judge：这个片段对回答问题有用吗？
2. 从 k=1 开始累计：前 k 个片段中「有用片段」占比的平均值。
- 有用但排得靠后 → 分低 → 排查：rerank、topK 过大、阈值。

### 4. Context Recall（上下文召回）—— 对标准答案拆 claim + 覆盖检查
1. 把标准答案（ground_truth）拆成若干 claim；
2. 逐个问 judge：这个 claim 能在检索到的 context 里找到吗？
3. 得分 = 被覆盖的 claim 数 / 总 claim 数。
- 得分低 = 检索漏了 → 排查：topK 太小、切块太粗、embedding 模型、Query 改写、混合检索。

### 重要认知
- 指标是 **LLM-as-a-Judge**（LLM 当裁判），不是精确数学——同一个样本跑两次
  分数可能有波动。对策：judge 温度设 0、优化结论看平均分 + bad case，
  而不是单题的抖动。
- 四个指标必须一起看：比如 context_recall 满分但 faithfulness 低，
  说明检索没问题，是生成在自由发挥。

---

## 五、指标低 → 排查方向速查表

| 现象 | 大概率原因 | 动作 |
|---|---|---|
| faithfulness 低 | 生成幻觉 / chunk 相关性差 | 收紧 system prompt；调 rerank 和阈值 |
| answer_relevancy 低 | 生成偏题 | 换模型；检查 context 与问题的匹配 |
| context_precision 低 | 召回噪声多 | 开 rerank；降 topK；提高阈值 |
| context_recall 低 | 检索漏召回 | 升 topK；混合检索；Query 改写；换 embedding |
| 全部低 | 测试集与知识库不匹配 | 检查文档是否上传；测试集题目是否越界 |

---

## 六、常见坑（已踩坑预警）

1. **snippet 截断失真**：后端 SourceReference 必须返回完整 content，
   否则 context 类指标系统性偏低（本 SOP 已改造）。
2. **judge 与生成同模型**：自评容易偏乐观。用更强的模型当 judge。
3. **测试集污染**：不要把 test_set.jsonl 也上传进知识库，否则题目答案
   直接"背题"。
4. **控制变量**：一次只改一个参数；改完检索参数要重新 collect。
5. **随机性**：temperature=0 只能降低、不能消除波动；重要结论多跑一轮取均值。
6. **DeepSeek 无 embedding**：answer_relevancy 会失败——脚本已自动降级跳过，
   配置好 OPENAI_EMBEDDING_* 即可恢复。

---

## 七、进阶路线（跑通后再来）

1. **自动生成测试集**：RAGAS 的 `TestsetGenerator` 可以从你的知识库文档
   自动演化出测试题（simple / reasoning / multi-context 三种难度），
   减少手工出题成本。
2. **更多指标**：`FactualCorrectness`（对比标准答案的事实正确率）、
   `NoiseSensitivity`、`ContextEntityRecall`。
3. **CI 集成**：每次检索/生成代码改动，自动跑一遍评估集，
   指标跌破阈值（如 faithfulness < 0.85）就拦截合并。
4. **维度扩展**：在测试集里加 `无答案类` 用例（知识库里没有答案时，
   系统是否诚实拒答）——这是企业落地最常被问的。
