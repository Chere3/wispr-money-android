#!/usr/bin/env python3
"""Servidor MCP de demostración con datos 100 % sintéticos.

Imita las herramientas de Wispr Money que usa la app (Streamable HTTP + JSON-RPC 2.0) para
probar la UI, el widget y la notificación, y para generar las capturas del README, sin tocar un
servidor real ni datos financieros reales.

Uso:
    python3 scripts/demo_server.py            # escucha en 0.0.0.0:8765
    # En el emulador (solo builds debug permiten HTTP a 10.0.2.2):
    adb shell am start -n mx.diego.wispr/.MainActivity --es endpoint http://10.0.2.2:8765/mcp --es token demo

Sin dependencias: solo la biblioteca estándar. Los datos son deterministas (semilla fija) y se
generan alrededor de la fecha actual para que "el mes en curso" siempre tenga movimientos.
"""
import json
import random
import sys
import uuid
from datetime import date, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CURRENCY = "MXN"
TODAY = date.today()


def month_start(d):
    return d.replace(day=1)


def add_months(d, n):
    y, m = divmod(d.month - 1 + n, 12)
    return date(d.year + y, m + 1, 1)


def month_end(d):
    return add_months(d, 1) - timedelta(days=1)


# Catálogo (montos en centavos)

CATEGORIES = [
    {"id": "c-super", "name": "Supermercado", "color": "green", "type": "expense"},
    {"id": "c-rest", "name": "Restaurantes", "color": "orange", "type": "expense"},
    {"id": "c-trans", "name": "Transporte", "color": "blue", "type": "expense"},
    {"id": "c-subs", "name": "Suscripciones", "color": "purple", "type": "expense"},
    {"id": "c-casa", "name": "Casa", "color": "teal", "type": "expense"},
    {"id": "c-salud", "name": "Salud", "color": "red", "type": "expense"},
    {"id": "c-ocio", "name": "Ocio", "color": "pink", "type": "expense"},
    {"id": "c-nomina", "name": "Nómina", "color": "emerald", "type": "income"},
    {"id": "c-freelance", "name": "Freelance", "color": "cyan", "type": "income"},
]
CAT = {c["id"]: c for c in CATEGORIES}

ACCOUNTS = [
    {"id": "a-deb", "name": "Cuenta de nómina", "type": "checking", "currency": CURRENCY, "bank": "Banco Azul", "is_connected": True},
    {"id": "a-ahorro", "name": "Ahorro", "type": "savings", "currency": CURRENCY, "bank": "Banco Azul", "is_connected": True},
    {"id": "a-efectivo", "name": "Efectivo", "type": "cash", "currency": CURRENCY, "bank": None, "is_connected": False},
    {"id": "a-tdc", "name": "Tarjeta Oro", "type": "credit_card", "currency": CURRENCY, "bank": "Banco Verde", "is_connected": True},
    {"id": "a-auto", "name": "Crédito auto", "type": "loan", "currency": CURRENCY, "bank": "Financiera Sol", "is_connected": False},
]

MERCHANTS = {
    "c-super": ["Súper del Barrio", "Mercado Central", "Tienda Fresca"],
    "c-rest": ["Café La Esquina", "Tacos El Güero", "Sushi Nami", "Pizzería Roma"],
    "c-trans": ["Metro", "Gasolina", "Taxi app"],
    "c-subs": ["Streaming Plus", "Música Pro", "Nube 200 GB"],
    "c-casa": ["Luz", "Internet hogar", "Ferretería"],
    "c-salud": ["Farmacia", "Consulta dental"],
    "c-ocio": ["Cine", "Librería", "Concierto"],
}
RANGES = {  # (mín, máx) por movimiento, en pesos
    "c-super": (180, 1400), "c-rest": (90, 650), "c-trans": (30, 900), "c-subs": (99, 299),
    "c-casa": (250, 1200), "c-salud": (150, 1800), "c-ocio": (120, 900),
}
PER_MONTH = {"c-super": 7, "c-rest": 8, "c-trans": 10, "c-subs": 3, "c-casa": 3, "c-salud": 1, "c-ocio": 3}


