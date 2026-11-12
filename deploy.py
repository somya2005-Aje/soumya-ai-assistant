#!/usr/bin/env python3
"""Deploy SOUMYA AI Assistant landing site to Cloudflare Pages with Wrangler,
using the stored token. Same pattern as voiceworld-ai deploy.py.
Token is never read or printed."""
import os
import subprocess
import sys

sys.path.insert(0, "/opt/muse/skills/custom-connector-setup/bin")
from dynamic_credentials import dynamic_credential_entry

PROJECT_DIR = os.path.expanduser("~/workspace/soumya-ai-assistant")
ACCOUNT_ID = "617e5ccdaae4cb0e07706e4e3a012801"


def main() -> int:
    entry = dynamic_credential_entry("custom.cloudflare")
    surrogate = str(entry["surrogate"]).strip()
    env = dict(os.environ)
    env["CLOUDFLARE_API_TOKEN"] = surrogate
    env["CLOUDFLARE_ACCOUNT_ID"] = ACCOUNT_ID
    cmd = [
        "npx", "--yes", "wrangler@4.160.0", "pages", "deploy",
        "final-deploy", "--project-name", "soumya-ai", "--commit-dirty=true",
    ]
    proc = subprocess.run(cmd, cwd=PROJECT_DIR, env=env)
    return proc.returncode


if __name__ == "__main__":
    raise SystemExit(main())
