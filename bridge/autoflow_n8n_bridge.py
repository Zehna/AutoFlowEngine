#!/usr/bin/env python3
"""
AutoFlow <-> n8n Localhost Control & Inspection Bridge.
Runs inside Linux/proot (e.g. omalauncher or Termux) bound strictly to 127.0.0.1:15678
so AutoFlow Engine Android APK can auto-start n8n, inspect local workflows & executions,
and trigger workflow runs without root or shell permissions.
"""

import json
import os
import re
import signal
import sqlite3
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

BRIDGE_HOST = "127.0.0.1"
BRIDGE_PORT = int(os.environ.get("AUTOFLOW_BRIDGE_PORT", "15678"))
N8N_PORT = int(os.environ.get("N8N_PORT", "5678"))
N8N_BIN = os.environ.get("N8N_BIN", "/usr/bin/n8n")
N8N_HOME = Path(os.environ.get("N8N_USER_FOLDER", str(Path.home()))) / ".n8n"
N8N_DB_PATH = Path(os.environ.get("N8N_DB_PATH", str(N8N_HOME / "database.sqlite")))
N8N_LOG_PATH = Path(os.environ.get("AUTOFLOW_N8N_LOG", "/root/n8n.log"))
BRIDGE_LOG_PATH = Path(os.environ.get("AUTOFLOW_BRIDGE_LOG", "/tmp/autoflow-bridge.log"))
BRIDGE_PID_PATH = Path("/tmp/autoflow-bridge.pid")
N8N_BOOT_TS_PATH = Path("/tmp/autoflow-n8n-boot.ts")
TOKEN_FILE_PATH = Path.home() / ".config" / "autoflow-bridge" / "token"

SAFE_ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")


def get_expected_token() -> str:
    env_tok = os.environ.get("AUTOFLOW_BRIDGE_TOKEN", "").strip()
    if env_tok:
        return env_tok
    if TOKEN_FILE_PATH.is_file():
        try:
            return TOKEN_FILE_PATH.read_text(encoding="utf-8").strip()
        except OSError:
            return ""
    return ""


def probe_n8n_healthz(timeout: float = 1.2) -> Tuple[bool, int]:
    url = f"http://127.0.0.1:{N8N_PORT}/healthz"
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status == 200, resp.status
    except urllib.error.HTTPError as err:
        return False, err.code
    except Exception:
        return False, 0


def find_n8n_pids() -> List[int]:
    pids: List[int] = []
    proc_dir = Path("/proc")
    if not proc_dir.is_dir():
        return pids
    my_pid = os.getpid()
    for entry in proc_dir.iterdir():
        if not entry.name.isdigit():
            continue
        pid = int(entry.name)
        if pid == my_pid:
            continue
        try:
            raw = (entry / "cmdline").read_bytes()
            cmd = raw.replace(b"\x00", b" ").decode("utf-8", errors="ignore").strip()
            if not cmd:
                continue
            if (
                "n8n start" in cmd
                or "/usr/bin/n8n start" in cmd
                or "/usr/lib/node_modules/n8n/bin/n8n start" in cmd
            ):
                pids.append(pid)
        except OSError:
            continue
    return pids


def get_boot_elapsed_seconds() -> int:
    if not N8N_BOOT_TS_PATH.is_file():
        return 0
    try:
        started = float(N8N_BOOT_TS_PATH.read_text(encoding="utf-8").strip())
        return max(0, int(time.time() - started))
    except Exception:
        return 0


def build_n8n_env() -> Dict[str, str]:
    env = os.environ.copy()
    env.setdefault("N8N_PORT", str(N8N_PORT))
    env.setdefault("N8N_HOST", "0.0.0.0")
    env.setdefault("N8N_DIAGNOSTICS_ENABLED", "false")
    env.setdefault("N8N_VERSION_NOTIFICATIONS_ENABLED", "false")
    env.setdefault("N8N_TEMPLATES_ENABLED", "false")
    env.setdefault("N8N_HIRING_BANNER_ENABLED", "false")
    env.setdefault("N8N_PERSONALIZATION_ENABLED", "false")
    env.setdefault("N8N_ENFORCE_SETTINGS_FILE_PERMISSIONS", "false")
    return env