def build_transactions():
    rng = random.Random(42)
    txs = []
    for back in range(11, -1, -1):
        start = add_months(month_start(TODAY), -back)
        last = TODAY if back == 0 else month_end(start)
        days = (last - start).days + 1

        def day():
            return start + timedelta(days=rng.randrange(days))

        txs.append(("Nómina quincena", "c-nomina", "a-deb", 2_150_000, start))
        if days >= 15:
            txs.append(("Nómina quincena", "c-nomina", "a-deb", 2_150_000, start + timedelta(days=14)))
        if rng.random() < 0.5:
            txs.append(("Proyecto freelance", "c-freelance", "a-deb", rng.randrange(300_000, 900_000, 100), day()))
        scale = days / 30
        for cat, n in PER_MONTH.items():
            for _ in range(max(1, round(n * scale))):
                lo, hi = RANGES[cat]
                amount = -rng.randrange(lo * 100, hi * 100, 50)
                account = rng.choice(["a-deb", "a-tdc", "a-tdc", "a-efectivo"])
                txs.append((rng.choice(MERCHANTS[cat]), cat, account, amount, day()))
    out = []
    for i, (desc, cat, acc, amount, d) in enumerate(sorted(txs, key=lambda t: t[4], reverse=True)):
        a = next(x for x in ACCOUNTS if x["id"] == acc)
        out.append({
            "id": f"t-{i}", "date": d.isoformat(), "description": desc, "amount": amount,
            "currency": CURRENCY, "category_id": cat, "category": CAT[cat]["name"],
            "account_id": acc, "account": a["name"], "labels": [],
        })
    return out


TRANSACTIONS = build_transactions()


def in_range(t, frm, to):
    return (not frm or t["date"] >= frm) and (not to or t["date"] <= to)


def totals(frm, to):
    sel = [t for t in TRANSACTIONS if in_range(t, frm, to)]
    income = sum(t["amount"] for t in sel if t["amount"] > 0)
    expense = -sum(t["amount"] for t in sel if t["amount"] < 0)
    net = income - expense
    return sel, {
        "income": income, "expense": expense, "net": net,
        "savings_rate": round(net / income * 100, 1) if income else None,
        "savings": max(net, 0), "investments": 0,
    }


def by_category(sel, sign):
    sums = {}
    for t in sel:
        if (t["amount"] > 0) == (sign > 0) and t["amount"] != 0:
            sums[t["category_id"]] = sums.get(t["category_id"], 0) + abs(t["amount"])
    return [{"category_id": k, "category": {"id": k, "name": CAT[k]["name"], "color": CAT[k]["color"]},
             "amount": v, "has_children": False} for k, v in sorted(sums.items(), key=lambda kv: -kv[1])]


# Herramientas

def list_budgets(_):
    start, end = month_start(TODAY), month_end(TODAY)
    sel = [t for t in TRANSACTIONS if in_range(t, start.isoformat(), end.isoformat())]

    def budget(bid, name, cats, target):
        # Asignado calculado para que el anillo quede en `target` (fracción gastada): así la demo
        # muestra los tres estados del semáforo sin importar qué día del mes sea.
        spent = -sum(t["amount"] for t in sel if t["category_id"] in cats and t["amount"] < 0)
        allocated = max(round(spent / target / 1_000) * 1_000, 1_000)
        return {"id": bid, "name": name, "period_type": "monthly",
                "categories": [{"id": c, "name": CAT[c]["name"]} for c in cats], "labels": [],
                "current_period": {"start_date": start.isoformat(), "end_date": end.isoformat(),
                                   "allocated_amount": allocated, "carried_over_amount": 0,
                                   "spent_amount": spent, "remaining_amount": allocated - spent,
                                   "processing_historical": False}}

    return {"currency": CURRENCY, "budgets": [
        budget("b-subs", "Suscripciones", ["c-subs"], 1.3),
        budget("b-comida", "Comida", ["c-super", "c-rest"], 0.86),
        budget("b-trans", "Transporte", ["c-trans"], 0.58),
        budget("b-ocio", "Ocio", ["c-ocio"], 0.34),
    ]}


def list_accounts(_):
    return {"space_id": "demo", "accounts": ACCOUNTS}


def list_categories(_):
    return {"categories": [dict(c, icon=None, parent_id=None) for c in CATEGORIES]}


