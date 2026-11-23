"""Demo only: replaces 7 of the 10 recorded refund answers with answers that miss the 30-day limit."""
import json, pathlib

for f in pathlib.Path("eval-example/src/test/resources/cassettes").glob("*.json"):
    data = json.loads(f.read_text())
    if "refund two weeks" in data["prompt"]:
        for i in range(7):
            data["responses"][i] = "Yes, refunds are possible. Contact support to start one."
        f.write_text(json.dumps(data, indent=2))
        print("broke 7 of 10 answers in", f.name)
