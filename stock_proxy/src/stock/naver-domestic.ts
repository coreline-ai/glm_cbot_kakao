import type { CandlePoint, CandleSeriesResult, MarketDataProvider, NewsItem, NewsResult, QuoteResult, StockProxyConfig } from "../types.js";

interface NaverBasicPayload {
  stockName?: string;
  stockExchangeName?: string;
  localTradedAt?: string;
}

interface NaverSummaryPayload {
  now?: number;
  diff?: number;
  rate?: number;
  quant?: number;
  amount?: number;
  high?: number;
  low?: number;
  open?: number;
}

interface NaverNewsPayload {
  total?: number;
  items?: Array<{
    officeId?: string;
    articleId?: string;
    title?: string;
    body?: string;
    datetime?: string;
    officeName?: string;
  }>;
}

interface NaverAutocompletePayload {
  items?: Array<{
    code?: string;
    name?: string;
    typeCode?: string;
    category?: string;
    nationCode?: string;
  }>;
}

type NaverAutocompleteItem = NonNullable<NaverAutocompletePayload["items"]>[number];

interface ResolvedDomesticStock {
  code: string;
  name?: string;
}

export class NaverDomesticClient implements MarketDataProvider {
  readonly name = "naver_domestic" as const;
  readonly market = "kr" as const;
  readonly supportsNews = true;
  private readonly resolvedCache = new Map<string, ResolvedDomesticStock>();

  constructor(private readonly config: StockProxyConfig) {}

  isConfigured(): boolean {
    return true;
  }

  async getQuote(symbol: string): Promise<QuoteResult> {
    const resolved = await this.resolveSymbol(symbol);
    const normalized = resolved.code;
    const [basic, summary] = await Promise.all([
      fetchJson<NaverBasicPayload>(
        `https://m.stock.naver.com/api/stock/${normalized}/basic`,
        this.config.requestTimeoutMs,
        "https://m.stock.naver.com/"
      ),
      fetchJson<NaverSummaryPayload>(
        `https://api.finance.naver.com/service/itemSummary.nhn?itemcode=${normalized}`,
        this.config.requestTimeoutMs,
        "https://finance.naver.com/"
      )
    ]);

    return {
      symbol: normalized,
      market: "kr",
      name: basic.stockName ?? resolved.name,
      price: summary.now,
      change: summary.diff,
      changePercent: summary.rate != null ? `${summary.rate}` : undefined,
      volume: summary.quant,
      latestTradingDay: basic.localTradedAt?.slice(0, 10),
      open: summary.open,
      high: summary.high,
      low: summary.low,
      currency: "KRW",
      source: this.name
    };
  }

  async getDailyCandles(symbol: string, points: number): Promise<CandleSeriesResult> {
    const resolved = await this.resolveSymbol(symbol);
    const normalized = resolved.code;
    const endDate = todayYmd();
    const startDate = daysAgoYmd(90);
    const url =
      `https://api.finance.naver.com/siseJson.naver?symbol=${normalized}&requestType=1&startTime=${startDate}&endTime=${endDate}&timeframe=day`;
    const raw = await fetchText(url, this.config.requestTimeoutMs);
    const parsed = parseNaverSiseJson(raw);
    const rows = parsed.slice(1).filter((row) => Array.isArray(row) && typeof row[0] === "string");

    const normalizedPoints: CandlePoint[] = rows
      .slice(-Math.max(1, Math.min(points, 90)))
      .reverse()
      .map((row) => ({
        date: String(row[0]),
        open: Number(row[1] ?? 0),
        high: Number(row[2] ?? 0),
        low: Number(row[3] ?? 0),
        close: Number(row[4] ?? 0),
        volume: Number(row[5] ?? 0)
      }));

    return {
      symbol: normalized,
      market: "kr",
      interval: "daily",
      points: normalizedPoints,
      source: this.name
    };
  }

  async getNews(symbol: string, limit: number): Promise<NewsResult> {
    const resolved = await this.resolveSymbol(symbol);
    const normalized = resolved.code;
    const payload = await fetchJson<NaverNewsPayload[]>(
      `https://m.stock.naver.com/api/news/stock/${normalized}?pageSize=${Math.max(1, Math.min(limit, 20))}&page=1`,
      this.config.requestTimeoutMs,
      "https://m.stock.naver.com/"
    );

    const items: NewsItem[] = payload
      .flatMap((group) => group.items ?? [])
      .slice(0, Math.max(1, Math.min(limit, 20)))
      .map((item) => ({
      title: item.title ?? "",
      url:
        item.officeId && item.articleId
          ? `https://n.news.naver.com/mnews/article/${item.officeId}/${item.articleId}`
          : "",
      summary: item.body ?? undefined,
      source: item.officeName ?? "Naver News",
      timePublished: item.datetime ?? ""
      }));

    return {
      symbol: normalized,
      market: "kr",
      items,
      source: this.name
    };
  }