def search_transactions(a):
    sel = [t for t in TRANSACTIONS if in_range(t, a.get("from"), a.get("to"))]
    if a.get("account_id"):
        sel = [t for t in sel if t["account_id"] == a["account_id"]]
    if a.get("category_id"):
        sel = [t for t in sel if t["category_id"] == a["category_id"]]
    if a.get("query"):
        q = a["query"].lower()
        sel = [t for t in sel if q in t["description"].lower()]
    limit = a.get("limit", 50)
    if limit > 200:
        raise ToolError("The limit field must not be greater than 200.")
    return {"count": len(sel), "transactions": sel[:limit]}


def get_cashflow(a):
    frm, to = a["from"], a["to"]
    sel, cur = totals(frm, to)
    f, t = date.fromisoformat(frm), date.fromisoformat(to)
    span = (t - f).days + 1
    _, prev = totals((f - timedelta(days=span)).isoformat(), (f - timedelta(days=1)).isoformat())
    trend, m = [], month_start(f)
    while m <= t:
        _, tm = totals(m.isoformat(), month_end(m).isoformat())
        trend.append({"month": m.strftime("%Y-%m"), "income": tm["income"], "expense": tm["expense"], "net": tm["net"]})
        m = add_months(m, 1)
    return {"summary": {"current": cur, "previous": prev},
            "sankey": {"income_categories": by_category(sel, 1), "expense_categories": by_category(sel, -1),
                       "total_income": cur["income"], "total_expense": cur["expense"]},
            "trend": {"data": trend}}


def spending_by_category(a):
    sel, _ = totals(a["from"], a["to"])
    return {"categories": by_category(sel, -1)}


def get_net_worth(a):
    start = month_start(date.fromisoformat(a["from"]))
    end = date.fromisoformat(a["to"])
    points, m, i = [], start, 0
    while m <= end:
        k = (m.year - TODAY.year) * 12 + m.month - TODAY.month  # 0 = mes actual, negativo = pasado
        points.append({"month": m.strftime("%Y-%m"), "timestamp": int(m.strftime("%s")),
                       "a-deb": 1_850_000 + 40_000 * k + (i % 3) * 25_000,
                       "a-ahorro": 9_800_000 + 520_000 * k,
                       "a-efectivo": 120_000 + (i % 2) * 30_000,
                       "a-tdc": -(980_000 + (i % 4) * 110_000),
                       "a-auto": 6_400_000 - 310_000 * k})  # el préstamo baja cada mes
        m, i = add_months(m, 1), i + 1

    def net(p):
        return p["a-deb"] + p["a-ahorro"] + p["a-efectivo"] + p["a-tdc"] - p["a-auto"]

    current = net(points[-1]) if points else 0
    previous = net(points[-2]) if len(points) > 1 else current
    return {"current": {"current": current, "previous": previous, "currency_code": CURRENCY},
            "evolution": {"data": points}}


def create_transaction(a):
    return {"id": f"t-{uuid.uuid4().hex[:8]}", "created": True, "note": "demo: no se guarda"}


TOOLS = {f.__name__: f for f in [list_budgets, list_accounts, list_categories, search_transactions,
                                 get_cashflow, spending_by_category, get_net_worth, create_transaction]}


class ToolError(Exception):
    pass


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        if "id" not in body:  # notificación
            self.send_response(202)
            self.end_headers()
            return
        method, params = body.get("method"), body.get("params") or {}
        headers = {}
        if method == "initialize":
            result = {"protocolVersion": params.get("protocolVersion", "2025-06-18"),
                      "capabilities": {"tools": {}}, "serverInfo": {"name": "wispr-demo", "version": "1.0"}}
            headers["Mcp-Session-Id"] = uuid.uuid4().hex
        elif method == "tools/call":
            tool = TOOLS.get(params.get("name"))
            try:
                if tool is None:
                    raise ToolError(f"Herramienta desconocida: {params.get('name')}")
                text, is_error = json.dumps(tool(params.get("arguments") or {}), ensure_ascii=False), False
            except ToolError as e:
                text, is_error = str(e), True
            result = {"content": [{"type": "text", "text": text}], "isError": is_error}
        else:
            result = {}
        payload = json.dumps({"jsonrpc": "2.0", "id": body["id"], "result": result}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        for k, v in headers.items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, fmt, *args):
        sys.stderr.write("demo · " + fmt % args + "\n")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    print(f"Servidor MCP de demostración en http://0.0.0.0:{port}/mcp (datos sintéticos)")
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
