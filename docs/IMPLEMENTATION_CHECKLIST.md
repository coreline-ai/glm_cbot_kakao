# Current Implementation Checklist

## Purpose

This document tracks the actual implementation state of the current project as of the latest local verification.  
`[x]` means implemented and verified locally in code or runtime.  
`[ ]` means not implemented yet, only partially implemented, or not fully verified.

Reference folders are out of scope:

- `ref/` is read-only

## 1. Workspace / Guardrails

- [x] `AGENTS.md` defines `ref/` as read-only
- [x] Active implementation lives outside `ref/`
- [x] Current active modules are `android_client/`, `stock_proxy/`, and `codex_proxy/`

## 2. Android App Core

- [x] Android app runs as the primary notification listener / auto-reply app
- [x] Notification flow is centered in `android_client/`
- [x] App uses manual DI via `CBotApplication` and `AppContainer`
- [x] Monitoring UI and service share one in-memory monitoring store
- [x] Existing terminal-style UI has been preserved
- [x] Provider selection is available in the app UI
- [x] Response target mode selection is available in the app UI

## 3. Android Providers

- [x] Direct `GLM` provider is implemented
- [x] Direct `OpenAI` provider is implemented
- [x] `Codex Proxy` provider is implemented
- [x] `Stock Proxy` provider is implemented
- [x] Runtime provider switching is supported from the UI
- [x] Provider-specific health / ready state is reflected in monitoring UI

## 4. Notification Auto-Reply Flow

- [x] KakaoTalk notifications are filtered by package
- [x] Wake word `코비서` is required
- [x] Incoming notification text is normalized before LLM routing
- [x] Duplicate notification reply prevention is implemented
- [x] Reply uses `RemoteInput` / reply action based flow
- [x] Failure does not force-send a bad reply
- [ ] Long message burst throttling is not implemented yet
- [ ] Full device-state resilience under process death / OEM background restrictions is not fully documented

## 5. Prompt / Memory / Reply Policies

- [x] Prompt building is separated from notification plumbing
- [x] Reply sanitization is separated
- [x] In-memory room-scoped conversation memory is implemented
- [x] Memory expiration and turn limits are implemented
- [x] Memory is only persisted in process memory
- [x] Memory use is logged in monitoring output
- [x] Reply length was expanded from the original short limit
- [ ] Persistent cross-process memory is not implemented
- [ ] Formal unsafe-content / forbidden-reply filtering is not implemented

## 6. Security / Secret Handling

- [x] GLM/OpenAI app keys are not stored directly in Kotlin source
- [x] Native secret loading path exists through JNI
- [x] `secrets.properties` local injection path exists
- [x] `android_client/secrets.properties` is ignored by git
- [x] Package / cert based embedded-secret guard exists
- [x] Debug build blocks embedded production-secret use
- [x] `usesCleartextTraffic=false` is enabled for the Android app
- [x] Proxy traffic to localhost is handled through network config / localhost routing
- [ ] Embedded secret approach is still best-effort obfuscation, not strong secret protection
- [ ] Formal key rotation / revocation playbook is not documented

## 7. GLM / OpenAI / Codex Runtime Status

- [x] GLM direct API connectivity was verified with a live request
- [x] OpenAI direct API connectivity was verified with a live request
- [x] OpenAI `gpt-5.x` compatibility was fixed using `max_completion_tokens`
- [x] Codex proxy can use host OAuth state from the local Mac
- [x] Android `Codex Proxy` provider can route through the local Mac proxy
- [x] `adb reverse tcp:4317 tcp:4317` is the current Codex proxy bridge path
- [ ] Codex proxy is still host-dependent and not phone-standalone
- [ ] No auth gate exists between Android and the local Codex proxy yet

## 8. Stock Proxy Current Scope

- [x] `stock_proxy/` is implemented as a separate local HTTP module
- [x] Alpha Vantage is used for global stock data
- [x] Naver public endpoints are used for domestic Korean stock data
- [x] Korean market summary is implemented
- [x] Natural-language stock query parsing is implemented
- [x] Common domestic alias mapping is implemented
- [x] Ambiguous finance-like prompts return clarification instead of hard 500
- [x] Sector summary is implemented for preset sectors
- [x] Two-stock comparison summary is implemented
- [x] Debug parse endpoint `POST /api/v1/parse-query` is implemented
- [x] Android `Stock Proxy` provider is integrated
- [x] Android `Stock Proxy` auto-falls back to OpenAI for non-stock prompts
- [ ] Sector coverage is still preset-based, not broad market-wide semantic coverage
- [ ] Comparison is currently limited to 2 stocks
- [ ] Theme / ETF / macro asset coverage is not implemented
- [ ] U.S. stock natural-language sector/compare flow is not implemented

## 9. Stock Query Examples Verified

- [x] `삼성전자 최근 흐름 요약해줘`
- [x] `삼성전자 금일 통향 정리 해줘`
- [x] `하닉 오늘 어때`
- [x] `금일 한국주식시장 정리 해줘`
- [x] `반도체주 오늘 어때`
- [x] `삼성전자랑 하닉 비교해줘`
- [x] `오늘 어때` -> does not hard-fail; handled as ambiguous / fallback path

## 10. Android Fallback Behavior

- [x] If provider is `STOCK_PROXY` and prompt is not stock-like, route to OpenAI
- [x] If provider is `STOCK_PROXY` and stock proxy request fails, fallback to OpenAI
- [x] Routing result is surfaced through finish metadata
- [x] Routing behavior is covered by unit tests
- [ ] UI does not yet show a dedicated fallback badge separate from provider badge

## 11. Codex Proxy Current Scope

- [x] `codex_proxy/` builds and runs independently
- [x] Codex OAuth token load / refresh path is implemented
- [x] `health`, `auth status`, `providers`, `chat`, `self-test` endpoints are implemented
- [x] Android `Codex Proxy` provider wiring is implemented
- [x] Local Mac-hosted proxy usage was validated
- [ ] LAN deployment flow is not fully documented
- [ ] Proxy auth gate / device-to-proxy authorization is not implemented

## 12. Build / Install / Runtime Verification

- [x] Android unit tests were executed successfully on the current machine
- [x] Android release build was executed successfully on the current machine
- [x] Release APK installation to a connected device was verified
- [x] App launch on device was verified
- [x] `adb reverse` based proxy routing was verified
- [x] `stock_proxy` live local summary requests were verified
- [x] `codex_proxy` live local self-test requests were verified
- [ ] No CI pipeline or automated release workflow is documented yet

## 13. Documentation Status

- [x] `AGENTS.md` matches current workspace rules
- [x] `STOCK_PROXY_PLAN.md` reflects natural-language, sector, and compare support
- [x] `CODEX_PROXY_PLAN.md` reflects that Android integration is already present
- [x] `stock_proxy/README.md` reflects natural-language, sector, compare, and parse endpoint behavior
- [x] `codex_proxy/README.md` reflects current Android integration and host-dependent operation
- [ ] No single end-user/operator guide exists yet for running Android + `stock_proxy` + `codex_proxy` together

## 14. Highest Remaining Gaps

- [ ] Broader sector dictionary coverage
- [ ] Compare support for more than two stocks
- [ ] Clarification loop surfaced cleanly in Android UI
- [ ] Operator guide for proxy startup / `adb reverse` recovery
- [ ] Stronger long-running reliability validation on device
- [ ] Secret rotation / incident response documentation