def start_n8n_background() -> Dict[str, Any]:
    up, _ = probe_n8n_healthz()
    pids = find_n8n_pids()
    if up:
        return {
            "status": "running",
            "alreadyRunning": True,
            "pid": pids[0] if pids else None,
            "n8nUrl": f"http://127.0.0.1:{N8N_PORT}",
        }
    if pids:
        return {
            "status": "starting",
            "alreadyStarting": True,
            "pid": pids[0],
            "bootElapsedSeconds": get_boot_elapsed_seconds(),
            "n8nUrl": f"http://127.0.0.1:{N8N_PORT}",
        }

    N8N_LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    N8N_BOOT_TS_PATH.write_text(str(time.time()), encoding="utf-8")
    log_f = open(N8N_LOG_PATH, "w", encoding="utf-8")
    proc = subprocess.Popen(
        [N8N_BIN, "start"],
        stdin=subprocess.DEVNULL,
        stdout=log_f,
        stderr=subprocess.STDOUT,
        env=build_n8n_env(),
        start_new_session=True,
        close_fds=True,
    )
    log_f.close()
    return {
        "status": "starting",
        "alreadyRunning": False,
        "pid": proc.pid,
        "bootElapsedSeconds": 0,
        "n8nUrl": f"http://127.0.0.1:{N8N_PORT}",
    }


def stop_n8n_processes() -> Dict[str, Any]:
    pids = find_n8n_pids()
    for pid in pids:
        try:
            os.kill(pid, signal.SIGTERM)
        except OSError:
            pass
    deadline = time.time() + 4.0
    while time.time() < deadline and find_n8n_pids():
        time.sleep(0.25)
    remaining = find_n8n_pids()
    for pid in remaining:
        try:
            os.kill(pid, signal.SIGKILL)
        except OSError:
            pass
    try:
        N8N_BOOT_TS_PATH.unlink(missing_ok=True)
    except OSError:
        pass
    return {"status": "stopped", "stoppedPids": pids}


def open_readonly_db() -> Optional[sqlite3.Connection]:
    if not N8N_DB_PATH.is_file():
        return None
    uri = f"file:{N8N_DB_PATH}?mode=ro"
    conn = sqlite3.connect(uri, uri=True, timeout=3.0)
    conn.row_factory = sqlite3.Row
    return conn


def safe_json_loads(raw: Optional[str], fallback: Any) -> Any:
    if not raw:
        return fallback
    try:
        return json.loads(raw)
    except Exception:
        return fallback


def flatted_parse_acyclic(raw: str) -> Any:
    """
    Decodes n8n's `flatted` JSON format into standard acyclic JSON-serializable structures.
    """
    arr = json.loads(raw)
    if not isinstance(arr, list) or not arr:
        return arr

    resolved_cache: Dict[int, Any] = {}
    visiting: set = set()

    def resolve_val(val: Any) -> Any:
        if isinstance(val, str) and val.isdigit():
            idx = int(val)
            if 0 <= idx < len(arr):
                return resolve_idx(idx)
        elif isinstance(val, list):
            return [resolve_val(x) for x in val]
        elif isinstance(val, dict):
            return {k: resolve_val(v) for k, v in val.items()}
        return val

    def resolve_idx(idx: int) -> Any:
        if idx in resolved_cache:
            return resolved_cache[idx]
        if idx in visiting:
            return "<circular-ref>"
        item = arr[idx]
        if isinstance(item, (str, int, float, bool)) or item is None:
            resolved_cache[idx] = item
            return item
        visiting.add(idx)
        if isinstance(item, list):
            res = [resolve_val(v) for v in item]
        elif isinstance(item, dict):
            res = {k: resolve_val(v) for k, v in item.items()}
        else:
            res = item
        visiting.remove(idx)
        resolved_cache[idx] = res
        return res

    return resolve_idx(0)


def extract_edges(connections: Dict[str, Any]) -> List[Dict[str, Any]]:
    edges: List[Dict[str, Any]] = []
    if not isinstance(connections, dict):
        return edges
    for src_node, outputs_by_type in connections.items():
        if not isinstance(outputs_by_type, dict):
            continue
        for conn_type, branches in outputs_by_type.items():
            if not isinstance(branches, list):
                continue
            for branch_idx, targets in enumerate(branches):
                if not isinstance(targets, list):
                    continue
                for target in targets:
                    if isinstance(target, dict) and target.get("node"):
                        edges.append({
                            "from": src_node,
                            "to": target.get("node"),
                            "type": conn_type,
                            "branch": branch_idx,
                            "targetIndex": target.get("index", 0),
                        })
    return edges


