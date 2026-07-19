#!/usr/bin/env python3
"""One-shot fixer: per-branch app name, OtelConfig, .gitignore; commit each."""
from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
os.chdir(ROOT)

BRANCHES = [
    "main",
    "heartbeat",
    "hitl",
    "hitl_spanlink",
    "retry",
    "human-in-the-loop",
]


def run(args: list[str] | str, check: bool = True) -> subprocess.CompletedProcess:
    if isinstance(args, str):
        print("+", args)
        r = subprocess.run(args, shell=True, text=True, capture_output=True)
    else:
        print("+", " ".join(args))
        r = subprocess.run(args, text=True, capture_output=True)
    if r.stdout.strip():
        print(r.stdout.rstrip())
    if r.stderr.strip():
        print(r.stderr.rstrip())
    if check and r.returncode != 0:
        raise SystemExit(f"cmd failed ({r.returncode}): {args}")
    return r


def ensure_env_file() -> None:
    env = ROOT / ".env"
    if not env.exists():
        env.write_text(
            "NR_ENDPOINT=https://otlp.nr-data.net:4317\n"
            "MY_NEW_RELIC_API_KEY=\n"
        )
        print("created placeholder .env")
    else:
        print(".env exists")


def ensure_gitignore() -> None:
    p = ROOT / ".gitignore"
    text = p.read_text() if p.exists() else ""
    lines = text.splitlines()
    out: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.strip() == "### Local / secrets ###":
            i += 1
            while i < len(lines) and lines[i].strip() in (".env", ".DS_Store", ""):
                i += 1
            continue
        if line.strip() in (".env", ".DS_Store"):
            i += 1
            continue
        out.append(line)
        i += 1
    while out and out[-1].strip() == "":
        out.pop()
    out += ["", "### Local / secrets ###", ".DS_Store", ".env"]
    p.write_text("\n".join(out) + "\n")
    print("gitignore ok")


def set_app_name(name: str) -> None:
    p = ROOT / "src/main/resources/application.yaml"
    text = p.read_text()
    new, n = re.subn(
        r"(spring:\n  application:\n    name:\s*)\S+",
        rf"\g<1>{name}",
        text,
        count=1,
    )
    if n != 1:
        raise SystemExit(f"failed to set app name on {name}: n={n}")
    p.write_text(new)
    print(f"application.name={name}")


def fix_otel(branch: str) -> None:
    cfg = ROOT / "src/main/java/click/yinsb/icmtracing/config"
    via = cfg / "OtelViaCollectorConfig.java"
    otel = cfg / "OtelConfig.java"
    if via.exists() and not otel.exists():
        run(["git", "mv", str(via), str(otel)])
        otel.write_text(otel.read_text().replace("OtelViaCollectorConfig", "OtelConfig"))
        nr_direct = cfg / "NewRelicDirectOtelConfig.java"
        if nr_direct.exists():
            nr_direct.write_text(
                nr_direct.read_text().replace("OtelViaCollectorConfig", "OtelConfig")
            )
        print("renamed OtelViaCollectorConfig -> OtelConfig")

    if not otel.exists():
        raise SystemExit(f"no OtelConfig on {branch}")

    text = otel.read_text()
    if "import org.springframework.context.annotation.Configuration;" not in text:
        text = text.replace(
            "import org.springframework.context.annotation.Bean;",
            "import org.springframework.context.annotation.Bean;\n"
            "import org.springframework.context.annotation.Configuration;",
        )
    if "@Configuration" not in text:
        text = text.replace(
            "public class OtelConfig", "@Configuration\npublic class OtelConfig"
        )

    if (
        '@Value("${spring.application.name}")' not in text
        and 'AttributeKey.stringKey("service.name")' in text
    ):
        if "import org.springframework.beans.factory.annotation.Value;" not in text:
            text = text.replace(
                "import org.springframework.context.annotation.Bean;",
                "import org.springframework.beans.factory.annotation.Value;\n"
                "import org.springframework.context.annotation.Bean;",
            )
        text = re.sub(
            r"OpenTelemetry openTelemetry\(\) \{",
            "OpenTelemetry openTelemetry(\n"
            '            @Value("${spring.application.name}") String applicationName) {',
            text,
            count=1,
        )
        text = re.sub(
            r'AttributeKey\.stringKey\("service\.name"\),\s*"[^"]*"',
            'AttributeKey.stringKey("service.name"), applicationName',
            text,
            count=1,
        )
    otel.write_text(text)
    print("OtelConfig: @Configuration + service.name from spring.application.name")

    nr = cfg / "NrOtelConfig.java"
    if nr.exists():
        t = nr.read_text()
        t = re.sub(r"\n@Configuration\n", "\n", t)
        if "@Configuration" not in t:
            t = t.replace(
                "import org.springframework.context.annotation.Configuration;\n", ""
            )
            if "Unused:" not in t and "To switch" not in t:
                t = t.replace(
                    "public class NrOtelConfig",
                    "// Unused: collector path uses OtelConfig. "
                    "Add @Configuration here and remove it from OtelConfig to switch.\n"
                    "public class NrOtelConfig",
                )
        nr.write_text(t)
        print("NrOtelConfig: @Configuration removed")


def dirty_paths() -> list[str]:
    r = run(["git", "status", "--porcelain"], check=False)
    paths = []
    for line in r.stdout.splitlines():
        if not line.strip():
            continue
        path = line[3:].strip()
        if " -> " in path:
            path = path.split(" -> ", 1)[1]
        if path == ".env" or path.endswith("/.env"):
            continue
        paths.append(path)
    return paths


def commit_if_needed(branch: str) -> bool:
    run(["git", "add", "-A"])
    run(["git", "reset", "HEAD", "--", ".env"], check=False)
    paths = dirty_paths()
    if not paths:
        print(f"{branch}: no changes to commit")
        return False
    msg = (
        f"chore: align app name with branch, use OtelConfig, ignore .env\n\n"
        f"- set spring.application.name to {branch}\n"
        f"- ensure OtelConfig is the active @Configuration\n"
        f"- add .env/.DS_Store to .gitignore"
    )
    run(["git", "commit", "-m", msg])
    print(f"{branch}: committed")
    return True


def main() -> None:
    ensure_env_file()
    results: list[tuple[str, bool]] = []
    start = sys.argv[1] if len(sys.argv) > 1 else None
    started = start is None
    for branch in BRANCHES:
        if not started:
            if branch == start:
                started = True
            else:
                continue
        print(f"\n########## {branch} ##########")
        run(["git", "checkout", "--", "."], check=False)
        # do not wipe untracked .env; avoid deleting tracked-only noise
        run(f"git clean -fd -e .env -e .fix-branches.py", check=False)
        run(["git", "checkout", branch])
        ensure_env_file()
        ensure_gitignore()
        set_app_name(branch)
        fix_otel(branch)
        changed = commit_if_needed(branch)
        results.append((branch, changed))

    print("\n===== SUMMARY =====")
    for b, c in results:
        print(f"{b}: {'committed' if c else 'clean'}")
    print("current:", run(["git", "branch", "--show-current"], check=False).stdout.strip())
    print("env exists:", (ROOT / ".env").exists())


if __name__ == "__main__":
    main()
