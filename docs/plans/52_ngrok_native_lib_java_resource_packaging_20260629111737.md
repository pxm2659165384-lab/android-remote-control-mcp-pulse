<!-- SACRED DOCUMENT — DO NOT MODIFY except for checkmarks ([ ] → [x]) and review findings. -->
<!-- You MUST NEVER alter, revert, or delete files outside the scope of this plan. -->
<!-- Plans in docs/plans/ are PERMANENT artifacts. There are ZERO exceptions. -->

# Plan 52 — Fix the ngrok in-process tunnel native-library load (load from jniLibs via System.loadLibrary) and enable arm64-v8a + x86_64

**Why (non-derivable context — established empirically):**
- On device the tunnel crashes with `UnsatisfiedLinkError: No implementation found for com.ngrok.NativeSession.connectNative … is the library loaded?`. Root cause: ngrok's `com.ngrok.Runtime.load()` does **not** use `System.loadLibrary`; it calls `getResourceAsStream("/libngrok_java.so")`, copies the bytes to a temp file, then `System.load()`s them (and `NativeSession`'s static block swallows any failure). The `.so` is shipped in `jniLibs`, which AGP packages under `lib/<abi>/` — reachable only via `System.loadLibrary`, which nothing calls. So `getResourceAsStream` returns `null`, the failure is swallowed, the native lib is never loaded, and `connectNative` throws.
- A Gradle experiment proved the `.so` **cannot** be packaged as a Java resource: AGP silently strips `*.so` from Java resources (only `lib/<abi>/` packaging accepts a `.so`). `getResourceAsStream("/libngrok_java.so")` can therefore never resolve inside an APK. The host JVM tests work only because `ngrok-java-native-host.jar` (testRuntimeOnly) carries the host `.so` on the raw test classpath, bypassing AGP.
- Fix: make ngrok's loader prefer `System.loadLibrary("ngrok_java")` (which loads the correct per-ABI `.so` from `jniLibs`/`lib/<abi>/`), keeping the existing resource-extraction path as a fallback so host JVM tests keep working via the host jar. Because `System.loadLibrary` + `jniLibs` is inherently multi-ABI, this also lets ngrok run on `x86_64` (emulators, redroid) — the `Makefile` already builds and ships the `x86_64-linux-android` `.so`; only the provider's ABI guard blocked it.

**Agreed scope (do NOT deviate):**
- The dual-path loader change lives as a commit in the **fork** `github.com/danielealbano/ngrok-java` (the `vendor/ngrok-java` submodule); this repo bumps the submodule pointer to it. No build-time patch file.
- ngrok supports **arm64-v8a + x86_64** (the two ABIs the `Makefile` already builds). Other ABIs keep the graceful "not supported" error.
- The `.so` **stays in `jniLibs`** for both ABIs. Therefore: **no** `Makefile` change, **no** CI workflow change, **no** `.gitignore` change, **no** alignment-check change (the `.so` never leaves `lib/`, so `make check-so-alignment` coverage is unchanged; it remains a manual/local gate as today).
- Add `native.properties` as a normal Java resource (it is not a `.so`, so AGP packages it correctly — verified) so ngrok reports `agent.version=1.1.1` on device.
- `cloudflared` and all other native libs untouched.

---

## User Story 1 — Add the dual-path loader to the ngrok-java fork and bump the submodule

**Why:** `Runtime.load()` must succeed on Android (so the subsequent native `Runtime.init()` runs) by loading the `.so` from `jniLibs`, while still loading from the classpath resource on the host JVM test path.

**Acceptance criteria:**
- [ ] In the fork, `Runtime.load()` attempts `System.loadLibrary("ngrok_java")` first and returns on success; on `UnsatisfiedLinkError` it falls back to the existing `getResourceAsStream` + `System.load()` logic unchanged.
- [ ] No other ngrok behavior changes (`init()`, `getLibname()`, resource fallback, etc. are untouched).
- [ ] The change is committed and pushed to `github.com/danielealbano/ngrok-java`; this repo's `vendor/ngrok-java` submodule points at that commit.

