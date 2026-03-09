export interface ProxyConfig {
  host: string;
  port: number;
  codexModel: string;
  codexBaseUrl: string;
  requestTimeoutMs: number;
  codexAuthPath: string;
  codexFallbackAuthPath: string;
}

export type CodexAuthFormat = "codex-cli" | "proxy";

export interface CodexStoredTokens {
  sourcePath: string;
  sourceFormat: CodexAuthFormat;
  accessToken: string;
  refreshToken: string;
  accountId?: string;
  expiresAt?: number;
  idToken?: string;
}

export interface CodexFinalResponse {
  id?: string;
  status?: string;
  output?: Array<{
    type?: string;
    role?: string;
    content?: Array<{
      type?: string;
      text?: string;
    }>;
  }>;
  usage?: {
    input_tokens?: number;
    output_tokens?: number;
    total_tokens?: number;
  };
}

export interface ChatRequestBody {
  prompt: string;
  model?: string;
  systemPrompt?: string;
  timeoutMs?: number;
  maxOutputTokens?: number;
  previousResponseId?: string;
}

export interface ChatResult {
  ok: boolean;
  provider: "codex";
  model: string;
  text: string;
  requestId?: string;
  finishReason: "stop" | "max_tokens";
  elapsedMs: number;
  usage?: {
    inputTokens?: number;
    outputTokens?: number;
    totalTokens?: number;
  };
}
