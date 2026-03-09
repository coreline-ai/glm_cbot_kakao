import dotenv from "dotenv";
import type { StockProxyConfig } from "./types.js";

dotenv.config({ path: ".env.local", override: true });
dotenv.config();

function toInt(value: string | undefined, fallback: number): number {
  const parsed = Number.parseInt(value ?? "", 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function loadConfig(): StockProxyConfig {
  return {
    host: process.env.PROXY_HOST?.trim() || "127.0.0.1",
    port: toInt(process.env.PROXY_PORT, 4327),
    alphaVantageApiKey: process.env.ALPHA_VANTAGE_API_KEY?.trim() || "demo",
    alphaVantageBaseUrl: process.env.ALPHA_VANTAGE_BASE_URL?.trim() || "https://www.alphavantage.co/query",
    openAiApiKey: process.env.OPENAI_API_KEY?.trim() || "",
    openAiBaseUrl: process.env.OPENAI_BASE_URL?.trim() || "https://api.openai.com/v1",
    openAiModel: process.env.OPENAI_MODEL?.trim() || "gpt-5.4",
    requestTimeoutMs: toInt(process.env.REQUEST_TIMEOUT_MS, 60_000)
  };
}
