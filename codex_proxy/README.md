# codex_proxy

Standalone Codex OAuth proxy module.

## Scope

- Loads Codex OAuth tokens from the local machine
- Refreshes them when needed
- Calls the Codex backend directly
- Exposes a small HTTP API for Android integration

This module is isolated from direct Android OAuth handling, but Android integration is already implemented through a dedicated provider.

## Endpoints

- `GET /health`
- `GET /api/v1/auth/status`
- `GET /api/v1/providers`
- `POST /api/v1/chat`
- `POST /api/v1/self-test`

## Local Run

```bash
cd codex_proxy
npm install
npm run dev
```

## Build

```bash
cd codex_proxy
npm run build
```

## Example Request

```bash
curl -s http://127.0.0.1:4317/api/v1/chat \
  -H 'content-type: application/json' \
  -d '{"prompt":"Say hi in Korean."}'
```

## Notes

- Default token source is `~/.codex/auth.json`.
- If a local fallback token file is needed, set `CODEX_FALLBACK_AUTH_PATH`.
- This proxy uses the Codex ChatGPT OAuth flow. It is not an OpenAI API key proxy.
- Current Android development bridge path is `adb reverse tcp:4317 tcp:4317`.
- Android `Codex Proxy` provider depends on a host machine that already has valid Codex OAuth state.
