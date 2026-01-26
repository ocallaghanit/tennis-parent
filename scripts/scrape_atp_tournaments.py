#!/usr/bin/env python3
import csv
import re
import sys
import time
from typing import List, Dict, Optional, Tuple
import requests
from bs4 import BeautifulSoup

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0 Safari/537.36",
    "Accept-Language": "en-US,en;q=0.9",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/*,*/*;q=0.8",
    "Connection": "keep-alive",
}

TIMEOUT = 20
RETRY = 3
SLEEP_BETWEEN = 0.8

ATP_TOUR_URL = "https://www.atptour.com/en/tournaments"
ATP_CHALLENGER_URL = "https://www.atptour.com/en/atp-challenger-tour/calendar"
ATP_ITF_URL = "https://www.atptour.com/en/atp-challenger-tour/itf-calendar"

# JSON endpoints used by the Vue app behind the pages above
ATP_TOUR_JSON = "https://www.atptour.com/en/-/tournaments/calendar/tour"
ATP_CHALLENGER_JSON = "https://www.atptour.com/en/-/tournaments/calendar/challenger"
ATP_ITF_JSON = "https://www.atptour.com/en/-/tournaments/calendar/futures"

def fetch(url: str) -> Optional[str]:
    for i in range(RETRY):
        try:
            resp = requests.get(url, headers=HEADERS, timeout=TIMEOUT)
            if 200 <= resp.status_code < 300 and resp.text:
                return resp.text
        except requests.RequestException:
            pass
        time.sleep(SLEEP_BETWEEN * (i + 1))
    return None

def normalize_ws(s: Optional[str]) -> str:
    if not s:
        return ""
    return re.sub(r"\s+", " ", s).strip()

def split_location_and_date(s: str) -> Tuple[str, str]:
    if "|" in s:
        parts = [normalize_ws(p) for p in s.split("|", 1)]
        if len(parts) == 2:
            return parts[0], parts[1]
    return "", normalize_ws(s)

def parse_date_range(s: str) -> Tuple[str, str]:
    s = normalize_ws(s)
    if not s:
        return "", ""
    m = re.match(r"([A-Za-z]{3,})\s+(\d{1,2})\-(\d{1,2}),\s*(\d{4})", s)
    if m:
        mon, d1, d2, year = m.groups()
        return f"{mon} {d1}, {year}", f"{mon} {d2}, {year}"
    m = re.match(r"([A-Za-z]{3,})\s+(\d{1,2})\s*-\s*([A-Za-z]{3,})\s+(\d{1,2}),\s*(\d{4})", s)
    if m:
        mon1, d1, mon2, d2, year = m.groups()
        return f"{mon1} {d1}, {year}", f"{mon2} {d2}, {year}"
    m = re.match(r"(\d{4}\.\d{2}\.\d{2})\s*-\s*(\d{4}\.\d{2}\.\d{2})", s)
    if m:
        return m.group(1), m.group(2)
    m = re.match(r"(\d{1,2}\.\d{1,2}\.\d{4})\s*-\s*(\d{1,2}\.\d{1,2}\.\d{4})", s)
    if m:
        return m.group(1), m.group(2)
    toks = re.findall(r"([A-Za-z]{3,}\s+\d{1,2},\s*\d{4}|\d{1,2}\.\d{1,2}\.\d{4}|\d{4}\.\d{2}\.\d{2})", s)
    if len(toks) >= 2:
        return toks[0], toks[1]
    return s, ""

def normalize_date_token(token: str) -> str:
    token = normalize_ws(token)
    if not token:
        return ""
    # Handle formats like "27 December, 2024"
    m = re.match(r"(\d{1,2})\s+([A-Za-z]{3,}),\s*(\d{4})", token)
    if m:
        d, mon, year = m.groups()
        return f"{mon} {int(d)}, {year}"
    # Handle formats like "January 5, 2025"
    m = re.match(r"([A-Za-z]{3,})\s+(\d{1,2}),\s*(\d{4})", token)
    if m:
        mon, d, year = m.groups()
        return f"{mon} {int(d)}, {year}"
    # Pass through other formats
    return token

