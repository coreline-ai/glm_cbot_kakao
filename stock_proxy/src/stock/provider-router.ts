import type { MarketDataProvider, StockProxyConfig } from "../types.js";
import { AlphaVantageClient } from "./alpha-vantage.js";
import { NaverDomesticClient } from "./naver-domestic.js";

export function createProviders(config: StockProxyConfig): {
  alphaProvider: AlphaVantageClient;
  naverProvider: NaverDomesticClient;
} {
  return {
    alphaProvider: new AlphaVantageClient(config),
    naverProvider: new NaverDomesticClient(config)
  };
}

export function pickProviderForSymbol(
  symbol: string,
  alphaProvider: AlphaVantageClient,
  naverProvider: NaverDomesticClient
): MarketDataProvider {
  const normalized = symbol.trim();
  if (/^\d{6}(?:\.[A-Z]{2,4})?$/.test(normalized) || /[가-힣]/.test(normalized)) {
    return naverProvider;
  }
  return alphaProvider;
}
