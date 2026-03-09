import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { URL } from "node:url";
import type { StockProxyConfig, SummaryRequest } from "./types.js";
import { AlphaVantageClient } from "./stock/alpha-vantage.js";
import { NaverDomesticClient } from "./stock/naver-domestic.js";
import { NaverKoreanMarketClient } from "./market/naver-korean-market.js";
import { OpenAiStockSummarizer } from "./openai/responses.js";
import { OpenAiKoreanMarketSummarizer } from "./openai/korean-market-summary.js";
import { OpenAiStockCompareSummarizer } from "./openai/stock-compare-summary.js";
import { OpenAiSectorSummarizer } from "./openai/sector-summary.js";
import { createProviders, pickProviderForSymbol } from "./stock/provider-router.js";
import { parseStockQuery } from "./query/stock-query-parser.js";

function sendJson(res: ServerResponse, statusCode: number, body: unknown): void {
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  });
  res.end(`${JSON.stringify(body, null, 2)}\n`);
}

async function readJsonBody<T>(req: IncomingMessage): Promise<T> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }

  const raw = Buffer.concat(chunks).toString("utf-8").trim();
  return raw ? JSON.parse(raw) as T : {} as T;
}

function requireQuery(url: URL, key: string): string {
  const value = url.searchParams.get(key)?.trim();
  if (!value) {
    throw new Error(`${key} is required`);
  }
  return value;
}