def is_trigger_node(node_type: str) -> bool:
    low = (node_type or "").lower()
    return "trigger" in low or low.endswith(".webhook") or "webhook" in low


def inspect_workflows_summary() -> List[Dict[str, Any]]:
    conn = open_readonly_db()
    if conn is None:
        return []
    try:
        tag_map: Dict[str, List[str]] = {}
        try:
            for tr in conn.execute(
                "SELECT wt.workflowId, t.name FROM workflows_tags wt "
                "JOIN tag_entity t ON wt.tagId = t.id"
            ):
                tag_map.setdefault(tr["workflowId"], []).append(tr["name"])
        except sqlite3.Error:
            pass

        rows = conn.execute(
            "SELECT id, name, active, nodes, connections, description, updatedAt, createdAt, isArchived, triggerCount "
            "FROM workflow_entity WHERE COALESCE(isArchived, 0) = 0 ORDER BY updatedAt DESC"
        ).fetchall()

        results: List[Dict[str, Any]] = []
        for row in rows:
            nodes = safe_json_loads(row["nodes"], [])
            connections = safe_json_loads(row["connections"], {})
            non_note_nodes = [
                n for n in nodes
                if isinstance(n, dict) and "stickynote" not in str(n.get("type", "")).lower()
            ]
            triggers = [
                {
                    "name": n.get("name", ""),
                    "type": n.get("type", ""),
                    "webhookPath": (n.get("parameters") or {}).get("path", ""),
                }
                for n in non_note_nodes
                if is_trigger_node(str(n.get("type", "")))
            ]
            node_types = [str(n.get("type", "")) for n in non_note_nodes]
            edges = extract_edges(connections)
            pipeline_names = [str(n.get("name", "")) for n in non_note_nodes[:8]]
            results.append({
                "id": row["id"],
                "name": row["name"],
                "description": row["description"] or "",
                "active": bool(row["active"]),
                "isArchived": bool(row["isArchived"]) if row["isArchived"] is not None else False,
                "nodeCount": len(non_note_nodes),
                "totalCanvasNodes": len(nodes),
                "edgeCount": len(edges),
                "nodeTypes": node_types,
                "triggers": triggers,
                "pipelinePreview": " -> ".join(pipeline_names),
                "tags": tag_map.get(row["id"], []),
                "triggerCount": row["triggerCount"] or 0,
                "updatedAt": str(row["updatedAt"] or ""),
                "createdAt": str(row["createdAt"] or ""),
                "source": "bridge_sqlite",
            })
        return results
    finally:
        conn.close()


def inspect_workflow_detail(workflow_id: str) -> Optional[Dict[str, Any]]:
    if not SAFE_ID_RE.match(workflow_id):
        return None
    conn = open_readonly_db()
    if conn is None:
        return None
    try:
        row = conn.execute(
            "SELECT id, name, active, nodes, connections, settings, description, updatedAt, createdAt, isArchived, versionId "
            "FROM workflow_entity WHERE id = ? LIMIT 1",
            (workflow_id,),
        ).fetchone()
        if row is None:
            return None

        raw_nodes = safe_json_loads(row["nodes"], [])
        connections = safe_json_loads(row["connections"], {})
        settings = safe_json_loads(row["settings"], {})

        sanitized_nodes = []
        sticky_notes = []
        triggers = []
        for n in raw_nodes:
            if not isinstance(n, dict):
                continue
            ntype = str(n.get("type", ""))
            params = n.get("parameters") or {}
            if "stickynote" in ntype.lower():
                sticky_notes.append({
                    "name": n.get("name", "Note"),
                    "content": str(params.get("content", "")),
                })
                continue
            cred_keys = list((n.get("credentials") or {}).keys())
            node_info = {
                "id": str(n.get("id", "")),
                "name": str(n.get("name", "")),
                "type": ntype,
                "typeVersion": n.get("typeVersion", 1),
                "disabled": bool(n.get("disabled", False)),
                "parameters": params,
                "credentialTypes": cred_keys,
                "notes": str(n.get("notes", "")),
                "isTrigger": is_trigger_node(ntype),
            }
            sanitized_nodes.append(node_info)
            if node_info["isTrigger"]:
                triggers.append({
                    "name": node_info["name"],
                    "type": ntype,
                    "webhookPath": str(params.get("path", "")),
                })

        edges = extract_edges(connections)
        return {
            "id": row["id"],
            "name": row["name"],
            "description": row["description"] or "",
            "active": bool(row["active"]),
            "versionId": str(row["versionId"] or ""),
            "updatedAt": str(row["updatedAt"] or ""),
            "createdAt": str(row["createdAt"] or ""),
            "nodes": sanitized_nodes,
            "edges": edges,
            "connections": connections,
            "triggers": triggers,
            "stickyNotes": sticky_notes,
            "settings": settings,
            "source": "bridge_sqlite",
        }
    finally:
        conn.close()


