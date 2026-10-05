# -*- coding: utf-8 -*-
"""
Creates ONE sprint's worth of GitHub issues per run, tracking progress in
automation/state.json so each sprint is only uploaded once, even if the
workflow runs again later.

This is the script the scheduled GitHub Actions workflow calls every week.
You normally never need to run it by hand — but you can, to test:
    export GITHUB_TOKEN=ghp_xxx   # only needed if running outside Actions
    python scripts/weekly_rollout.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from github_issues_lib import load_data, create_issues_for_sprint

STATE_PATH = "automation/state.json"


def load_state():
    if not os.path.exists(STATE_PATH):
        return {"last_sprint_uploaded": -1}
    with open(STATE_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def save_state(state):
    os.makedirs(os.path.dirname(STATE_PATH), exist_ok=True)
    with open(STATE_PATH, "w", encoding="utf-8") as f:
        json.dump(state, f, indent=2)


def main():
    data = load_data()
    state = load_state()
    next_sprint = state["last_sprint_uploaded"] + 1
    max_sprint = max(int(k) for k in data["sprints"].keys())

    if next_sprint > max_sprint:
        print("All sprints have already been uploaded. Nothing to do this week.")
        _set_output("rolled_out", "false")
        return

    sprint_title = data["sprints"][str(next_sprint)]["title"]
    print(f"Rolling out issues for {sprint_title} (sprint {next_sprint})...")
    created, skipped = create_issues_for_sprint(data, next_sprint)
    print(f"Done: {created} issue(s) created, {skipped} already existed.")

    state["last_sprint_uploaded"] = next_sprint
    save_state(state)
    _set_output("rolled_out", "true")
    _set_output("sprint", str(next_sprint))


def _set_output(name, value):
    gh_output = os.environ.get("GITHUB_OUTPUT")
    if gh_output:
        with open(gh_output, "a", encoding="utf-8") as f:
            f.write(f"{name}={value}\n")


if __name__ == "__main__":
    main()
