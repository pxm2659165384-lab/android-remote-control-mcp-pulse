package com.danielealbano.androidremotecontrolmcp.mcp.oauth

/**
 * Self-contained consent page served at `/authorize`. It displays the client name/host and the 2-digit
 * match code, counts down the remaining approval window, and polls `/authorize/status?id=<approvalId>`
 * every 2s. The ONLY approval gate is the explicit on-device action in the app — this page has no
 * approve/submit control; the match code is a UX confirmation, not a secret. No external resources are
 * referenced (the monogram is rendered in CSS), so nothing about the request leaks to a third party.
 */
fun consentPageHtml(
    approvalId: String,
    matchCode: String,
    clientName: String,
    host: String,
    expiresInSeconds: Long,
): String {
    val safeName = escapeHtml(clientName)
    val safeHost = escapeHtml(host)
    val safeId = escapeHtml(approvalId)
    val safeCode = escapeHtml(matchCode)
    val initials =
        escapeHtml(
            clientName
                .trim()
                .take(2)
                .uppercase()
                .ifEmpty { "?" },
        )
    val seconds = expiresInSeconds.coerceAtLeast(0)
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Approve connection</title>
        <style>$CONSENT_PAGE_STYLES</style>
        </head>
        <body>
        <div class="card">
          <div class="avatar">$initials</div>
          <h1>Approve <strong>$safeName</strong></h1>
          <div class="host">&#127760; $safeHost</div>
          <div class="prompt">Open Android Remote Control and approve the request showing this code:</div>
          <div class="code">$safeCode</div>
          <div class="timer" id="timer"></div>
          <div class="status" id="status">Waiting for approval on your device&#8230;</div>
        </div>
        <script>${consentPageScript(safeId, seconds)}</script>
        </body>
        </html>
        """.trimIndent()
}

private val CONSENT_PAGE_STYLES =
    """
    :root {
      --bg: #f6f5fb; --card: #ffffff; --fg: #1b1b1f; --muted: #5b5b66;
      --line: #e4e2ec; --accent: #6750a4; --accent-bg: #eaddff; --accent-fg: #21005d; --error: #b3261e;
    }
    @media (prefers-color-scheme: dark) {
      :root {
        --bg: #131316; --card: #1e1e22; --fg: #e6e1e9; --muted: #cac4d0;
        --line: #2e2e33; --accent: #d0bcff; --accent-bg: #4f378b; --accent-fg: #eaddff; --error: #f2b8b5;
      }
    }
    * { box-sizing: border-box; }
    body {
      font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
      background: var(--bg); color: var(--fg); margin: 0; min-height: 100vh;
      display: flex; align-items: center; justify-content: center; padding: 1.5rem;
    }
    .card {
      background: var(--card); border: 1px solid var(--line); border-radius: 1.75rem;
      max-width: 24rem; width: 100%; padding: 2rem 1.75rem; text-align: center;
      box-shadow: 0 12px 32px rgba(0,0,0,0.12);
    }
    .avatar {
      width: 72px; height: 72px; border-radius: 50%; margin: 0 auto 1rem;
      background: var(--accent); color: #fff; font-size: 1.75rem; font-weight: 600;
      display: flex; align-items: center; justify-content: center;
    }
    h1 { font-size: 1.3rem; font-weight: 600; margin: 0 0 0.5rem; }
    h1 strong { font-weight: 700; }
    .host {
      display: inline-flex; align-items: center; gap: 0.4rem; font-size: 0.85rem; color: var(--muted);
      background: var(--bg); border: 1px solid var(--line); border-radius: 999px; padding: 0.25rem 0.75rem;
    }
    .prompt { color: var(--muted); font-size: 0.95rem; margin: 1.5rem 0 0.75rem; }
    .code {
      display: inline-block; font-size: 2.75rem; font-weight: 700; letter-spacing: 0.4rem;
      font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
      background: var(--accent-bg); color: var(--accent-fg);
      border-radius: 1.25rem; padding: 0.6rem 1.75rem; margin-left: 0.4rem;
    }
    .timer { margin-top: 1.25rem; font-size: 0.9rem; color: var(--muted); }
    .status { margin-top: 0.5rem; font-size: 0.9rem; color: var(--muted); }
    .error { color: var(--error); }
    """.trimIndent()

private fun consentPageScript(
    safeId: String,
    seconds: Long,
): String =
    """
    (function () {
      var id = "$safeId";
      var statusEl = document.getElementById("status");
      var timerEl = document.getElementById("timer");
      var remaining = $seconds;
      var done = false;
      function tick() {
        if (done) { return; }
        if (remaining <= 0) {
          timerEl.className = "timer error";
          timerEl.textContent = "Request expired";
          return;
        }
        var m = Math.floor(remaining / 60);
        var s = remaining % 60;
        timerEl.textContent = "Expires in " + m + ":" + (s < 10 ? "0" : "") + s;
        remaining -= 1;
        setTimeout(tick, 1000);
      }
      function poll() {
        fetch("/authorize/status?id=" + encodeURIComponent(id))
          .then(function (r) { return r.json(); })
          .then(function (data) {
            if (data.state === "approved" && data.redirect) {
              done = true; timerEl.textContent = ""; window.location = data.redirect;
            } else if (data.state === "denied") {
              done = true; timerEl.textContent = "";
              statusEl.className = "status error";
              statusEl.textContent = "Request denied on the device.";
            } else if (data.state === "expired") {
              done = true; timerEl.className = "timer error"; timerEl.textContent = "Request expired";
              statusEl.className = "status error";
              statusEl.textContent = "Request expired. Start the connection again.";
            } else {
              setTimeout(poll, 2000);
            }
          })
          .catch(function () { setTimeout(poll, 2000); });
      }
      tick();
      poll();
    })();
    """.trimIndent()

private fun escapeHtml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
