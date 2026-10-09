# -*- coding: utf-8 -*-
"""
Creates EVERY remaining sprint's issues in ONE run (all sprints after
automation/state.json's "last_sprint_uploaded", up to the last sprint),
each issue assigned to its team member's GitHub account and attached to
its sprint milestone. Issues that already exist (same title) are skipped,
so running it twice never duplicates anything.

automation/state.json ships set to 2: Sprint 0, 1 and 2 are treated as
already done, so this run creates Sprint 3 to Sprint 15.

Run from the Actions tab (workflow "Create All Issues"), or locally:
    pip install requests
    export GITHUB_TOKEN=ghp_xxx  GITHUB_REPOSITORY=lenouu/mobile-expense-management
    python scripts/rollout_all.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import github_issues_lib as lib

STATE_PATH = "automation/state.json"


def main():
    data = lib.load_data()
    assignees = lib.load_assignees()
    lib.check_assignees(data, assignees)
    last = -1
    if os.path.exists(STATE_PATH):
        with open(STATE_PATH, encoding="utf-8") as f:
            last = json.load(f)["last_sprint_uploaded"]
    max_sprint = max(int(k) for k in data["sprints"])
    if last >= max_sprint:
        print("Everything has already been uploaded. Nothing to do.")
        _out("rolled_out", "false")
        return

    created = skipped = 0
    for n in range(last + 1, max_sprint + 1):
        print(f"\n=== {data['sprints'][str(n)]['title']} ===")
        c, s = lib.create_issues_for_sprint(data, n, assignees=assignees)
        created += c
        skipped += s

    print(f"\nTOTAL: {created} created, {skipped} already existed.")
    for user, k in sorted(lib.ASSIGNED_COUNT.items()):
        print(f"  assigned to {user}: {k}")
    if lib.UNASSIGNED:
        print(f"\nWARNING: {len(lib.UNASSIGNED)} issue(s) could NOT be assigned (usernames must be repo collaborators):")
        for t, u in lib.UNASSIGNED:
            print(f"  - {t} (wanted {u})")

    os.makedirs(os.path.dirname(STATE_PATH), exist_ok=True)
    with open(STATE_PATH, "w", encoding="utf-8") as f:
        json.dump({"last_sprint_uploaded": max_sprint}, f, indent=2)
    _out("rolled_out", "true")


def _out(name, value):
    gh = os.environ.get("GITHUB_OUTPUT")
    if gh:
        with open(gh, "a", encoding="utf-8") as f:
            f.write(f"{name}={value}\n")


if __name__ == "__main__":
    main()
