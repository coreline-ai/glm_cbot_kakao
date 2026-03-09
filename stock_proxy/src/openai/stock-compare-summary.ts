import type { ParsedStockQuery, QuoteResult, CandleSeriesResult, NewsResult, StockProxyConfig } from "../types.js";
import type { MarketDataProvider } from "../types.js";

interface OpenAiResponseOutputItem {
  type?: string;
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

export class OpenAiStockCompareSummarizer {
  constructor(
    private readonly config: StockProxyConfig,
    private readonly marketData: MarketDataProvider
  ) {}

  async summarize(parsedQuery: ParsedStockQuery): Promise<{
    ok: true;
    symbol: string;
    model: string;
    text: string;
    responseId?: string;
    toolCalls: string[];
    parsedQuery: ParsedStockQuery;
    groundedData: {
      compare: Array<{
        quote: QuoteResult;
        candles?: CandleSeriesResult;
        news?: NewsResult;
      }>;
    };
  }> {
    if (!this.config.openAiApiKey) {
      throw new Error("OPENAI_API_KEY is required for compare summary route");
    }

    const stocks = parsedQuery.entities
      .filter((entity) => entity.type === "stock")
      .slice(0, 2);
    if (stocks.length < 2) {
      throw new Error("two stocks are required for compare summary");
    }

    const compare = await Promise.all(stocks.map(async (stock) => {
      const quote = await this.marketData.getQuote(stock.symbol ?? stock.name);
      const candles = await this.marketData.getDailyCandles(stock.symbol ?? stock.name, parsedQuery.timeframe === "1m" ? 20 : 5);
      const news = await this.marketData.getNews(stock.symbol ?? stock.name, 2).catch(() => undefined);
      return { quote, candles, news };
    }));

    const response = await fetch(`${this.config.openAiBaseUrl}/responses`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${this.config.openAiApiKey}`
      },
      body: JSON.stringify({
        model: this.config.openAiModel,
        instructions: [
          "You are a Korean stock comparison assistant.",
          "Use only the supplied grounded data.",
          "Compare the two stocks in concise Korean.",
          "Mention price move, recent trend, and any notable news differences.",
          "End with a short practical summary, but do not give direct investment advice."
        ].join(" "),
        input: [
          {
            role: "user",
            content: [
              {
                type: "input_text",
                text: [
                  `질문: ${parsedQuery.originalQuery}`,
                  JSON.stringify({ compare, parsedQuery })
                ].join("\n\n")
              }
            ]
          }
        ]
      }),
      signal: AbortSignal.timeout(this.config.requestTimeoutMs)
    });

    if (!response.ok) {
      const body = await response.text().catch(() => "");
      throw new Error(`openai compare summary failed: ${response.status} ${body}`.trim());
    }

    const payload = await response.json() as OpenAiResponsesPayload;
    const text = extractText(payload).trim();
    if (!text) {
      throw new Error("openai compare summary returned empty text");
    }

    return {
      ok: true,
      symbol: compare.map((item) => item.quote.symbol).join(","),
      model: this.config.openAiModel,
      text,
      responseId: payload.id,
      toolCalls: ["compare_overview"],
      parsedQuery,
      groundedData: { compare }
    };
  }
}

