# Stock Proxy Plan

## Goal

Add a separate `stock_proxy/` module that provides grounded stock data endpoints and an OpenAI-powered summary route for the current project.

## Guardrails

- `android_client/` remains unchanged in phase 1.
- `ref/` remains read-only.
- `stock_proxy/` is isolated from `codex_proxy/`.
- Raw market data comes from a stock data provider.
- OpenAI is used to interpret data, not to invent live prices.

## Architecture

```text
Android app or local client
  -> stock_proxy
     -> query parser
     -> Alpha Vantage API
     -> Naver domestic endpoints
     -> OpenAI Responses API
```

## Why This Shape

- price, volume, and chart data should come from a deterministic source
- LLMs are useful for summarization, explanation, and Q&A over fetched data
- proxy-side orchestration avoids shipping finance keys into the Android app
- later Android integration can use a stable local/LAN HTTP contract

## Provider Choice

Current shape uses two raw-data providers:

- `Alpha Vantage`
  - global stocks
  - quote, daily candles, news
- `Naver public endpoints`
  - domestic stocks and Korean market summary
  - no signup/auth required
  - unofficial web endpoint, so schema can change

## Endpoints

- `GET /health`
- `GET /api/v1/providers`
- `GET /api/v1/quote?symbol=IBM`
- `GET /api/v1/candles?symbol=IBM&interval=daily&points=5`
- `GET /api/v1/news?symbol=IBM&limit=5`
- `POST /api/v1/summary`
- `POST /api/v1/parse-query`
- `POST /api/v1/self-test`

`POST /api/v1/parse-query` is intended for parser debugging and routing inspection.

## Natural Language Parsing

`stock_proxy` now parses Korean natural-language stock questions before selecting a provider.

Parsing pipeline:

1. normalize question
2. detect market/stock/sector intent
3. resolve explicit 6-digit ticker
4. match alias dictionary
5. extract company-like candidates
6. decide category:
   - `market_summary`
   - `stock_summary`
   - `stock_compare`
   - `sector_summary`
   - `unknown`

Examples:

- `금일 한국주식시장 정리 해줘` -> `market_summary`
- `삼성전자 최근 흐름 요약해줘` -> `stock_summary`, `005930`
- `삼전 오늘 어때` -> `stock_summary`, `005930`
- `삼성전자 금일 통향 정리 해줘` -> `stock_summary`, `005930`
- `반도체주 오늘 어때` -> `sector_summary`, `반도체`
- `삼성전자랑 하닉 비교해줘` -> `stock_compare`, `005930`, `000660`

If the parser cannot confidently identify a stock but the query still looks finance-related, the proxy returns a clarification message instead of a hard 500.

Current supported higher-level flows:

- market summary
- single-stock summary
- preset sector summary
- two-stock comparison

## Current Status

- [x] Isolated `stock_proxy/` module created
- [x] Alpha Vantage global stock routes implemented
- [x] Naver domestic quote/candles/news routes implemented
- [x] Korean market summary route implemented
- [x] OpenAI grounded summary implemented
- [x] Natural-language stock query parser implemented
- [x] Alias dictionary for common domestic stock nicknames implemented
- [x] Sector preset dictionary implemented
- [x] Sector summary implemented
- [x] Two-stock compare summary implemented
- [x] `POST /api/v1/parse-query` debug endpoint implemented
- [x] Android `StockProxy` integration implemented
- [ ] Broader sector coverage beyond current presets
- [ ] Multi-stock compare beyond 2 stocks
- [ ] Clarification loop back into Android UI

## Android Integration Status

- Android already includes `Stock Proxy` as a runtime-selectable provider.
- Current bridge path is:
  - `adb reverse tcp:4327 tcp:4327`
  - Android `StockProxy` provider -> `http://127.0.0.1:4327/`
- Android routing behavior:
  - stock/market-like prompt -> `StockProxy`
  - non-stock prompt while `StockProxy` is selected -> OpenAI fallback in app

## Demo Key Limitation

- Alpha Vantage `demo` key is enough for limited smoke tests.
- `GLOBAL_QUOTE` and `TIME_SERIES_DAILY` worked in verification.
- `NEWS_SENTIMENT` rejected the `demo` key, so a real Alpha Vantage key is required for reliable news coverage.
- The summary route degrades gracefully when news is unavailable.

## `POST /api/v1/summary`

Request:

```json
{
  "symbol": "IBM",
  "question": "IBM 최근 흐름 요약해줘",
  "includeNews": true,
  "candlePoints": 5
}
```

Behavior:

- parse question into category/entity/timeframe
- route market-wide questions to Korean market summarizer
- route sector questions to preset-based sector summarizer
- route 2-stock comparison questions to compare summarizer
- resolve stock symbol/name for single-stock questions
- fetch quote
- fetch recent candles
- optionally fetch recent news
- call OpenAI with tool-calling to access those functions as needed
- return grounded summary text, parsed metadata, and source payload metadata

## OpenAI Strategy

Phase 1 uses OpenAI Responses API with custom function tools:

- `get_quote`
- `get_recent_candles`
- `get_recent_news`

The model can choose which tools to call. The proxy executes them and returns tool outputs until a final answer is produced.

## Non-Goals For Phase 1

- broker integration
- order execution
- portfolio sync
- Android UI wiring
- direct mobile-side finance API keys

## Success Criteria

- `stock_proxy` builds and runs independently
- deterministic market data endpoints work
- OpenAI summary route works when an OpenAI key is configured
- Android app can consume the proxy through the existing provider switch
