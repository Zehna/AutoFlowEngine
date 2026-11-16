import importlib
import inspect
import io
import json
import os
import sys
import traceback
from typing import Dict, Any


def _wrap_result(is_success: bool, outputs: Any, stdout_text: str, error_message: Any = None) -> str:
    if outputs is None:
        normalized = {}
    elif isinstance(outputs, dict):
        normalized = outputs
    else:
        normalized = {"value": outputs}

    return json.dumps({
        "is_success": is_success,
        "outputs": normalized,
        "stdout": stdout_text,
        "error_message": error_message
    }, default=str)


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
        return _wrap_result(True, exec_scope.get("outputs", {}), stdout_capture.getvalue())
    except Exception as exc:
        sys.stdout = old_stdout
        return _wrap_result(False, {}, stdout_capture.getvalue(), f"{str(exc)}\n{traceback.format_exc()}")


def call_module_function(
    modules_dir: str,
    module_name: str,
    function_name: str,
    payload_json: str,
    context_json: str = "{}"
) -> str:
    """
    Mengimpor modul .py dari direktori filesDir/python_modules dan memanggil fungsi target.
    """
    input_data = json.loads(payload_json) if payload_json else {}
    context_data = json.loads(context_json) if context_json else {}
    stdout_capture = io.StringIO()
    old_stdout = sys.stdout

    clean_mod = module_name[:-3] if module_name.endswith(".py") else module_name
    clean_func = function_name.strip() if function_name and function_name.strip() else "run"

    try:
        if modules_dir and os.path.isdir(modules_dir) and modules_dir not in sys.path:
            sys.path.insert(0, modules_dir)

        sys.stdout = stdout_capture
        mod = importlib.import_module(clean_mod)
        mod = importlib.reload(mod)

        fn = getattr(mod, clean_func)
        sig = inspect.signature(fn)
        if len(sig.parameters) >= 2:
            res = fn(input_data, context_data)
        elif len(sig.parameters) == 1:
            res = fn(input_data)
        else:
            res = fn()

        sys.stdout = old_stdout
        return _wrap_result(True, res, stdout_capture.getvalue())
    except Exception as exc:
        sys.stdout = old_stdout
        return _wrap_result(False, {}, stdout_capture.getvalue(), f"{str(exc)}\n{traceback.format_exc()}")
