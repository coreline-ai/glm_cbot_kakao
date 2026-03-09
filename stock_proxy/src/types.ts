export interface StockProxyConfig {
  host: string;
  port: number;
  alphaVantageApiKey: string;
  alphaVantageBaseUrl: string;
  openAiApiKey: string;
  openAiBaseUrl: string;
  openAiModel: string;
  requestTimeoutMs: number;
}

export type MarketProviderName = "alpha_vantage" | "naver_domestic";

export interface QuoteResult {
  symbol: string;
  market: "global" | "kr";
  name?: string;
  price?: number;
  change?: number;
  changePercent?: string;
  volume?: number;
  latestTradingDay?: string;
  previousClose?: number;
  open?: number;
  high?: number;
  low?: number;
  currency?: string;
  source: MarketProviderName;
}

export interface CandlePoint {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface CandleSeriesResult {
  symbol: string;
  market: "global" | "kr";
  interval: "daily";
  points: CandlePoint[];
  source: MarketProviderName;
}

export interface NewsItem {
  title: string;
  url: string;
  summary?: string;
  source: string;
  timePublished: string;
  sentimentLabel?: string;
  overallSentimentScore?: number;
}

export interface NewsResult {
  symbol: string;
  market: "global" | "kr";
  items: NewsItem[];
  source: MarketProviderName;
}

export interface SummaryRequest {
  symbol: string;
  question: string;
  includeNews?: boolean;
  candlePoints?: number;
}

export type SummaryCategory =
  | "market_summary"
  | "stock_summary"
  | "stock_compare"
  | "sector_summary"
  | "unknown";

export type SummaryTimeframe = "today" | "5d" | "1w" | "1m";

export interface ParsedStockEntity {
  type: "stock" | "market" | "sector";
  name: string;
  symbol?: string;
  matchedText?: string;
}

export interface ParsedStockQuery {
  originalQuery: string;
  normalizedQuestion: string;
  category: SummaryCategory;
  timeframe?: SummaryTimeframe;
  entities: ParsedStockEntity[];
  stockSymbol?: string;
  stockName?: string;
  needsClarification: boolean;
  wantsNews: boolean;
  wantsTechnical: boolean;
  debug: {
    hasMarketKeyword: boolean;
    hasStockIntent: boolean;
    compactQuestion: string;
  };
}

export interface SummaryResult {
  ok: boolean;
  symbol: string;
  model: string;
  text: string;
  responseId?: string;
  toolCalls: string[];
  parsedQuery?: ParsedStockQuery;
  groundedData: {
    quote?: QuoteResult;
    candles?: CandleSeriesResult;
    news?: NewsResult;
    compare?: Array<{
      quote: QuoteResult;
      candles?: CandleSeriesResult;
      news?: NewsResult;
    }>;
    sector?: string;
    members?: Array<{
      quote: QuoteResult;
      candles?: CandleSeriesResult;
      news?: NewsResult;
    }>;
    [key: string]: unknown;
  };
}

export interface MarketDataProvider {
  readonly name: MarketProviderName;
  readonly market: "global" | "kr";
  readonly supportsNews: boolean;
  isConfigured(): boolean;
  getQuote(symbol: string): Promise<QuoteResult>;
  getDailyCandles(symbol: string, points: number): Promise<CandleSeriesResult>;
  getNews(symbol: string, limit: number): Promise<NewsResult>;
}