  private async resolveSymbol(input: string): Promise<ResolvedDomesticStock> {
    const directCode = extractDomesticCode(input);
    if (directCode) {
      return { code: directCode };
    }

    const cacheKey = input.trim().toLowerCase();
    const cached = this.resolvedCache.get(cacheKey);
    if (cached) {
      return cached;
    }

    const candidates = buildSearchCandidates(input);
    for (const candidate of candidates) {
      const payload = await fetchJson<NaverAutocompletePayload>(
        `https://ac.stock.naver.com/ac?q=${encodeURIComponent(candidate)}&target=stock,ipo,index,marketindicator`,
        this.config.requestTimeoutMs,
        "https://finance.naver.com/"
      );

      const exact = (payload.items ?? []).find((item) => {
        return isDomesticStockItem(item) && normalizeName(item.name) === normalizeName(candidate);
      });
      const fallback = (payload.items ?? []).find(isDomesticStockItem);
      const resolvedItem = exact ?? fallback;
      if (resolvedItem?.code) {
        const resolved = { code: resolvedItem.code, name: resolvedItem.name };
        this.resolvedCache.set(cacheKey, resolved);
        this.resolvedCache.set(candidate.toLowerCase(), resolved);
        return resolved;
      }
    }

    throw new Error(`domestic symbol not resolved from input: ${input}`);
  }
}

async function fetchJson<T>(url: string, timeoutMs: number, referer: string): Promise<T> {
  const raw = await fetchRaw(url, timeoutMs, referer);
  return JSON.parse(raw) as T;
}

async function fetchText(url: string, timeoutMs: number): Promise<string> {
  return await fetchRaw(url, timeoutMs, "https://finance.naver.com/");
}

async function fetchRaw(url: string, timeoutMs: number, referer: string): Promise<string> {
  let lastError: Error | undefined;

  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      const response = await fetch(url, {
        headers: { "User-Agent": "Mozilla/5.0", Referer: referer },
        signal: AbortSignal.timeout(timeoutMs)
      });
      if (!response.ok) {
        throw new Error(`naver request failed: ${response.status}`);
      }
      const raw = (await response.text()).trim();
      if (!raw) {
        throw new Error("naver request returned empty body");
      }
      return raw;
    } catch (error) {
      lastError = error instanceof Error ? error : new Error(String(error));
      if (attempt < 2) {
        await new Promise((resolve) => setTimeout(resolve, 150 * (attempt + 1)));
      }
    }
  }

  throw lastError ?? new Error("naver request failed");
}

function parseNaverSiseJson(raw: string): Array<Array<string | number>> {
  const normalized = raw.trim().replace(/'/g, "\"");
  return JSON.parse(normalized) as Array<Array<string | number>>;
}

function extractDomesticCode(input: string): string | null {
  const trimmed = input.trim().toUpperCase();
  const match = trimmed.match(/(?<!\d)(\d{6})(?:\.[A-Z]{2,4})?(?!\d)/);
  return match?.[1] ?? null;
}

function buildSearchCandidates(input: string): string[] {
  const trimmed = input.trim();
  const normalized = trimmed.replace(/[()[\]{}!?.,/\\|"'`~:;+-]/g, " ").replace(/\s+/g, " ").trim();
  const tokens = normalized.split(" ").filter(Boolean);
  const filtered = tokens.filter((token) => !STOP_WORDS.has(token.toLowerCase()));
  const source = filtered.length > 0 ? filtered : tokens;
  const candidates = new Set<string>();

  addCandidate(candidates, trimmed);
  addCandidate(candidates, trimmed.replace(/\s+/g, ""));
  addCandidate(candidates, normalized);
  addCandidate(candidates, normalized.replace(/\s+/g, ""));

  for (let size = Math.min(4, source.length); size >= 1; size -= 1) {
    for (let index = 0; index <= source.length - size; index += 1) {
      const slice = source.slice(index, index + size);
      addCandidate(candidates, slice.join(" "));
      addCandidate(candidates, slice.join(""));
    }
  }

  return [...candidates].filter((candidate) => candidate.length >= 2).slice(0, 24);
}

function addCandidate(bucket: Set<string>, value: string): void {
  const normalized = value.trim();
  if (!normalized) {
    return;
  }
  bucket.add(normalized);
}

function normalizeName(value: string | undefined): string {
  return (value ?? "").replace(/\s+/g, "").trim().toUpperCase();
}

function isDomesticStockItem(item: NaverAutocompleteItem | undefined): boolean {
  if (!item?.code || !item.name) {
    return false;
  }
  return item.category === "stock" && item.nationCode === "KOR";
}

function todayYmd(): string {
  const now = new Date();
  return formatYmd(now);
}

function daysAgoYmd(days: number): string {
  const value = new Date(Date.now() - days * 24 * 60 * 60 * 1000);
  return formatYmd(value);
}

function formatYmd(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}${month}${day}`;
}

const STOP_WORDS = new Set([
  "최근",
  "흐름",
  "요약",
  "해줘",
  "알려줘",
  "알려",
  "뉴스",
  "주가",
  "종가",
  "차트",
  "분석",
  "전망",
  "어때",
  "어떄",
  "오늘",
  "지금",
  "좀",
  "부탁",
  "정리",
  "설명",
  "봐줘",
  "부탁해",
  "리포트",
  "stock",
  "summary"
]);
