import type { MarketDataProvider, ParsedStockQuery, StockProxyConfig, SummaryRequest, SummaryResult } from "../types.js";

interface OpenAiResponseOutputItem {
  type?: string;
  id?: string;
  call_id?: string;
  name?: string;
  arguments?: string;
  content?: Array<{
    type?: string;
    text?: string;
  }>;
}

interface OpenAiResponsesPayload {
  id?: string;
  output?: OpenAiResponseOutputItem[];
}

function extractText(payload: OpenAiResponsesPayload): string {
  return (payload.output ?? [])
    .flatMap((item) => item.content ?? [])
    .filter((content) => content.type === "output_text" && typeof content.text === "string")
    .map((content) => content.text as string)
    .join("");
}

function asErrorPayload(error: unknown): { ok: false; error: string } {
  return {
    ok: false,
    error: error instanceof Error ? error.message : "unknown tool error"
  };
}

export class OpenAiStockSummarizer {
  constructor(
    private readonly config: StockProxyConfig,
    private readonly marketData: MarketDataProvider
  ) {}

  async summarize(request: SummaryRequest, parsedQuery?: ParsedStockQuery): Promise<SummaryResult> {
    if (!this.config.openAiApiKey) {
      throw new Error("OPENAI_API_KEY is required for summary route");
    }

    const normalizedSymbol = request.symbol.trim().toUpperCase();
    const groundedData: SummaryResult["groundedData"] = {};
    const toolCalls: string[] = [];
    let previousResponseId: string | undefined;
    let finalText = "";
    let responseId: string | undefined;

    for (let step = 0; step < 6; step += 1) {
      const input = step === 0
        ? request.question.trim()
        : [];

      const response = await fetch(`${this.config.openAiBaseUrl}/responses`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${this.config.openAiApiKey}`
        },
        body: JSON.stringify({
          model: this.config.openAiModel,
          instructions: [
            "You are a finance assistant for Korean users.",
            "Use tools for current stock data instead of guessing live market values.",
            "Do not give investment advice or promise returns.",
            "State uncertainty clearly when data is missing.",
            "Answer in concise Korean.",
            parsedQuery?.stockName ? `The stock name is ${parsedQuery.stockName}.` : "",
            parsedQuery?.timeframe ? `Requested timeframe is ${parsedQuery.timeframe}.` : ""
          ].join(" "),
          input,
          ...(previousResponseId ? { previous_response_id: previousResponseId } : {}),
          tools: [
            {
              type: "function",
              name: "get_quote",
              description: "Fetch the latest quote for a stock ticker.",
              parameters: {
                type: "object",
                properties: {
                  symbol: { type: "string" }
                },
                required: ["symbol"],
                additionalProperties: false
              }
            },
            {
              type: "function",
              name: "get_recent_candles",
              description: "Fetch recent daily candles for a stock ticker.",
              parameters: {
                type: "object",
                properties: {
                  symbol: { type: "string" },
                  points: { type: "integer" }
                },
                required: ["symbol"],
                additionalProperties: false
              }
            },
            {
              type: "function",
              name: "get_recent_news",
              description: "Fetch recent news items for a stock ticker.",
              parameters: {
                type: "object",
                properties: {
                  symbol: { type: "string" },
                  limit: { type: "integer" }
                },
                required: ["symbol"],
                additionalProperties: false
              }
            }
          ].filter((tool) => this.marketData.supportsNews || tool.name !== "get_recent_news")
        }),
        signal: AbortSignal.timeout(this.config.requestTimeoutMs)
      });

      if (!response.ok) {
        const body = await response.text().catch(() => "");
        throw new Error(`openai summary failed: ${response.status} ${body}`.trim());
      }

      const payload = (await response.json()) as OpenAiResponsesPayload;
      responseId = payload.id;
      previousResponseId = payload.id;
      finalText = extractText(payload);

      const functionCalls = (payload.output ?? []).filter((item) => item.type === "function_call");
      if (functionCalls.length === 0) {
        break;
      }

      const outputs = [];
      for (const call of functionCalls) {
        if (!call.call_id || !call.name) {
          continue;
        }

        const args = call.arguments ? JSON.parse(call.arguments) as Record<string, unknown> : {};
        toolCalls.push(call.name);

        if (call.name === "get_quote") {
          const quote = await this.marketData.getQuote(String(args.symbol ?? normalizedSymbol))
            .then((value) => {
              groundedData.quote = value;
              return value;
            })
            .catch((error) => asErrorPayload(error));
          outputs.push({
            type: "function_call_output",
            call_id: call.call_id,
            output: JSON.stringify(quote)
          });
          continue;
        }

        if (call.name === "get_recent_candles") {
          const candles = await this.marketData.getDailyCandles(
            String(args.symbol ?? normalizedSymbol),
            Number.isFinite(args.points as number) ? Number(args.points) : request.candlePoints ?? 5
          ).then((value) => {
            groundedData.candles = value;
            return value;
          }).catch((error) => asErrorPayload(error));
          outputs.push({
            type: "function_call_output",
            call_id: call.call_id,
            output: JSON.stringify(candles)
          });
          continue;
        }

        if (call.name === "get_recent_news") {
          if (!this.marketData.supportsNews) {
            outputs.push({
              type: "function_call_output",
              call_id: call.call_id,
              output: JSON.stringify({ ok: false, error: `news is not supported by ${this.marketData.name}` })
            });
            continue;
          }
          const newsResult = await this.marketData.getNews(
            String(args.symbol ?? normalizedSymbol),
            Number.isFinite(args.limit as number) ? Number(args.limit) : 3
          ).then((news) => {
            groundedData.news = news;
            return news;
          }).catch((error) => asErrorPayload(error));
          outputs.push({
            type: "function_call_output",
            call_id: call.call_id,
            output: JSON.stringify(newsResult)
          });
        }
      }

      if (outputs.length === 0) {
        break;
      }

      const continuation = await fetch(`${this.config.openAiBaseUrl}/responses`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${this.config.openAiApiKey}`
        },
        body: JSON.stringify({
          model: this.config.openAiModel,
          previous_response_id: previousResponseId,
          input: outputs
        }),
        signal: AbortSignal.timeout(this.config.requestTimeoutMs)
      });

      if (!continuation.ok) {
        const body = await continuation.text().catch(() => "");
        throw new Error(`openai summary continuation failed: ${continuation.status} ${body}`.trim());
      }

      const continuationPayload = (await continuation.json()) as OpenAiResponsesPayload;
      responseId = continuationPayload.id;
      previousResponseId = continuationPayload.id;
      finalText = extractText(continuationPayload);

      if (finalText) {
        break;
      }
    }

    if (!groundedData.quote) {
      groundedData.quote = await this.marketData.getQuote(normalizedSymbol);
    }
    if (!groundedData.candles) {
      groundedData.candles = await this.marketData.getDailyCandles(normalizedSymbol, request.candlePoints ?? 5);
    }
    if (request.includeNews && this.marketData.supportsNews && !groundedData.news) {
      groundedData.news = await this.marketData.getNews(normalizedSymbol, 3).catch(() => undefined);
    }

    return {
      ok: true,
      symbol: normalizedSymbol,
      model: this.config.openAiModel,
      text: finalText || "요약을 생성하지 못했습니다. 원시 데이터를 먼저 확인해 주세요.",
      responseId,
      toolCalls,
      parsedQuery,
      groundedData
    };
  }
}