def summarize_execution_blob(raw_data: Optional[str]) -> Dict[str, Any]:
    if not raw_data:
        return {"lastNodeExecuted": None, "errorMessage": None, "nodes": []}
    try:
        parsed = flatted_parse_acyclic(raw_data)
    except Exception as exc:
        return {"lastNodeExecuted": None, "errorMessage": f"Parse error: {exc}", "nodes": []}

    if not isinstance(parsed, dict):
        return {"lastNodeExecuted": None, "errorMessage": None, "nodes": []}

    result_data = parsed.get("resultData") or {}
    if not isinstance(result_data, dict):
        result_data = {}
    err_obj = result_data.get("error")
    err_msg = None
    if isinstance(err_obj, dict):
        err_msg = err_obj.get("message") or err_obj.get("description") or json.dumps(err_obj)
    elif err_obj:
        err_msg = str(err_obj)

    run_data = result_data.get("runData") or {}
    node_runs = []
    if isinstance(run_data, dict):
        for node_name, runs in run_data.items():
            if not isinstance(runs, list) or not runs:
                continue
            last_run = runs[-1] if isinstance(runs[-1], dict) else {}
            node_err = last_run.get("error")
            node_err_msg = None
            if isinstance(node_err, dict):
                node_err_msg = node_err.get("message") or json.dumps(node_err)
            elif node_err:
                node_err_msg = str(node_err)
            main_data = ((last_run.get("data") or {}).get("main") or [])
            item_count = 0
            preview = ""
            if isinstance(main_data, list) and main_data and isinstance(main_data[0], list):
                item_count = len(main_data[0])
                if main_data[0]:
                    preview_raw = json.dumps(main_data[0][0], ensure_ascii=False, default=str)
                    preview = preview_raw[:500]
            node_runs.append({
                "nodeName": node_name,
                "status": str(last_run.get("executionStatus") or ("error" if node_err_msg else "success")),
                "executionTimeMs": last_run.get("executionTime", 0),
                "itemCount": item_count,
                "outputPreview": preview,
                "error": node_err_msg,
            })

    return {
        "lastNodeExecuted": result_data.get("lastNodeExecuted"),
        "errorMessage": err_msg,
        "nodes": node_runs,
    }


def inspect_executions(workflow_id: Optional[str] = None, limit: int = 20) -> List[Dict[str, Any]]:
    conn = open_readonly_db()
    if conn is None:
        return []
    limit = max(1, min(limit, 100))
    try:
        sql = (
            "SELECT e.id, e.workflowId, e.status, e.mode, e.finished, e.startedAt, e.stoppedAt, "
            "w.name as workflowName, d.data as blobData "
            "FROM execution_entity e "
            "LEFT JOIN workflow_entity w ON e.workflowId = w.id "
            "LEFT JOIN execution_data d ON e.id = d.executionId "
        )
        params: Tuple[Any, ...] = ()
        if workflow_id and SAFE_ID_RE.match(workflow_id):
            sql += "WHERE e.workflowId = ? "
            params = (workflow_id,)
        sql += "ORDER BY e.id DESC LIMIT ?"
        params = params + (limit,)

        rows = conn.execute(sql, params).fetchall()
        out = []
        for r in rows:
            summary = summarize_execution_blob(r["blobData"])
            out.append({
                "id": str(r["id"]),
                "workflowId": str(r["workflowId"] or ""),
                "workflowName": str(r["workflowName"] or r["workflowId"] or ""),
                "status": str(r["status"] or ""),
                "mode": str(r["mode"] or ""),
                "finished": bool(r["finished"]) if r["finished"] is not None else False,
                "startedAt": str(r["startedAt"] or ""),
                "stoppedAt": str(r["stoppedAt"] or ""),
                "lastNodeExecuted": summary["lastNodeExecuted"],
                "errorMessage": summary["errorMessage"],
                "nodeCount": len(summary["nodes"]),
                "nodes": summary["nodes"],
                "source": "bridge_sqlite",
            })
        return out
    finally:
        conn.close()


