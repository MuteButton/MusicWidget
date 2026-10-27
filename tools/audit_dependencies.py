"""Scan resolved Maven coordinates with OSV. Sends package names/versions only."""
import json
import pathlib
import sys
import urllib.request


def main():
    inventory = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
    payload = {"queries": [{"package": {"ecosystem": "Maven", "name": p["name"]},
                            "version": p["version"]} for p in inventory]}
    request = urllib.request.Request("https://api.osv.dev/v1/querybatch",
                                     data=json.dumps(payload).encode(),
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=60) as response:
        results = json.load(response)["results"]
    if len(results) != len(inventory):
        raise RuntimeError("Incomplete vulnerability response")
    findings = [{**package, "advisories": [v["id"] for v in result.get("vulns", [])]}
                for package, result in zip(inventory, results) if result.get("vulns")]
    output = pathlib.Path(sys.argv[2])
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"source": "OSV", "packagesChecked": len(inventory),
                                  "findings": findings}, indent=2), encoding="utf-8")
    print(f"OSV: {len(inventory)} packages checked, {len(findings)} packages with advisories")
    for finding in findings:
        print(f"{finding['name']}:{finding['version']}: {', '.join(finding['advisories'])}")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