export function createStockProxyServer(config: StockProxyConfig) {
  const { alphaProvider, naverProvider } = createProviders(config);
  const marketClient = new NaverKoreanMarketClient(config);

  const server = createServer(async (req, res) => {
    try {
      const method = req.method ?? "GET";
      const url = new URL(req.url ?? "/", `http://${config.host}:${config.port}`);

      if (method === "GET" && url.pathname === "/health") {
        sendJson(res, 200, {
          ok: true,
          service: "stock_proxy",
          host: config.host,
          port: config.port,
          dataProviders: ["alpha_vantage", "naver_domestic"],
          openAiConfigured: Boolean(config.openAiApiKey)
        });
        return;
      }

      if (method === "GET" && url.pathname === "/api/v1/providers") {
        sendJson(res, 200, {
          providers: {
            alpha_vantage: {
              market: alphaProvider.market,
              supportsNews: alphaProvider.supportsNews
            },
            naver_domestic: {
              market: naverProvider.market,
              supportsNews: naverProvider.supportsNews,
              note: "No signup/auth required. Unofficial public web endpoint."
            },
            openAi: config.openAiApiKey ? config.openAiModel : null
          }
        });
        return;
      }

      if (method === "GET" && url.pathname === "/api/v1/quote") {
        const symbol = requireQuery(url, "symbol");
        const provider = resolveProvider(url.searchParams.get("provider"), symbol, alphaProvider, naverProvider);
        sendJson(res, 200, await provider.getQuote(symbol));
        return;
      }

      if (method === "GET" && url.pathname === "/api/v1/candles") {
        const symbol = requireQuery(url, "symbol");
        const points = Number.parseInt(url.searchParams.get("points") ?? "5", 10);
        const provider = resolveProvider(url.searchParams.get("provider"), symbol, alphaProvider, naverProvider);
        sendJson(res, 200, await provider.getDailyCandles(symbol, points));
        return;
      }

      if (method === "GET" && url.pathname === "/api/v1/news") {
        const symbol = requireQuery(url, "symbol");
        const limit = Number.parseInt(url.searchParams.get("limit") ?? "3", 10);
        const provider = resolveProvider(url.searchParams.get("provider"), symbol, alphaProvider, naverProvider);
        sendJson(res, 200, await provider.getNews(symbol, limit));
        return;
      }

      if (method === "GET" && url.pathname === "/api/v1/domestic/quote") {
        const symbol = requireQuery(url, "symbol");
        sendJson(res, 200, await naverProvider.getQuote(symbol));
        return;
      }

      if (method === "GET" && url.pathname === "/api/v1/domestic/candles") {
        const symbol = requireQuery(url, "symbol");
        const points = Number.parseInt(url.searchParams.get("points") ?? "30", 10);
        sendJson(res, 200, await naverProvider.getDailyCandles(symbol, points));
        return;
      }

      if (method === "GET" && url.pathname === "/api/v1/domestic/news") {
        const symbol = requireQuery(url, "symbol");
        const limit = Number.parseInt(url.searchParams.get("limit") ?? "5", 10);
        sendJson(res, 200, await naverProvider.getNews(symbol, limit));
        return;
      }

      if (method === "POST" && url.pathname === "/api/v1/summary") {
        const body = await readJsonBody<SummaryRequest>(req);
        const parsedQuery = parseStockQuery(body);

        if (parsedQuery.category === "market_summary" || isKoreanMarketSummaryQuery(body.question || body.symbol)) {
          const summarizer = new OpenAiKoreanMarketSummarizer(config, marketClient);
          sendJson(res, 200, await summarizer.summarize(body.question || body.symbol));
          return;
        }

        if (parsedQuery.category === "sector_summary") {
          const summarizer = new OpenAiSectorSummarizer(config, naverProvider);
          sendJson(res, 200, await summarizer.summarize(parsedQuery));
          return;
        }

        if (parsedQuery.category === "stock_compare") {
          const summarizer = new OpenAiStockCompareSummarizer(config, naverProvider);
          sendJson(res, 200, await summarizer.summarize(parsedQuery));
          return;
        }

        if (parsedQuery.needsClarification) {
          sendJson(res, 200, {
            ok: true,
            symbol: parsedQuery.stockSymbol ?? "UNKNOWN",
            model: config.openAiModel,
            text: "종목명이 조금 모호합니다. 종목명이나 6자리 종목코드를 더 구체적으로 알려주세요. 예: 삼성전자, SK하이닉스, 005930",
            toolCalls: [],
            parsedQuery,
            groundedData: {}
          });
          return;
        }

        const effectiveSymbol = parsedQuery.stockSymbol ?? parsedQuery.stockName ?? body.symbol;
        const effectiveQuestion = parsedQuery.normalizedQuestion || body.question || body.symbol;
        const provider = resolveProvider(url.searchParams.get("provider"), effectiveSymbol, alphaProvider, naverProvider);
        const summarizer = new OpenAiStockSummarizer(config, provider);
        sendJson(res, 200, await summarizer.summarize({
          ...body,
          symbol: effectiveSymbol,
          question: effectiveQuestion
        }, parsedQuery));
        return;
      }

      if (method === "POST" && url.pathname === "/api/v1/parse-query") {
        const body = await readJsonBody<SummaryRequest>(req);
        sendJson(res, 200, {
          ok: true,
          parsedQuery: parseStockQuery(body)
        });
        return;
      }

      if (method === "POST" && url.pathname === "/api/v1/self-test") {
        const globalQuote = await alphaProvider.getQuote("IBM");
        const domesticQuote = await naverProvider.getQuote("005930");
        sendJson(res, 200, {
          ok: true,
          globalQuote,
          domesticQuote,
          summaryAvailable: Boolean(config.openAiApiKey)
        });
        return;
      }

      sendJson(res, 404, { ok: false, error: `route not found: ${url.pathname}` });
    } catch (error) {
      sendJson(res, 500, {
        ok: false,
        error: error instanceof Error ? error.message : "unknown server error"
      });
    }
  });

  return {
    listen(): Promise<void> {
      return new Promise((resolve, reject) => {
        server.listen(config.port, config.host, () => resolve());
        server.once("error", reject);
      });
    },
    close(): Promise<void> {
      return new Promise((resolve, reject) => {
        server.close((error) => {
          if (error) {
            reject(error);
            return;
          }
          resolve();
        });
      });
    }
  };
}

function isKoreanMarketSummaryQuery(query: string | undefined): boolean {
  const normalized = (query ?? "").replace(/\s+/g, "").toLowerCase();
  if (!normalized) {
    return false;
  }
  if (/\d{6}/.test(normalized)) {
    return false;
  }
  return [
    "한국주식시장",
    "국내주식시장",
    "주식시장",
    "증시",
    "장마감",
    "시장정리",
    "시황",
    "코스피",
    "코스닥"
  ].some((keyword) => normalized.includes(keyword));
}

function resolveProvider(
  providerName: string | null,
  symbol: string,
  alphaProvider: AlphaVantageClient,
  naverProvider: NaverDomesticClient
) {
  if (providerName === "naver" || providerName === "naver_domestic" || providerName === "krx") {
    return naverProvider;
  }
  if (providerName === "alpha" || providerName === "alpha_vantage") {
    return alphaProvider;
  }
  return pickProviderForSymbol(symbol, alphaProvider, naverProvider);
}
