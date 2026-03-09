import type { ChatRequestBody, ChatResult, CodexFinalResponse, ProxyConfig } from "../types.js";
import { getValidCodexTokens } from "../auth/codex.js";
import { parseSseEvents } from "../shared/sse.js";

function extractFinalText(response: CodexFinalResponse | null): string {
  if (!response?.output) {
    return "";
  }

  return response.output
    .flatMap((item) => item.content ?? [])
    .filter((content) => content.type === "output_text" && typeof content.text === "string")
    .map((content) => content.text as string)
    .join("");
}

function normalizePrompt(prompt: string): string {
  return prompt.replace(/\r\n/g, "\n").trim();
}

export class CodexClient {
  constructor(private readonly config: ProxyConfig) {}

  async getAuthStatus(): Promise<{
    ok: boolean;
    sourcePath?: string;
    accountIdPresent: boolean;
    refreshable: boolean;
    expiresAt?: number;
    reason?: string;
  }> {
    try {
      const tokens = await getValidCodexTokens(this.config.codexAuthPath, this.config.codexFallbackAuthPath);
      if (!tokens) {
        return {
          ok: false,
          accountIdPresent: false,
          refreshable: false,
          reason: "missing codex auth",
        };
      }

      return {
        ok: Boolean(tokens.accountId),
        sourcePath: tokens.sourcePath,
        accountIdPresent: Boolean(tokens.accountId),
        refreshable: Boolean(tokens.refreshToken),
        expiresAt: tokens.expiresAt,
        reason: tokens.accountId ? undefined : "missing codex account id",
      };
    } catch (error) {
      return {
        ok: false,
        accountIdPresent: false,
        refreshable: false,
        reason: error instanceof Error ? error.message : "unknown auth error",
      };
    }
  }

  async chat(input: ChatRequestBody): Promise<ChatResult> {
    const prompt = normalizePrompt(input.prompt);
    if (!prompt) {
      throw new Error("prompt is required");
    }

    const tokens = await getValidCodexTokens(this.config.codexAuthPath, this.config.codexFallbackAuthPath);
    if (!tokens) {
      throw new Error("missing codex auth");
    }
    if (!tokens.accountId) {
      throw new Error("missing codex account id");
    }

    const model = input.model?.trim() || this.config.codexModel;
    const startedAt = Date.now();
    const response = await fetch(`${this.config.codexBaseUrl}/codex/responses`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${tokens.accessToken}`,
        "chatgpt-account-id": tokens.accountId,
        "OpenAI-Beta": "responses=experimental",
        originator: "codex_cli_rs",
      },
      body: JSON.stringify({
        model,
        instructions: input.systemPrompt?.trim() || "",
        input: [
          {
            type: "message",
            role: "user",
            content: [
              {
                type: "input_text",
                text: prompt,
              },
            ],
          },
        ],
        stream: true,
        store: false,
        reasoning: {
          effort: "medium",
          summary: "auto",
        },
        text: {
          verbosity: "medium",
        },
        ...(input.previousResponseId ? { previous_response_id: input.previousResponseId } : {}),
      }),
      signal: AbortSignal.timeout(input.timeoutMs ?? this.config.requestTimeoutMs),
    });

    if (!response.ok) {
      const body = await response.text().catch(() => "");
      throw new Error(`codex request failed: ${response.status} ${body}`.trim());
    }

    if (!response.body) {
      throw new Error("invalid provider response");
    }

    let finalResponse: CodexFinalResponse | null = null;
    let accumulated = "";

    for await (const event of parseSseEvents(response.body)) {
      if (event.data === "[DONE]") {
        break;
      }

      const parsed = JSON.parse(event.data) as {
        type?: string;
        delta?: string;
        response?: CodexFinalResponse;
      };

      if (parsed.type === "response.output_text.delta" && typeof parsed.delta === "string") {
        accumulated += parsed.delta;
      }

      if ((parsed.type === "response.done" || parsed.type === "response.completed") && parsed.response) {
        finalResponse = parsed.response;
      }
    }

    const text = accumulated || extractFinalText(finalResponse);
    return {
      ok: true,
      provider: "codex",
      model,
      text,
      requestId: finalResponse?.id,
      finishReason: finalResponse?.status === "incomplete" ? "max_tokens" : "stop",
      elapsedMs: Date.now() - startedAt,
      usage: finalResponse?.usage
        ? {
            inputTokens: finalResponse.usage.input_tokens,
            outputTokens: finalResponse.usage.output_tokens,
            totalTokens: finalResponse.usage.total_tokens,
          }
        : undefined,
    };
  }
}
