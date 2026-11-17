#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Installing AutoFlow <-> n8n Bridge to /usr/local/bin/autoflow-n8n-bridge..."
install -m 0755 "$SCRIPT_DIR/autoflow_n8n_bridge.py" /usr/local/bin/autoflow-n8n-bridge

if [ -d "$HOME/.config/omalauncher/widgets" ]; then
    echo "Installing omalauncher widget n8n-bridge.owl..."
    cp "$SCRIPT_DIR/n8n-bridge.owl" "$HOME/.config/omalauncher/widgets/n8n-bridge.owl"
    if command -v owl-check >/dev/null 2>&1; then
        owl-check n8n-bridge.owl || true
    fi
fi

echo "Ensuring bridge daemon is running on http://127.0.0.1:15678..."
/usr/local/bin/autoflow-n8n-bridge ensure-json
echo "Done! Bridge ready at http://127.0.0.1:15678"
