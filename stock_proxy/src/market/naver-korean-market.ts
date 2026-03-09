import type { CandlePoint, NewsItem, StockProxyConfig } from "../types.js";

interface NaverIndexBasicPayload {
  itemCode?: string;
  stockName?: string;
  closePrice?: string;
  compareToPreviousClosePrice?: string;
  fluctuationsRatio?: string;
  localTradedAt?: string;
}

export interface MarketIndexQuote {
  code: "KOSPI" | "KOSDAQ";
  name: string;
  price: number;
  change: number;
  changePercent: number;
  latestTradingDay?: string;
}

export interface KoreanMarketOverview {
  kospi: MarketIndexQuote;
  kosdaq: MarketIndexQuote;
  kospiCandles: CandlePoint[];
  kosdaqCandles: CandlePoint[];
  news: NewsItem[];
}

export class NaverKoreanMarketClient {
  constructor(private readonly config: StockProxyConfig) {}

  async getOverview(): Promise<KoreanMarketOverview> {
    const [kospi, kosdaq, kospiCandles, kosdaqCandles, news] = await Promise.all([
      this.getIndexQuote("KOSPI"),
      this.getIndexQuote("KOSDAQ"),
      this.getIndexCandles("KOSPI", 7),
      this.getIndexCandles("KOSDAQ", 7),
      this.getMarketNews(5)
    ]);

    return { kospi, kosdaq, kospiCandles, kosdaqCandles, news };
  }

  async getIndexQuote(code: "KOSPI" | "KOSDAQ"): Promise<MarketIndexQuote> {
    const payload = await fetchJson<NaverIndexBasicPayload>(
      `https://m.stock.naver.com/api/index/${code}/basic`,
      this.config.requestTimeoutMs,
      "https://m.stock.naver.com/"
    );

    return {
      code,
      name: payload.stockName ?? code,
      price: parseCommaNumber(payload.closePrice),
      change: parseCommaNumber(payload.compareToPreviousClosePrice),
      changePercent: parseFloatNumber(payload.fluctuationsRatio),
      latestTradingDay: payload.localTradedAt?.slice(0, 10)
    };
  }

  async getIndexCandles(code: "KOSPI" | "KOSDAQ", points: number): Promise<CandlePoint[]> {
    const url =
      `https://api.finance.naver.com/siseJson.naver?symbol=${code}&requestType=1&startTime=${daysAgoYmd(30)}&endTime=${todayYmd()}&timeframe=day`;
    const raw = await fetchText(url, this.config.requestTimeoutMs, "https://finance.naver.com/");
    const parsed = JSON.parse(raw.trim().replace(/'/g, "\"")) as Array<Array<string | number>>;
    const rows = parsed.slice(1).filter((row) => Array.isArray(row) && typeof row[0] === "string");

    return rows
      .slice(-Math.max(1, Math.min(points, 30)))
      .reverse()
      .map((row) => ({
        date: String(row[0]),
        open: Number(row[1] ?? 0),
        high: Number(row[2] ?? 0),
        low: Number(row[3] ?? 0),
        close: Number(row[4] ?? 0),
        volume: Number(row[5] ?? 0)
      }));
  }

  async getMarketNews(limit: number): Promise<NewsItem[]> {
    const html = await fetchText(
      "https://finance.naver.com/sise/sise_index.naver?code=KOSPI",
      this.config.requestTimeoutMs,
      "https://finance.naver.com/"
    );

    const decoded = decodeHtmlEntities(html);
    const items: NewsItem[] = [];
    const seen = new Set<string>();
    const regex = /news_read\.naver\?article_id=(\d+)&office_id=(\d+)[^>]*>([^<]+)</g;

    for (const match of decoded.matchAll(regex)) {
      const articleId = match[1];
      const officeId = match[2];
      const title = stripTags(match[3]).trim();
      if (!title || seen.has(articleId)) {
        continue;
      }

      seen.add(articleId);
      items.push({
        title,
        url: `https://n.news.naver.com/mnews/article/${officeId}/${articleId}`,
        source: "Naver Finance",
        timePublished: ""
      });

      if (items.length >= Math.max(1, Math.min(limit, 10))) {
        break;
      }
    }

    return items;
  }
}

async function fetchJson<T>(url: string, timeoutMs: number, referer: string): Promise<T> {
  const raw = await fetchText(url, timeoutMs, referer);
  return JSON.parse(raw) as T;
}

async function fetchText(url: string, timeoutMs: number, referer: string): Promise<string> {
  let lastError: Error | undefined;

  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      const response = await fetch(url, {
        headers: { "User-Agent": "Mozilla/5.0", Referer: referer },
        signal: AbortSignal.timeout(timeoutMs)
      });
      if (!response.ok) {
        throw new Error(`naver market request failed: ${response.status}`);
      }

      const buffer = await response.arrayBuffer();
      const contentType = response.headers.get("content-type") ?? "";
      const charset = contentType.toLowerCase().includes("euc-kr") ? "euc-kr" : "utf-8";
      const text = new TextDecoder(charset).decode(buffer).trim();
      if (!text) {
        throw new Error("naver market request returned empty body");
      }
      return text;
    } catch (error) {
      lastError = error instanceof Error ? error : new Error(String(error));
      if (attempt < 2) {
        await new Promise((resolve) => setTimeout(resolve, 150 * (attempt + 1)));
      }
    }
  }

  throw lastError ?? new Error("naver market request failed");
}

function parseCommaNumber(value: string | undefined): number {
  return Number((value ?? "0").replace(/,/g, "")) || 0;
}

function parseFloatNumber(value: string | undefined): number {
  return Number.parseFloat(value ?? "0") || 0;
}

function todayYmd(): string {
  return formatYmd(new Date());
}

function daysAgoYmd(days: number): string {
  return formatYmd(new Date(Date.now() - days * 24 * 60 * 60 * 1000));
}

function formatYmd(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}${month}${day}`;
}

function decodeHtmlEntities(input: string): string {
  return input
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'")
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&hellip;/g, "...");
}

function stripTags(input: string): string {
  return input.replace(/<[^>]+>/g, " ");
}
