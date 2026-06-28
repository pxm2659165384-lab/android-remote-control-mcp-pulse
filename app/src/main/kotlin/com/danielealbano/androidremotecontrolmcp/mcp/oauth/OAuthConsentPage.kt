package com.danielealbano.androidremotecontrolmcp.mcp.oauth

/**
 * Self-contained consent page served at `/authorize`. It displays the client name and the 2-digit match
 * code and polls `/authorize/status?id=<approvalId>` every 2s. The ONLY approval gate is the explicit
 * on-device action in the app — this page has no approve/submit control; the match code is a UX
 * confirmation, not a secret. No external resources are referenced.
 */
fun consentPageHtml(
    approvalId: String,
    matchCode: String,
    clientName: String,
): String {
    val safeName = escapeHtml(clientName)
    val safeId = escapeHtml(approvalId)
    val safeCode = escapeHtml(matchCode)
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Approve connection</title>
        <style>
        body { font-family: sans-serif; max-width: 28rem; margin: 3rem auto; padding: 0 1rem; color: #1b1b1f; }
        .code { font-size: 3rem; font-weight: 700; letter-spacing: 0.3rem; text-align: center; margin: 1.5rem 0; }
        .status { margin-top: 1.5rem; text-align: center; color: #5b5b66; }
        .error { color: #b3261e; }
        </style>
        </head>
        <body>
        <h1>Approve <strong>$safeName</strong> to connect</h1>
        <p>Open the Android Remote Control app and approve the request showing this code:</p>
        <div class="code">$safeCode</div>
        <div class="status" id="status">Waiting for approval on your device…</div>
        <script>
        (function () {
          var id = "$safeId";
          var statusEl = document.getElementById("status");
          function poll() {
            fetch("/authorize/status?id=" + encodeURIComponent(id))
              .then(function (r) { return r.json(); })
              .then(function (data) {
                if (data.state === "approved" && data.redirect) {
                  window.location = data.redirect;
                } else if (data.state === "denied") {
                  statusEl.className = "status error";
                  statusEl.textContent = "Request denied on the device.";
                } else if (data.state === "expired") {
                  statusEl.className = "status error";
                  statusEl.textContent = "Request expired. Start the connection again.";
                } else {
                  setTimeout(poll, 2000);
                }
              })
              .catch(function () { setTimeout(poll, 2000); });
          }
          poll();
        })();
        </script>
        </body>
        </html>
        """.trimIndent()
}

private fun escapeHtml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
