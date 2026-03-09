import type { StockProxyConfig } from "../types.js";
import { NaverKoreanMarketClient } from "../market/naver-korean-market.js";

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

export class OpenAiKoreanMarketSummarizer {
  constructor(
    private readonly config: StockProxyConfig,
    private readonly marketClient: NaverKoreanMarketClient
  ) {}

  async summarize(question: string): Promise<{
    ok: true;
    symbol: "KR_MARKET";
    model: string;
    text: string;
    responseId?: string;
    toolCalls: string[];
    groundedData: unknown;
  }> {
    if (!this.config.openAiApiKey) {
      throw new Error("OPENAI_API_KEY is required for market summary route");
    }

    const overview = await this.marketClient.getOverview();
    const response = await fetch(`${this.config.openAiBaseUrl}/responses`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${this.config.openAiApiKey}`
      },
      body: JSON.stringify({
        model: this.config.openAiModel,
        instructions: [
          "You are a Korean market briefing assistant.",
          "Use only the supplied grounded market data.",
          "Summarize the day's Korean stock market in concise Korean.",
          "Mention KOSPI and KOSDAQ levels and daily change.",
          "Mention recent trend from the supplied candles.",
          "Incorporate the supplied market news headlines if available.",
          "Do not invent unsupported facts or investment advice."
        ].join(" "),
        input: [
          {
            role: "user",
            content: [
              {
                type: "input_text",
                text: [
                  `질문: ${question}`,
                  "다음은 금일 한국 주식시장 데이터다.",
                  JSON.stringify(overview)
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
      throw new Error(`openai market summary failed: ${response.status} ${body}`.trim());
    }

    const payload = (await response.json()) as OpenAiResponsesPayload;
    const text = extractText(payload).trim();
    if (!text) {
      throw new Error("openai market summary returned empty text");
    }

    return {
      ok: true,
      symbol: "KR_MARKET",
      model: this.config.openAiModel,
      text,
      responseId: payload.id,
      toolCalls: ["market_overview"],
      groundedData: overview
    };
  }
}
