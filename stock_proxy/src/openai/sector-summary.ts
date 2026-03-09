import type { ParsedStockQuery, QuoteResult, CandleSeriesResult, NewsResult, StockProxyConfig } from "../types.js";
import type { MarketDataProvider } from "../types.js";
import { findSectorPreset } from "../query/sector-dictionary.js";

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

export class OpenAiSectorSummarizer {
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
      sector: string;
      members: Array<{
        quote: QuoteResult;
        candles?: CandleSeriesResult;
        news?: NewsResult;
      }>;
    };
  }> {
    if (!this.config.openAiApiKey) {
      throw new Error("OPENAI_API_KEY is required for sector summary route");
    }

    const sectorName = parsedQuery.entities.find((entity) => entity.type === "sector")?.name ?? parsedQuery.originalQuery;
    const preset = findSectorPreset(sectorName);
    if (!preset) {
      throw new Error(`sector preset not found: ${sectorName}`);
    }

    const members = await Promise.all(preset.symbols.map(async (member) => {
      const quote = await this.marketData.getQuote(member.symbol);
      const candles = await this.marketData.getDailyCandles(member.symbol, parsedQuery.timeframe === "1m" ? 20 : 5);
      const news = await this.marketData.getNews(member.symbol, 2).catch(() => undefined);
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
          "You are a Korean sector briefing assistant.",
          "Use only the supplied grounded data.",
          "Summarize the sector mood in concise Korean.",
          "Mention which representative stocks were strong or weak.",
          "Highlight common drivers from the supplied news, but do not invent unsupported facts."
        ].join(" "),
        input: [
          {
            role: "user",
            content: [
              {
                type: "input_text",
                text: [
                  `질문: ${parsedQuery.originalQuery}`,
                  JSON.stringify({ sector: preset.name, members, parsedQuery })
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
      throw new Error(`openai sector summary failed: ${response.status} ${body}`.trim());
    }

    const payload = await response.json() as OpenAiResponsesPayload;
    const text = extractText(payload).trim();
    if (!text) {
      throw new Error("openai sector summary returned empty text");
    }

    return {
      ok: true,
      symbol: preset.symbols.map((member) => member.symbol).join(","),
      model: this.config.openAiModel,
      text,
      responseId: payload.id,
      toolCalls: ["sector_overview"],
      parsedQuery,
      groundedData: {
        sector: preset.name,
        members
      }
    };
  }
}

