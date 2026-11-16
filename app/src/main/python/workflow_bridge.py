import json
import sys
import io
import traceback
from typing import Dict, Any


def execute_inline_script(script_source: str, payload_json: str, context_json: str = "{}") -> str:
    """
    Mengeksekusi skrip Python inline dengan akses ke:
    - inputs: parameter JSON khusus node ini
    - context: seluruh output node sebelumnya dan variabel workflow
    - outputs: kamus hasil yang akan dikembalikan ke orkestrator
    """
    input_data = json.loads(payload_json) if payload_json else {}
    context_data = json.loads(context_json) if context_json else {}
    stdout_capture = io.StringIO()
    old_stdout = sys.stdout

    exec_scope: Dict[str, Any] = {
        "inputs": input_data,
        "context": context_data,
        "outputs": {},
        "__builtins__": __builtins__
    }

    try:
        sys.stdout = stdout_capture
        exec(script_source, exec_scope)
        sys.stdout = old_stdout

        raw_outputs = exec_scope.get("outputs", {})
        if not isinstance(raw_outputs, dict):
            raw_outputs = {"value": raw_outputs}

        return json.dumps({
            "is_success": True,
            "outputs": raw_outputs,
            "stdout": stdout_capture.getvalue(),
            "error_message": None
        }, default=str)
    except Exception as exc:
        sys.stdout = old_stdout
        return json.dumps({
            "is_success": False,
            "outputs": {},
            "stdout": stdout_capture.getvalue(),
            "error_message": f"{str(exc)}\n{traceback.format_exc()}"
        })