### Task 1.1 — Patch the loader in the fork
- [ ] **Modify (in the fork submodule)** `vendor/ngrok-java/ngrok-java-native/src/main/java/com/ngrok/Runtime.java`: at the top of `load()`, try `System.loadLibrary("ngrok_java")` and `return` on success; catch `UnsatisfiedLinkError` and fall through to the existing temp-file extraction path. Keep all existing fallback code intact.
- [ ] **Commit + push** the change on the fork; record the new commit SHA.

**DoD:**
- [ ] Fork builds; loader prefers `loadLibrary`, falls back to the resource path.

### Task 1.2 — Bump the submodule pointer in this repo
- [ ] **Modify** `vendor/ngrok-java` (submodule gitlink): check out the new fork commit so the parent repo records the bumped pointer; stage only the submodule gitlink.

**DoD:**
- [ ] `git submodule status vendor/ngrok-java` shows the new commit; the parent repo's staged change is only the gitlink bump.

### Task 1.3 — Rebuild the ngrok native artifacts from the bumped submodule
- [ ] **Run** `make compile-ngrok-native` so the patched loader is compiled into the local `ngrok-java-native-classes.jar` and `ngrok-java-native-host.jar` (the `files(...)` classpath artifacts the app build and host tests consume) and the per-ABI `.so`s are (re)produced into `jniLibs/{arm64-v8a,x86_64}/`. Without this, the stale unpatched jars remain and verification would be invalid. (The native `.so`s are unaffected by the Java-only patch; the target rebuilds them too.) On Linux, ensure `JAVA_HOME` points to JDK 17 (the target's macOS-Homebrew defaults are overridden by `JAVA_HOME`, per project setup).

**DoD:**
- [ ] The local `ngrok-java-native-classes.jar` carries the patched `Runtime.class`; the host jar (`libngrok_java.so` only) and the API jar (`ngrok-java-1.1.1.jar`) are unaffected by the Java-only patch.

---

## User Story 2 — Enable arm64-v8a + x86_64 in the provider

**Why:** With `System.loadLibrary` + `jniLibs`, the correct `.so` loads per-ABI automatically; the only blocker is the provider's arm64-only guard.

**Acceptance criteria:**
- [ ] `isSupportedAbi()` returns true when the device's `Build.SUPPORTED_ABIS` contains `arm64-v8a` or `x86_64`.
- [ ] Unsupported ABIs still produce the existing graceful error.
- [ ] The provider KDoc/comment no longer claims arm64-only.

### Task 2.1 — Relax the ABI guard (via a testable helper)
- [ ] **Modify** `app/src/main/kotlin/com/danielealbano/androidremotecontrolmcp/services/tunnel/NgrokTunnelProvider.kt`: replace the single `SUPPORTED_ABI` constant with a supported-ABI set (`arm64-v8a`, `x86_64`); have `isSupportedAbi()` read `Build.SUPPORTED_ABIS` and delegate the decision to a pure, `internal` helper that takes the device ABI list and tests membership against the set (so the matching is unit-testable without mocking the `Build` static field); update the class KDoc ("Only available on arm64-v8a devices …") to state arm64-v8a + x86_64.

**DoD:**
- [ ] Guard accepts both ABIs; error path unchanged for others; KDoc accurate.

### Task 2.2 — Update stale arm64-only ngrok claims (string resources + live docs)
- [ ] **Modify** `app/src/main/res/values/strings.xml`: change the `remote_access_ngrok_unsupported` string from `ngrok is not available on this device (requires ARM64)` to `ngrok is not available on this device (requires arm64-v8a or x86_64)` so it no longer claims ARM64-only.
- [ ] **Modify** the two live docs that explicitly assert ngrok is arm64-only — `README.md` (line ~266, "Only available on ARM64 devices.") and `docs/PROJECT.md` (line ~610, "Only available on ARM64 devices.") — to read arm64-v8a + x86_64.
- [ ] **Sweep** the rest of the live docs (e.g. `docs/ARCHITECTURE.md`, any other current ngrok/tunnel doc) for further arm64-only ngrok assertions and **modify** only those now inaccurate. (A full repo grep at planning time found only the README, PROJECT.md, and `strings.xml` claims, but the implementer MUST re-grep to catch any added since.)
- [ ] **MUST NOT** edit any file under `docs/plans/` — those are SACRED permanent artifacts; their arm64-only references record past state and are never updated. Make no other unrelated edits.

**DoD:**
- [ ] No live doc or string resource claims ngrok is arm64-only; every file under `docs/plans/` is untouched; no out-of-scope edits.

---

## User Story 3 — Add `native.properties` so ngrok reports its real agent version on device

**Why:** `NativeSession` reads `agent.version` from `getResourceAsStream("/native.properties")`; neither vendored jar ships it, so on device the version defaults. `native.properties` is not a `.so`, so AGP packages it as a Java resource correctly (established by a planning-time Gradle experiment; Task 5.1 re-asserts it mechanically against the assembled APK).

**Acceptance criteria:**
- [ ] An assembled APK exposes `/native.properties` at the Java-resources root with `agent.version=1.1.1`.
- [ ] No collision/regression on the host JVM test path.

### Task 3.1 — Create the committed resource
- [ ] **Create** `app/src/main/resources/native.properties` containing exactly `agent.version=1.1.1`. (Only `agent.version` is read at runtime by `NativeSession`; `agent.classifier` is omitted because a single resource serves both ABIs and the value is never read.)

**DoD:**
- [ ] File present and tracked; `agent.version` matches the vendored `1.1.1`.
- [ ] On the host JVM test classpath this file is the sole `/native.properties` (host jar and classes jar ship none), so it introduces no collision.

---

## User Story 4 — Unit-test the relaxed ABI matching

**Why:** The guard change (single ABI → arm64-v8a + x86_64) needs direct coverage. The existing `NgrokTunnelProviderTest` stubs `isSupportedAbi()` via `mockkObject(NgrokTunnelProvider.Companion)`, and `Build.SUPPORTED_ABIS` is a static **field** MockK cannot stub — so the real matching logic must be made testable to cover it.

**Acceptance criteria:**
- [ ] The ABI-matching logic is unit-tested for arm64-v8a, x86_64, an unsupported ABI, a realistic multi-entry list (mixed supported + unsupported), and an empty list.
- [ ] The existing `NgrokTunnelProviderTest` cases still pass unchanged in intent (they stub `isSupportedAbi()`).

### Task 4.1 — Add coverage for the matching helper
- [ ] **Modify** `app/src/test/kotlin/com/danielealbano/androidremotecontrolmcp/services/tunnel/NgrokTunnelProviderTest.kt`: add cases for the pure helper introduced in Task 2.1.

  **Setup**: call the pure helper directly with crafted ABI lists; no `Build`/`mockkStatic` needed.

  | Test | Verifies |
  |------|----------|
  | `abi match true for arm64-v8a` | Helper returns true for a list containing `arm64-v8a`. |
  | `abi match true for x86_64` | Helper returns true for a list containing `x86_64`. |
  | `abi match false for unsupported abi` | Helper returns false for a list with only an unsupported ABI (e.g. `armeabi-v7a`). |
  | `abi match true for multi-entry list` | Returns true for a realistic multi-ABI list mixing supported + unsupported (e.g. `["arm64-v8a","armeabi-v7a","armeabi"]`), matching real `Build.SUPPORTED_ABIS`. |
  | `abi match false for empty list` | Returns false for an empty ABI list. |

**DoD:**
- [ ] New cases pass; existing provider tests remain green.

---

## User Story 5 — Verify on both targets and pass all quality gates

**Why:** Prove the fix on the host test path (loader resource fallback), on the real arm64 device and an x86_64 emulator (the `loadLibrary` path for each enabled ABI), with the E2E suite as a no-regression gate.

**Acceptance criteria:**
- [ ] Host x86_64: lint clean; full JVM unit + integration suite passes (incl. the host ngrok integration test, which exercises the loader's resource fallback); `./gradlew build` clean.
- [ ] E2E suite (`make test-e2e`) passes — regression gate confirming the debug APK assembles and device flows work with the changes (it does not itself exercise ngrok).
- [ ] arm64 device AND x86_64 emulator: the ngrok tunnel loads its native library (no `UnsatisfiedLinkError`).

### Task 5.1 — Host x86_64 quality gates
- [ ] **Run** `make lint` → `/tmp/p52-lint.log`.
- [ ] **Run** the full JVM suite (sourcing `.env` so the ngrok integration test runs) → `/tmp/p52-test.log`.
- [ ] **Run** `./gradlew build` → `/tmp/p52-build.log`.
- [ ] **Inspect** the assembled debug APK (`unzip -l`) and assert it contains `lib/arm64-v8a/libngrok_java.so`, `lib/x86_64/libngrok_java.so`, and `/native.properties` at the resource root — and that no `libngrok_java.so` is present as a Java resource. → `/tmp/p52-apk-contents.log`.
- [ ] **Run** `make check-so-alignment` to confirm both ngrok `.so`s (and `cloudflared`) are 16KB-aligned in the APK → `/tmp/p52-so-align.log`.

**DoD:**
- [ ] Lint clean; all JVM tests pass; build clean (no warnings).
- [ ] APK contains both ABIs' `libngrok_java.so` under `lib/` and `/native.properties` at the resource root; alignment check passes.

### Task 5.2 — E2E regression gate
- [ ] **Run** `make test-e2e` → `/tmp/p52-e2e.log`.

**DoD:**
- [ ] E2E suite passes (no regression from the submodule bump / provider / resource changes).

### Task 5.3 — arm64 device manual verification
- [ ] **Manual Test (arm64 device):** `make install` to the connected arm64 device, start the MCP server with a real ngrok authtoken configured, and confirm via `adb logcat` that the tunnel reaches Connected with a tunnel URL and no `UnsatisfiedLinkError`.

**DoD:**
- [ ] Tunnel connects on the device; logcat shows no native-load error.

### Task 5.4 — x86_64 emulator manual verification
- [ ] **Manual Test (x86_64 emulator):** start an x86_64 emulator (`make setup-emulator` / `make start-emulator`), `make install`, start the MCP server with a real ngrok authtoken configured, and confirm via `adb logcat` that the ngrok native library loads (tunnel reaches Connected, or at minimum produces an ngrok auth/connection error — **not** `UnsatisfiedLinkError`). This verifies the newly enabled x86_64 ABI end-to-end.

**DoD:**
- [ ] On x86_64, ngrok loads its native library (no `UnsatisfiedLinkError`).

---

## User Story 6 — Ground-up double-check of the entire implementation

**Why:** Final end-to-end audit that every change is consistent, complete, and matches this plan and the agreed scope.

### Task 6.1 — Re-verify every change from the ground up
- [ ] **Re-read** every modified/created file: the fork's `Runtime.java` (dual-path `load()`), the rebuilt ngrok jars reflecting the patch, the `vendor/ngrok-java` submodule pointer, `NgrokTunnelProvider.kt` (guard + helper + KDoc), `app/src/main/resources/native.properties`, the updated provider unit test, `app/src/main/res/values/strings.xml`, `README.md`, `docs/PROJECT.md`, and any other doc edits — confirm each matches its task and that no `docs/plans/` file was touched.
- [ ] **Confirm** no out-of-scope changes were made: `Makefile`, `.github/workflows/*`, `.gitignore`, the alignment check, `jniLibs` layout, and `cloudflared` are all untouched.
- [ ] **Confirm** the `.so` is still packaged under `lib/arm64-v8a/` and `lib/x86_64/` in an assembled APK (both ABIs present), and `/native.properties` is a Java resource at the root.
- [ ] **Confirm** the loader fallback is intact (host JVM tests still load via the host jar) and `Runtime.init()` runs on device after `loadLibrary` succeeds.
- [ ] **Re-run** lint, the full JVM suite, `./gradlew build`, and E2E from clean to confirm reproducibility → `/tmp/p52-final-*.log`.
- [ ] **Spawn** the `code-reviewer` subagent in plan-compliance mode over the full implementation; fix every finding and re-run until clean.

**DoD:**
- [ ] All re-verifications pass; code-reviewer reports zero findings; the implementation matches this plan with zero deviations.