def parse_formatted_date_range(formatted: str) -> Tuple[str, str]:
    # Expected formats include: "27 December, 2024 - 5 January, 2025" or "January 1-7, 2025"
    if not formatted:
        return "", ""
    if "-" in formatted and "," in formatted and any(c.isdigit() for c in formatted):
        parts = [normalize_ws(p) for p in formatted.split("-", 1)]
        if len(parts) == 2:
            return normalize_date_token(parts[0]), normalize_date_token(parts[1])
    # Fallback to the older parser
    return parse_date_range(formatted)

def find_results_link(container: BeautifulSoup) -> Optional[str]:
    for a in container.find_all("a", href=True):
        txt = normalize_ws(a.get_text())
        if txt.lower() in ("results", "draws") and a["href"].startswith("/"):
            return "https://www.atptour.com" + a["href"]
    return None

def try_extract_surface_text(container: BeautifulSoup) -> Tuple[str, str]:
    text = normalize_ws(container.get_text(separator=" "))
    surf = ""
    io = ""
    m = re.search(r"(Hard|Clay|Grass|Carpet|Acrylic|Synthetic)", text, re.IGNORECASE)
    if m:
        surf = m.group(1).title()
    m = re.search(r"(Indoor|Outdoor)", text, re.IGNORECASE)
    if m:
        io = "Indoor" if m.group(1).lower().startswith("in") else "Outdoor"
    return surf, io

def try_extract_winner_from_results(url: str) -> str:
    html = fetch(url)
    if not html:
        return ""
    soup = BeautifulSoup(html, "lxml")
    txt = soup.get_text(" ", strip=True)
    m = re.search(r"(Singles.*?)((Champion|Winner)[^A-Za-z0-9]+([A-Z][a-zA-Z]+(?:\s+[A-Z][a-zA-Z\-']+){0,3}))", txt, re.IGNORECASE)
    if m:
        cand = m.group(4)
        if 2 <= len(cand.split()) <= 4:
            return normalize_ws(cand)
    m = re.search(r"(Champion|Winner)\s*:\s*([A-Z][a-zA-Z]+(?:\s+[A-Z][a-zA-Z\-']+){0,3})", txt)
    if m:
        return normalize_ws(m.group(2))
    return ""

def parse_calendar_like(url: str, event_type_label: str) -> List[Dict]:
    html = fetch(url)
    if not html:
        print(f"[warn] failed to fetch {url}", file=sys.stderr)
        return []
    soup = BeautifulSoup(html, "lxml")

    rows: List[Dict] = []

    containers = []
    for sel in ["section", "div", "li", "article"]:
        containers.extend(soup.select(sel))

    seen = set()
    for c in containers:
        text = normalize_ws(c.get_text(" ", strip=True))
        if not text:
            continue
        if "|" not in text:
            continue
        if not re.search(r"(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|\d{1,2}\.\d{1,2}\.\d{4}|\d{4}\.\d{2}\.\d{2})", text, re.IGNORECASE):
            continue

        name = ""
        name_a = None
        for a in c.find_all("a"):
            atxt = normalize_ws(a.get_text())
            if not atxt or atxt.lower() in ("draws", "results", "tickets", "profile", "latest"):
                continue
            if len(atxt) <= 2 or len(atxt) > 120:
                continue
            name = atxt
            name_a = a
            break
        if not name:
            continue

        loc_date = ""
        if name_a and name_a.parent:
            sib_text = normalize_ws(name_a.parent.get_text(" ", strip=True))
            sib_text = sib_text.replace(name, "", 1).strip()
            loc_date = sib_text
        if not loc_date:
            loc_date = text
        location, formatted = split_location_and_date(loc_date)
        start_date, end_date = parse_date_range(formatted)

        surface, indoor_outdoor = try_extract_surface_text(c)
        results_url = find_results_link(c)

        key = (name, location, start_date, end_date)
        if key in seen:
            continue
        seen.add(key)

        winner = ""
        if results_url:
            try:
                winner = try_extract_winner_from_results(results_url)
            except Exception:
                winner = ""

        rows.append({
            "name": name,
            "location": location,
            "start_date": start_date,
            "end_date": end_date,
            "surface": surface,
            "indoor_outdoor": indoor_outdoor,
            "winner": winner,
            "results_url": results_url or "",
            "source": event_type_label,
            "source_page": url,
        })

    return dedupe_rows(rows)

