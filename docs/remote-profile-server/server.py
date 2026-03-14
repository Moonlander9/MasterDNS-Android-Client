#!/usr/bin/env python3

# Import BaseHTTPRequestHandler so we can implement a tiny HTTP server.
from http.server import BaseHTTPRequestHandler

# Import HTTPServer to actually listen on an IP and port.
from http.server import HTTPServer

# Import Path so file handling is simple and safe.
from pathlib import Path

# Import hmac for comparing and generating request signatures.
import hmac

# Import hashlib because the Android client uses HMAC-SHA256.
import hashlib

# Import time so we can reject old or replayed requests.
import time

# Import url parsing helpers to safely read the requested profile name.
from urllib.parse import urlparse
from urllib.parse import unquote


# Set the shared secret used by both Android and the server.
# This must match the value built into the Android app.
SECRET = b"masterdns-profile-key"

# Set the directory where profile TOML files live on the VPS.
# Example: ./configs/jack.toml will be served for GET /jack
CONFIG_DIR = Path("./configs")

# Keep nonces in memory so the same signed request cannot be replayed immediately.
USED_NONCES = {}

# Reject requests older than this many seconds.
MAX_AGE_SECONDS = 30

# Bind the service to all network interfaces on port 8080.
HOST = "0.0.0.0"
PORT = 8080


# Build the exact signature format expected by the Android client.
def sign(method: str, path: str, timestamp: str, nonce: str) -> str:
    # Create the payload in the same format used in the app:
    # METHOD|/path|timestamp|nonce
    payload = f"{method}|{path}|{timestamp}|{nonce}".encode("utf-8")

    # Return a lowercase hex HMAC-SHA256 signature.
    return hmac.new(SECRET, payload, hashlib.sha256).hexdigest()


# Remove expired nonces from memory so the dictionary does not grow forever.
def cleanup_nonces(now: int) -> None:
    # Walk over a snapshot of the current nonce map.
    for nonce, created_at in list(USED_NONCES.items()):
        # Delete any nonce older than the accepted request window.
        if now - created_at > MAX_AGE_SECONDS:
            del USED_NONCES[nonce]


# Implement the request handler for our tiny HTTP server.
class Handler(BaseHTTPRequestHandler):
    # Silence the default noisy access log unless you want it.
    def log_message(self, format: str, *args) -> None:
        # Print one short access line to stdout for service logs.
        print("%s - - [%s] %s" % (self.client_address[0], self.log_date_time_string(), format % args))

    # Handle GET requests like /jack
    def do_GET(self) -> None:
        # Parse the URL so we can safely extract the path.
        parsed = urlparse(self.path)

        # Keep the raw path exactly as requested because the signature uses it.
        request_path = parsed.path

        # Convert /jack into the profile name jack.
        profile_name = unquote(request_path.lstrip("/")).strip()

        # Read auth headers sent by the Android client.
        timestamp = self.headers.get("X-Timestamp", "")
        nonce = self.headers.get("X-Nonce", "")
        signature = self.headers.get("X-Signature", "")

        # Reject obviously malformed requests.
        if not profile_name or not timestamp or not nonce or not signature:
            self.send_response(403)
            self.end_headers()
            return

        # Read current time once so all checks use the same reference.
        now = int(time.time())

        # Parse the timestamp header.
        try:
            timestamp_int = int(timestamp)
        except ValueError:
            self.send_response(403)
            self.end_headers()
            return

        # Reject stale requests or requests too far in the future.
        if abs(now - timestamp_int) > MAX_AGE_SECONDS:
            self.send_response(403)
            self.end_headers()
            return

        # Clean old nonces before checking reuse.
        cleanup_nonces(now)

        # Reject immediate replay of the same signed request.
        if nonce in USED_NONCES:
            self.send_response(403)
            self.end_headers()
            return

        # Recompute the signature the same way the client did.
        expected_signature = sign("GET", request_path, timestamp, nonce)

        # Use compare_digest to avoid unsafe string comparison.
        if not hmac.compare_digest(expected_signature, signature):
            self.send_response(403)
            self.end_headers()
            return

        # Mark this nonce as used after signature verification succeeds.
        USED_NONCES[nonce] = now

        # Map /jack to ./configs/jack.toml
        file_path = CONFIG_DIR / f"{profile_name}.toml"

        # Return 404 if there is no config for that profile name.
        if not file_path.is_file():
            self.send_response(404)
            self.end_headers()
            return

        # Read the TOML file bytes.
        data = file_path.read_bytes()

        # Return the TOML file to the Android app.
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


# Start the server if this file is executed directly.
if __name__ == "__main__":
    # Ensure the config directory exists before the service starts.
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)

    # Show where the server is listening.
    print(f"Starting remote profile server on {HOST}:{PORT}")

    # Show where profile files should be stored.
    print(f"Profile directory: {CONFIG_DIR.resolve()}")

    # Start serving forever until the process is stopped.
    HTTPServer((HOST, PORT), Handler).serve_forever()
