import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { URL } from "node:url";
import type { ChatRequestBody, ProxyConfig } from "./types.js";
import { CodexClient } from "./codex/client.js";

function sendJson(res: ServerResponse, statusCode: number, body: unknown): void {
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
  });
  res.end(`${JSON.stringify(body, null, 2)}\n`);
}

async function readJsonBody<T>(req: IncomingMessage): Promise<T> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }

  const raw = Buffer.concat(chunks).toString("utf-8").trim();
  if (!raw) {
    return {} as T;
  }
  return JSON.parse(raw) as T;
}

export function createProxyServer(config: ProxyConfig) {
  const codex = new CodexClient(config);

  const server = createServer(async (req, res) => {
    try {
      const method = req.method ?? "GET";
      const url = new URL(req.url ?? "/", `http://${config.host}:${config.port}`);

      if (method === "GET" && url.pathname === "/health") {
        sendJson(res, 200, {
          ok: true,
          service: "codex_proxy",
          host: config.host,
          port: config.port,
          provider: "codex",
          model: config.codexModel,
        });
        return;
      }

      if (method === "GET" && url.pathname === "/api/v1/auth/status") {
        const status = await codex.getAuthStatus();
        sendJson(res, status.ok ? 200 : 503, status);
        return;
      }

      if (method === "GET" && url.pathname === "/api/v1/providers") {
        const status = await codex.getAuthStatus();
        sendJson(res, 200, {
          providers: [
            {
              name: "codex",
              ok: status.ok,
              model: config.codexModel,
              reason: status.reason,
            },
          ],
        });
        return;
      }

      if (method === "POST" && url.pathname === "/api/v1/chat") {
        const body = await readJsonBody<ChatRequestBody>(req);
        const result = await codex.chat(body);
        sendJson(res, 200, result);
        return;
      }

      if (method === "POST" && url.pathname === "/api/v1/self-test") {
        const result = await codex.chat({
          prompt: "Reply with a short Korean greeting.",
        });
        sendJson(res, 200, result);
        return;
      }

      sendJson(res, 404, {
        ok: false,
        error: `route not found: ${url.pathname}`,
      });
    } catch (error) {
      sendJson(res, 500, {
        ok: false,
        error: error instanceof Error ? error.message : "unknown server error",
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
    },
  };
}
