import json
import sys
import io
import traceback
from typing import Dict, Any

def execute_inline_script(script_source: str, payload_json: str) -> str:
    """
    Mengeksekusi skrip Python inline dengan menangkap stdout dan mengembalikan
    kamus output yang terstruktur dalam format JSON.
    """
    input_data = json.loads(payload_json) if payload_json else {}
    stdout_capture = io.StringIO()
    old_stdout = sys.stdout

    exec_scope: Dict[str, Any] = {
        "inputs": input_data,
        "outputs": {},
        "__builtins__": __builtins__
    }

    try:
        sys.stdout = stdout_capture
        exec(script_source, exec_scope)
        sys.stdout = old_stdout

        return json.dumps({
            "is_success": True,
            "outputs": exec_scope.get("outputs", {}),
            "stdout": stdout_capture.getvalue(),
            "error_message": None
        })
    except Exception as exc:
        sys.stdout = old_stdout
        return json.dumps({
            "is_success": False,
            "outputs": {},
            "stdout": stdout_capture.getvalue(),
            "error_message": f"{str(exc)}\n{traceback.format_exc()}"
        })
