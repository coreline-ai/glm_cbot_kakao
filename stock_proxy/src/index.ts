import { loadConfig } from "./config.js";
import { createStockProxyServer } from "./server.js";

async function main(): Promise<void> {
  const config = loadConfig();
  const server = createStockProxyServer(config);
  await server.listen();
  process.stdout.write(
    `[stock_proxy] listening on http://${config.host}:${config.port} providers=alpha_vantage+naver_domestic mode=auto openai=${config.openAiApiKey ? "on" : "off"}\n`
  );
}

main().catch((error) => {
  process.stderr.write(`[stock_proxy] failed: ${error instanceof Error ? error.message : String(error)}\n`);
  process.exitCode = 1;
});
