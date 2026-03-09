# Codex Proxy Rollout Plan

## Goal

Introduce Codex OAuth access through a separate proxy/router module while keeping existing direct GLM/OpenAI behavior intact.

## Guardrails

- `ref/` is reference-only.
- All new work for Codex OAuth lives under `codex_proxy/`.
- The proxy must be runnable on its own and testable before any Android integration starts.

## Why This Shape

Directly embedding Codex OAuth inside the Android app would couple mobile UI, token lifecycle, SSE parsing, and ChatGPT-specific backend headers in one place. That increases break risk and makes token handling harder to control.

An isolated proxy gives us:

- a single place for OAuth token loading and refresh
- one transport for the Codex backend API
- a stable local HTTP contract the Android app can adopt later
- no regression risk for the current GLM/OpenAI Android flow

## Phase 1

- [x] Lock `ref/` as read-only in workspace rules
- [x] Define isolated rollout plan
- [x] Create standalone `codex_proxy/` module
- [x] Implement Codex OAuth token loading and refresh
- [x] Implement minimal Codex direct request path
- [x] Expose `health`, `auth status`, `providers`, `chat`, and `self-test` endpoints
- [x] Verify the proxy builds and runs without Android changes

## Phase 2

- [x] Add a new Android provider option that targets the proxy
- [x] Keep existing direct GLM/OpenAI providers intact
- [x] Add runtime provider selection in Android UI
- [ ] Add fallback handling from Codex proxy failure to another provider

## Phase 3

- [ ] Optional LAN deployment or localhost bridge strategy
- [ ] Optional auth gate between Android and proxy
- [ ] Optional richer `responses` contract with streamed output

## Minimal HTTP Contract

### `GET /health`

Returns process health and current config summary.

### `GET /api/v1/auth/status`

Returns whether Codex OAuth tokens are present and whether refresh/account data are usable.

### `GET /api/v1/providers`

Returns current provider availability. Phase 1 exposes only `codex`.

### `POST /api/v1/chat`

Accepts a single prompt and returns a single normalized text response.

### `POST /api/v1/self-test`

Runs a smoke test prompt through Codex and returns the normalized response.

## Non-Goals For The Current Rollout

- no phone-standalone Codex OAuth flow
- no streaming endpoint for the mobile app yet
- no production auth layer in front of the proxy yet
- no public-network deployment hardening yet

## Current Reality

Phase 1 and most of Phase 2 are complete.

- `codex_proxy/` is implemented and runnable
- Android has a `Codex Proxy` provider
- Current development bridge path is:
  - Mac host runs `codex_proxy`
  - Android uses `adb reverse tcp:4317 tcp:4317`
  - Android provider hits `http://127.0.0.1:4317/`

Important operational constraint:

- this is not phone-standalone OAuth
- the Android app depends on a host machine that already has Codex OAuth state

## Success Criteria

- `codex_proxy` builds and starts independently
- It can load Codex OAuth state from the local machine
- It can refresh tokens when needed
- It can call the Codex backend and return text
- Android can consume it through the current `Codex Proxy` provider without regressing direct GLM/OpenAI paths
