import type {
  CandlePoint,
  CandleSeriesResult,
  MarketDataProvider,
  NewsItem,
  NewsResult,
  QuoteResult,
  StockProxyConfig
} from "../types.js";

function toNumber(value: string | undefined): number | undefined {
  const parsed = Number.parseFloat(value ?? "");
  return Number.isFinite(parsed) ? parsed : undefined;
}

function toInt(value: string | undefined): number | undefined {
  const parsed = Number.parseInt(value ?? "", 10);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function normalizeSymbol(input: string): string {
  return input.trim().toUpperCase();
}

export class AlphaVantageClient implements MarketDataProvider {
  readonly name = "alpha_vantage" as const;
  readonly market = "global" as const;
  readonly supportsNews = true;
  private lastRequestAt = 0;

  constructor(private readonly config: StockProxyConfig) {}

  isConfigured(): boolean {
    return Boolean(this.config.alphaVantageApiKey);
  }

  private async waitForRateWindow(): Promise<void> {
    const now = Date.now();
    const minIntervalMs = 1_200;
    const waitMs = this.lastRequestAt + minIntervalMs - now;
    if (waitMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, waitMs));
    }
    this.lastRequestAt = Date.now();
  }

  async getQuote(symbol: string): Promise<QuoteResult> {
    const normalized = normalizeSymbol(symbol);
    const url = new URL(this.config.alphaVantageBaseUrl);
    url.searchParams.set("function", "GLOBAL_QUOTE");
    url.searchParams.set("symbol", normalized);
    url.searchParams.set("apikey", this.config.alphaVantageApiKey);

    await this.waitForRateWindow();
    const response = await fetch(url, {
      signal: AbortSignal.timeout(this.config.requestTimeoutMs)
    });
    if (!response.ok) {
      throw new Error(`alpha vantage quote failed: ${response.status}`);
    }

    const payload = (await response.json()) as {
      "Global Quote"?: Record<string, string>;
      Information?: string;
      Note?: string;
    };

    if (payload.Note || payload.Information) {
      throw new Error(payload.Note || payload.Information || "alpha vantage quote unavailable");
    }

    const quote = payload["Global Quote"];
    if (!quote || !quote["01. symbol"]) {
      throw new Error("alpha vantage quote unavailable");
    }

    return {
      symbol: quote["01. symbol"],
      market: "global",
      open: toNumber(quote["02. open"]),
      high: toNumber(quote["03. high"]),
      low: toNumber(quote["04. low"]),
      price: toNumber(quote["05. price"]),
      volume: toInt(quote["06. volume"]),
      latestTradingDay: quote["07. latest trading day"],
      previousClose: toNumber(quote["08. previous close"]),
      change: toNumber(quote["09. change"]),
      changePercent: quote["10. change percent"],
      source: this.name
    };
  }

  async getDailyCandles(symbol: string, points: number): Promise<CandleSeriesResult> {
    const normalized = normalizeSymbol(symbol);
    const url = new URL(this.config.alphaVantageBaseUrl);
    url.searchParams.set("function", "TIME_SERIES_DAILY");
    url.searchParams.set("symbol", normalized);
    url.searchParams.set("apikey", this.config.alphaVantageApiKey);

    await this.waitForRateWindow();
    const response = await fetch(url, {
      signal: AbortSignal.timeout(this.config.requestTimeoutMs)
    });
    if (!response.ok) {
      throw new Error(`alpha vantage candles failed: ${response.status}`);
    }

    const payload = (await response.json()) as {
      "Meta Data"?: Record<string, string>;
      "Time Series (Daily)"?: Record<string, Record<string, string>>;
      Information?: string;
      Note?: string;
    };

    if (payload.Note || payload.Information) {
      throw new Error(payload.Note || payload.Information || "alpha vantage candles unavailable");
    }

    const rawSeries = payload["Time Series (Daily)"];
    if (!rawSeries) {
      throw new Error("alpha vantage candles unavailable");
    }

    const normalizedPoints: CandlePoint[] = Object.entries(rawSeries)
      .sort(([left], [right]) => right.localeCompare(left))
      .slice(0, Math.max(1, Math.min(points, 30)))
      .map(([date, candle]) => ({
        date,
        open: toNumber(candle["1. open"]) ?? 0,
        high: toNumber(candle["2. high"]) ?? 0,
        low: toNumber(candle["3. low"]) ?? 0,
        close: toNumber(candle["4. close"]) ?? 0,
        volume: toInt(candle["5. volume"]) ?? 0
      }));

    return {
      symbol: normalized,
      market: "global",
      interval: "daily",
      points: normalizedPoints,
      source: this.name
    };
  }

  async getNews(symbol: string, limit: number): Promise<NewsResult> {
    const normalized = normalizeSymbol(symbol);
    const url = new URL(this.config.alphaVantageBaseUrl);
    url.searchParams.set("function", "NEWS_SENTIMENT");
    url.searchParams.set("tickers", normalized);
    url.searchParams.set("sort", "LATEST");
    url.searchParams.set("limit", String(Math.max(1, Math.min(limit, 10))));
    url.searchParams.set("apikey", this.config.alphaVantageApiKey);

    await this.waitForRateWindow();
    const response = await fetch(url, {
      signal: AbortSignal.timeout(this.config.requestTimeoutMs)
    });
    if (!response.ok) {
      throw new Error(`alpha vantage news failed: ${response.status}`);
    }

    const payload = (await response.json()) as {
      feed?: Array<Record<string, unknown>>;
      Information?: string;
      Note?: string;
    };

    if (payload.Note || payload.Information) {
      throw new Error(payload.Note || payload.Information || "alpha vantage news unavailable");
    }

    const items: NewsItem[] = (payload.feed ?? []).map((item) => ({
      title: typeof item.title === "string" ? item.title : "",
      url: typeof item.url === "string" ? item.url : "",
      summary: typeof item.summary === "string" ? item.summary : undefined,
      source: typeof item.source === "string" ? item.source : "unknown",
      timePublished: typeof item.time_published === "string" ? item.time_published : "",
      sentimentLabel:
        typeof item.overall_sentiment_label === "string" ? item.overall_sentiment_label : undefined,
      overallSentimentScore:
        typeof item.overall_sentiment_score === "number"
          ? item.overall_sentiment_score
          : typeof item.overall_sentiment_score === "string"
            ? Number.parseFloat(item.overall_sentiment_score)
            : undefined
    }));

    return {
      symbol: normalized,
      market: "global",
      items,
      source: this.name
    };
  }
}