def inspect_execution_detail(exec_id: str) -> Optional[Dict[str, Any]]:
    if not SAFE_ID_RE.match(exec_id):
        return None
    conn = open_readonly_db()
    if conn is None:
        return None
    try:
        row = conn.execute(
            "SELECT e.id, e.workflowId, e.status, e.mode, e.finished, e.startedAt, e.stoppedAt, "
            "w.name as workflowName, d.data as blobData "
            "FROM execution_entity e "
            "LEFT JOIN workflow_entity w ON e.workflowId = w.id "
            "LEFT JOIN execution_data d ON e.id = d.executionId "
            "WHERE e.id = ? LIMIT 1",
            (exec_id,),
        ).fetchone()
        if row is None:
            return None
        summary = summarize_execution_blob(row["blobData"])
        return {
            "id": str(row["id"]),
            "workflowId": str(row["workflowId"] or ""),
            "workflowName": str(row["workflowName"] or ""),
            "status": str(row["status"] or ""),
            "mode": str(row["mode"] or ""),
            "finished": bool(row["finished"]) if row["finished"] is not None else False,
            "startedAt": str(row["startedAt"] or ""),
            "stoppedAt": str(row["stoppedAt"] or ""),
            "lastNodeExecuted": summary["lastNodeExecuted"],
            "errorMessage": summary["errorMessage"],
            "nodes": summary["nodes"],
            "source": "bridge_sqlite",
        }
    finally:
        conn.close()


def execute_workflow_cli(workflow_id: str) -> Tuple[int, Dict[str, Any]]:
    if not SAFE_ID_RE.match(workflow_id):
        return 400, {"error": "Invalid workflow ID format"}
    try:
        proc = subprocess.run(
            [N8N_BIN, "execute", f"--id={workflow_id}", "--rawOutput"],
            stdin=subprocess.DEVNULL,
            capture_output=True,
            text=True,
            timeout=150,
            env=build_n8n_env(),
        )
        latest = inspect_executions(workflow_id=workflow_id, limit=1)
        return (200 if proc.returncode == 0 else 500), {
            "exitCode": proc.returncode,
            "stdout": proc.stdout[-8000:],
            "stderr": proc.stderr[-4000:],
            "latestExecution": latest[0] if latest else None,
        }
    except subprocess.TimeoutExpired:
        return 504, {"error": "n8n execute timed out after 150s"}
    except Exception as exc:
        return 500, {"error": str(exc)}


def read_n8n_log_tail(lines: int = 50) -> Dict[str, Any]:
    lines = max(1, min(lines, 200))
    if not N8N_LOG_PATH.is_file():
        return {"lines": [], "text": ""}
    try:
        content = N8N_LOG_PATH.read_text(encoding="utf-8", errors="ignore")
        tail = content.splitlines()[-lines:]
        return {"lines": tail, "text": "\n".join(tail)}
    except OSError as exc:
        return {"lines": [], "text": f"Error reading log: {exc}"}


def build_health_payload() -> Dict[str, Any]:
    up, code = probe_n8n_healthz()
    pids = find_n8n_pids()
    booting = (not up) and len(pids) > 0
    if up:
        try:
            N8N_BOOT_TS_PATH.unlink(missing_ok=True)
        except OSError:
            pass
    wf_count = 0
    if N8N_DB_PATH.is_file():
        try:
            conn = open_readonly_db()
            if conn:
                wf_count = conn.execute(
                    "SELECT COUNT(*) FROM workflow_entity WHERE COALESCE(isArchived, 0) = 0"
                ).fetchone()[0]
                conn.close()
        except Exception:
            pass
    return {
        "bridge": "ok",
        "version": "1.0.0",
        "port": BRIDGE_PORT,
        "n8nPort": N8N_PORT,
        "n8nUrl": f"http://127.0.0.1:{N8N_PORT}",
        "n8nRunning": up,
        "n8nHealthCode": code,
        "n8nBooting": booting,
        "n8nPid": pids[0] if pids else None,
        "bootElapsedSeconds": get_boot_elapsed_seconds() if booting else 0,
        "dbExists": N8N_DB_PATH.is_file(),
        "workflowCount": wf_count,
        "tokenRequired": bool(get_expected_token()),
    }


class BridgeHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format: str, *args: Any) -> None:
        return

    def _send_json(self, status: int, payload: Any) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _check_auth(self) -> bool:
        expected = get_expected_token()
        if not expected:
            return True
        provided = self.headers.get("X-Bridge-Token", "").strip()
        if not provided:
            auth = self.headers.get("Authorization", "")
            if auth.lower().startswith("bearer "):
                provided = auth[7:].strip()
        if provided != expected:
            self._send_json(401, {"error": "Unauthorized: invalid or missing X-Bridge-Token"})
            return False
        return True

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        qs = urllib.parse.parse_qs(parsed.query)

        if path == "/health":
            self._send_json(200, build_health_payload())
            return

        if not self._check_auth():
            return

        if path == "/n8n/logs":
            lines = int((qs.get("lines") or ["50"])[0])
            self._send_json(200, read_n8n_log_tail(lines))
        elif path == "/n8n/inspect/workflows":
            items = inspect_workflows_summary()
            self._send_json(200, {"data": items, "count": len(items)})
        elif path.startswith("/n8n/inspect/workflows/"):
            wf_id = path[len("/n8n/inspect/workflows/"):]
            detail = inspect_workflow_detail(wf_id)
            if detail is None:
                self._send_json(404, {"error": f"Workflow '{wf_id}' not found"})
            else:
                self._send_json(200, detail)
        elif path == "/n8n/inspect/executions":
            wf_id = (qs.get("workflowId") or [None])[0]
            limit = int((qs.get("limit") or ["20"])[0])
            items = inspect_executions(workflow_id=wf_id, limit=limit)
            self._send_json(200, {"data": items, "count": len(items)})
        elif path.startswith("/n8n/inspect/executions/"):
            ex_id = path[len("/n8n/inspect/executions/"):]
            detail = inspect_execution_detail(ex_id)
            if detail is None:
                self._send_json(404, {"error": f"Execution '{ex_id}' not found"})
            else:
                self._send_json(200, detail)
        else:
            self._send_json(404, {"error": f"Unknown endpoint: {path}"})

    def do_POST(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        if not self._check_auth():
            return

        # Drain request body safely up to 64 KB
        length = int(self.headers.get("Content-Length", "0") or "0")
        if 0 < length <= 65536:
            self.rfile.read(length)

        if path == "/n8n/start":
            res = start_n8n_background()
            code = 200 if res.get("status") == "running" else 202
            self._send_json(code, res)
        elif path == "/n8n/stop":
            self._send_json(200, stop_n8n_processes())
        elif path.startswith("/n8n/workflows/") and path.endswith("/execute"):
            wf_id = path[len("/n8n/workflows/"):-len("/execute")]
            code, res = execute_workflow_cli(wf_id)
            self._send_json(code, res)
        else:
            self._send_json(404, {"error": f"Unknown endpoint: {path}"})


def is_bridge_alive() -> bool:
    try:
        with urllib.request.urlopen(f"http://{BRIDGE_HOST}:{BRIDGE_PORT}/health", timeout=0.8) as r:
            return r.status == 200
    except Exception:
        return False


def ensure_daemon() -> Dict[str, Any]:
    if not is_bridge_alive():
        BRIDGE_LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
        log_f = open(BRIDGE_LOG_PATH, "a", encoding="utf-8")
        subprocess.Popen(
            [sys.executable, os.path.abspath(__file__), "serve"],
            stdin=subprocess.DEVNULL,
            stdout=log_f,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            close_fds=True,
        )
        log_f.close()
        for _ in range(15):
            if is_bridge_alive():
                break
            time.sleep(0.1)
    health = build_health_payload()
    health["bridgeAlive"] = is_bridge_alive()
    return health


def serve_forever() -> None:
    BRIDGE_PID_PATH.write_text(str(os.getpid()), encoding="utf-8")
    ThreadingHTTPServer.allow_reuse_address = True
    server = ThreadingHTTPServer((BRIDGE_HOST, BRIDGE_PORT), BridgeHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


def main() -> None:
    cmd = sys.argv[1] if len(sys.argv) > 1 else "serve"
    if cmd == "serve":
        serve_forever()
    elif cmd == "ensure-json":
        print(json.dumps(ensure_daemon()))
    elif cmd == "status":
        data = build_health_payload()
        data["bridgeAlive"] = is_bridge_alive()
        print(json.dumps(data, indent=2))
    elif cmd == "start-n8n":
        print(json.dumps(start_n8n_background(), indent=2))
    elif cmd == "stop-n8n":
        print(json.dumps(stop_n8n_processes(), indent=2))
    else:
        print("Usage: autoflow-n8n-bridge [serve|ensure-json|status|start-n8n|stop-n8n]", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
