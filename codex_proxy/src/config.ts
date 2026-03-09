import { homedir } from "node:os";
import { join, resolve } from "node:path";
import dotenv from "dotenv";
import type { ProxyConfig } from "./types.js";

dotenv.config();

function toInt(value: string | undefined, fallback: number): number {
  const parsed = Number.parseInt(value ?? "", 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function resolveProjectPath(input: string): string {
  if (input.startsWith("/")) {
    return input;
  }
  return resolve(process.cwd(), input);
}

export function loadConfig(): ProxyConfig {
  const defaultAuthPath = join(homedir(), ".codex", "auth.json");
  const fallbackAuthPath = process.env.CODEX_FALLBACK_AUTH_PATH?.trim() || ".proxy-auth.json";

  return {
    host: process.env.PROXY_HOST?.trim() || "127.0.0.1",
    port: toInt(process.env.PROXY_PORT, 4317),
    codexModel: process.env.CODEX_MODEL?.trim() || "gpt-5",
    codexBaseUrl: process.env.CODEX_BASE_URL?.trim() || "https://chatgpt.com/backend-api",
    requestTimeoutMs: toInt(process.env.REQUEST_TIMEOUT_MS, 60_000),
    codexAuthPath: process.env.CODEX_AUTH_PATH?.trim() || defaultAuthPath,
    codexFallbackAuthPath: resolveProjectPath(fallbackAuthPath),
  };
}
