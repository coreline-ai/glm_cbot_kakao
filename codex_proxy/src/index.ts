import { createProxyServer } from "./server.js";
import { loadConfig } from "./config.js";

async function main(): Promise<void> {
  const config = loadConfig();
  const server = createProxyServer(config);
  await server.listen();
  process.stdout.write(
    `[codex_proxy] listening on http://${config.host}:${config.port} model=${config.codexModel}\n`
  );
}

main().catch((error) => {
  process.stderr.write(`[codex_proxy] failed: ${error instanceof Error ? error.message : String(error)}\n`);
  process.exitCode = 1;
});