def parse_calendar_json(url: str, event_type_label: str) -> List[Dict]:
    try:
        h = dict(HEADERS)
        h.update({"Accept": "application/json, text/plain, */*", "Referer": ATP_TOUR_URL})
        resp = requests.get(url, headers=h, timeout=TIMEOUT)
        if not (200 <= resp.status_code < 300):
            print(f"[warn] json {url} returned {resp.status_code}", file=sys.stderr)
            return []
        data = resp.json()
    except Exception as e:
        print(f"[warn] failed to fetch json {url}: {e}", file=sys.stderr)
        return []

    rows: List[Dict] = []
    seen = set()
    dates = data.get("TournamentDates", []) if isinstance(data, dict) else []
    for d in dates:
        tournaments = d.get("Tournaments", []) if isinstance(d, dict) else []
        for t in tournaments:
            name = normalize_ws(t.get("Name", ""))
            location = normalize_ws(t.get("Location", ""))
            formatted = normalize_ws(t.get("FormattedDate", ""))
            start_date, end_date = parse_formatted_date_range(formatted)
            surface = normalize_ws(t.get("Surface", ""))
            indoor_outdoor = normalize_ws(t.get("IndoorOutdoor", ""))
            draws = t.get("DrawsUrl") or ""
            scores = t.get("ScoresUrl") or ""
            results_url = draws or scores or ""
            if results_url and results_url.startswith("/"):
                results_url = "https://www.atptour.com" + results_url

            key = (name, location, start_date, end_date)
            if key in seen or not name:
                continue
            seen.add(key)

            winner = ""
            if results_url:
                try:
                    winner = try_extract_winner_from_results(results_url)
                except Exception:
                    winner = ""

            rows.append({
                "name": name,
                "location": location,
                "start_date": start_date,
                "end_date": end_date,
                "surface": surface,
                "indoor_outdoor": indoor_outdoor,
                "winner": winner,
                "results_url": results_url,
                "source": event_type_label,
                "source_page": url,
            })

    return dedupe_rows(rows)


def dedupe_rows(rows: List[Dict]) -> List[Dict]:
    out = []
    seen = set()
    for r in rows:
        key = (r.get("name",""), r.get("location",""), r.get("start_date",""), r.get("end_date",""))
        if key in seen:
            continue
        seen.add(key)
        out.append(r)
    return out


def write_csv(path: str, rows: List[Dict]) -> None:
    cols = ["name", "location", "start_date", "end_date", "surface", "indoor_outdoor", "winner", "results_url", "source", "source_page"]
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=cols)
        w.writeheader()
        for r in rows:
            w.writerow({k: r.get(k, "") for k in cols})


def main():
    print("[info] scraping ATP Tour tournaments …", file=sys.stderr)
    atp_rows = parse_calendar_json(ATP_TOUR_JSON, "ATP Tour") or parse_calendar_like(ATP_TOUR_URL, "ATP Tour")
    write_csv("atp_tour_tournaments.csv", atp_rows)
    print(f"[ok] atp_tour_tournaments.csv ({len(atp_rows)} rows)")

    print("[info] scraping ATP Challenger calendar …", file=sys.stderr)
    ch_rows = parse_calendar_json(ATP_CHALLENGER_JSON, "ATP Challenger") or parse_calendar_like(ATP_CHALLENGER_URL, "ATP Challenger")
    write_csv("challenger_tournaments.csv", ch_rows)
    print(f"[ok] challenger_tournaments.csv ({len(ch_rows)} rows)")

    print("[info] scraping ITF calendar …", file=sys.stderr)
    itf_rows = parse_calendar_json(ATP_ITF_JSON, "ITF") or parse_calendar_like(ATP_ITF_URL, "ITF")
    write_csv("itf_tournaments.csv", itf_rows)
    print(f"[ok] itf_tournaments.csv ({len(itf_rows)} rows)")


if __name__ == "__main__":
    main()

