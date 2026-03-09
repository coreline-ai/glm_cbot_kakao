# stock_proxy

Grounded stock data proxy for the current project.

## What It Does

- routes global stock symbols to Alpha Vantage
- routes 6-digit Korean stock symbols like `005930` to Naver Finance public endpoints
- exposes HTTP endpoints for quote, candles, news, parsing, and summary
- supports Korean natural-language stock queries
- supports Korean market summary queries
- supports preset sector summary queries
- supports 2-stock comparison queries
- uses OpenAI to summarize grounded data

## Provider Strategy

- `alpha_vantage`
  - global symbols like `AAPL`, `IBM`, `MSFT`
  - requires `ALPHA_VANTAGE_API_KEY`
- `naver_domestic`
  - Korean domestic symbols like `005930`, `000660`
  - no signup or API key required
  - uses unofficial public web endpoints

## KRX Note

- Official `KRX OPEN API` is free, but it requires signup and service approval.
- For the current "free + no extra signup/auth first" requirement, this proxy uses Naver Finance public endpoints for Korean domestic stocks instead of official KRX Open API.

## Endpoints

- `GET /health`
- `GET /api/v1/providers`
- `GET /api/v1/quote?symbol=IBM`
- `GET /api/v1/quote?symbol=005930`
- `GET /api/v1/candles?symbol=IBM&points=5`
- `GET /api/v1/candles?symbol=005930&points=5`
- `GET /api/v1/news?symbol=IBM&limit=3`
- `GET /api/v1/news?symbol=005930&limit=3`
- `GET /api/v1/domestic/quote?symbol=005930`
- `GET /api/v1/domestic/candles?symbol=005930&points=30`
- `GET /api/v1/domestic/news?symbol=005930&limit=5`
- `POST /api/v1/summary`
- `POST /api/v1/parse-query`
- `POST /api/v1/self-test`

## Routing Rules

- 6-digit numeric symbols are treated as Korean domestic stocks and routed to `naver_domestic`
- everything else is routed to `alpha_vantage`
- you can override with `?provider=alpha_vantage` or `?provider=naver_domestic`

Natural-language summary routing:

- `금일 한국주식시장 정리 해줘` -> Korean market summary
- `삼성전자 최근 흐름 요약해줘` -> single-stock summary
- `하닉 오늘 어때` -> alias-resolved single-stock summary
- `반도체주 오늘 어때` -> preset sector summary
- `삼성전자랑 하닉 비교해줘` -> two-stock comparison
- ambiguous finance-like prompts return clarification text instead of a hard 500

## Run

```bash
cd stock_proxy
npm install
npm run dev
```

You can place local secrets in `stock_proxy/.env.local`.

```env
ALPHA_VANTAGE_API_KEY=your_key_here
OPENAI_API_KEY=your_openai_key_here
```

## Example

```bash
curl -s 'http://127.0.0.1:4327/api/v1/quote?symbol=IBM'
curl -s 'http://127.0.0.1:4327/api/v1/quote?symbol=005930'
curl -s 'http://127.0.0.1:4327/api/v1/domestic/news?symbol=005930&limit=3'
curl -s -X POST 'http://127.0.0.1:4327/api/v1/parse-query' \
  -H 'content-type: application/json' \
  -d '{"symbol":"하닉 오늘 어때","question":"하닉 오늘 어때"}'
curl -s -X POST 'http://127.0.0.1:4327/api/v1/summary' \
  -H 'content-type: application/json' \
  -d '{"symbol":"005930","question":"삼성전자 최근 흐름을 짧게 요약해줘","includeNews":true,"candlePoints":5}'
curl -s -X POST 'http://127.0.0.1:4327/api/v1/summary' \
  -H 'content-type: application/json' \
  -d '{"symbol":"반도체주 오늘 어때","question":"반도체주 오늘 어때","includeNews":true,"candlePoints":5}'
curl -s -X POST 'http://127.0.0.1:4327/api/v1/summary' \
  -H 'content-type: application/json' \
  -d '{"symbol":"삼성전자랑 하닉 비교해줘","question":"삼성전자랑 하닉 비교해줘","includeNews":true,"candlePoints":5}'
```

## Notes

- `ALPHA_VANTAGE_API_KEY=demo` is enough for limited global smoke tests.
- `POST /api/v1/summary` requires `OPENAI_API_KEY`.
- Naver domestic endpoints are unofficial and may change without notice.
- Android integration already exists through the `StockProxy` provider.
- Current Android bridge path is `adb reverse tcp:4327 tcp:4327`.
