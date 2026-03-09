import { existsSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import type { CodexStoredTokens } from "../types.js";

const CODEX_OAUTH_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann";
const CODEX_OAUTH_TOKEN_URL = "https://auth.openai.com/oauth/token";

interface CodexCliAuthFile {
  auth_mode?: string;
  OPENAI_API_KEY?: string | null;
  tokens?: {
    id_token?: string;
    access_token?: string;
    refresh_token?: string;
    account_id?: string;
  };
  last_refresh?: string;
}

interface ProxyAuthFile {
  access_token?: string;
  refresh_token?: string;
  expires_at?: number;
  chatgpt_account_id?: string;
}

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  const parts = token.split(".");
  if (parts.length !== 3 || !parts[1]) {
    return null;
  }

  try {
    const json = Buffer.from(parts[1], "base64url").toString("utf-8");
    return JSON.parse(json) as Record<string, unknown>;
  } catch {
    return null;
  }
}

function extractAccountIdFromAccessToken(accessToken: string): string | undefined {
  const payload = decodeJwtPayload(accessToken);
  const authClaim = payload?.["https://api.openai.com/auth"];
  if (!authClaim || typeof authClaim !== "object") {
    return undefined;
  }
  const accountId = (authClaim as { chatgpt_account_id?: unknown }).chatgpt_account_id;
  return typeof accountId === "string" ? accountId : undefined;
}

function extractJwtExpiry(accessToken: string): number | undefined {
  const payload = decodeJwtPayload(accessToken);
  const exp = payload?.exp;
  return typeof exp === "number" ? exp * 1000 : undefined;
}

function parseCodexAuthPayload(sourcePath: string, raw: unknown): CodexStoredTokens | null {
  if (!raw || typeof raw !== "object") {
    return null;
  }

  const asCli = raw as CodexCliAuthFile;
  if (asCli.tokens?.access_token && asCli.tokens.refresh_token) {
    return {
      sourcePath,
      sourceFormat: "codex-cli",
      accessToken: asCli.tokens.access_token,
      refreshToken: asCli.tokens.refresh_token,
      accountId: asCli.tokens.account_id ?? extractAccountIdFromAccessToken(asCli.tokens.access_token),
      expiresAt: extractJwtExpiry(asCli.tokens.access_token),
      idToken: asCli.tokens.id_token,
    };
  }

  const asProxy = raw as ProxyAuthFile;
  if (asProxy.access_token && asProxy.refresh_token) {
    return {
      sourcePath,
      sourceFormat: "proxy",
      accessToken: asProxy.access_token,
      refreshToken: asProxy.refresh_token,
      accountId: asProxy.chatgpt_account_id ?? extractAccountIdFromAccessToken(asProxy.access_token),
      expiresAt: asProxy.expires_at ?? extractJwtExpiry(asProxy.access_token),
    };
  }

  return null;
}

async function persistCodexTokens(tokens: CodexStoredTokens): Promise<void> {
  await mkdir(dirname(tokens.sourcePath), { recursive: true });

  if (tokens.sourceFormat === "codex-cli") {
    const payload: CodexCliAuthFile = {
      auth_mode: "chatgpt",
      OPENAI_API_KEY: null,
      tokens: {
        id_token: tokens.idToken,
        access_token: tokens.accessToken,
        refresh_token: tokens.refreshToken,
        account_id: tokens.accountId,
      },
      last_refresh: new Date().toISOString(),
    };
    await writeFile(tokens.sourcePath, `${JSON.stringify(payload, null, 2)}\n`, { mode: 0o600 });
    return;
  }

  const payload: ProxyAuthFile = {
    access_token: tokens.accessToken,
    refresh_token: tokens.refreshToken,
    expires_at: tokens.expiresAt ?? extractJwtExpiry(tokens.accessToken),
    chatgpt_account_id: tokens.accountId,
  };
  await writeFile(tokens.sourcePath, `${JSON.stringify(payload, null, 2)}\n`, { mode: 0o600 });
}

export async function loadCodexTokens(primaryPath: string, fallbackPath: string): Promise<CodexStoredTokens | null> {
  for (const filePath of [primaryPath, fallbackPath]) {
    if (!existsSync(filePath)) {
      continue;
    }

    try {
      const raw = JSON.parse(await readFile(filePath, "utf-8")) as unknown;
      const parsed = parseCodexAuthPayload(filePath, raw);
      if (parsed) {
        return parsed;
      }
    } catch {
      continue;
    }
  }

  return null;
}

function shouldRefreshCodexToken(tokens: Pick<CodexStoredTokens, "expiresAt">, now = Date.now()): boolean {
  if (!tokens.expiresAt) {
    return false;
  }
  return now >= tokens.expiresAt - 5 * 60 * 1000;
}

export async function refreshCodexTokens(tokens: CodexStoredTokens): Promise<CodexStoredTokens> {
  const response = await fetch(CODEX_OAUTH_TOKEN_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams({
      grant_type: "refresh_token",
      refresh_token: tokens.refreshToken,
      client_id: CODEX_OAUTH_CLIENT_ID,
    }),
  });

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new Error(`refresh failed: ${response.status} ${body}`.trim());
  }

  const payload = (await response.json()) as {
    access_token?: string;
    refresh_token?: string;
    expires_in?: number;
    id_token?: string;
  };

  if (!payload.access_token || !payload.refresh_token) {
    throw new Error("refresh failed: invalid token response");
  }

  const refreshed: CodexStoredTokens = {
    ...tokens,
    accessToken: payload.access_token,
    refreshToken: payload.refresh_token,
    accountId: extractAccountIdFromAccessToken(payload.access_token) ?? tokens.accountId,
    expiresAt:
      typeof payload.expires_in === "number"
        ? Date.now() + payload.expires_in * 1000
        : extractJwtExpiry(payload.access_token),
    idToken: payload.id_token ?? tokens.idToken,
  };

  await persistCodexTokens(refreshed);
  return refreshed;
}

export async function getValidCodexTokens(primaryPath: string, fallbackPath: string): Promise<CodexStoredTokens | null> {
  const tokens = await loadCodexTokens(primaryPath, fallbackPath);
  if (!tokens) {
    return null;
  }
  if (!shouldRefreshCodexToken(tokens)) {
    return tokens;
  }
  return refreshCodexTokens(tokens);
}
