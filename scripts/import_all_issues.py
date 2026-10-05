# -*- coding: utf-8 -*-
"""
Create EVERY issue (all 9 sprints) right now, in one go — this is the
script you run today to get all 96 scheduled issues (plus, optionally, the
3 icebox items) onto GitHub immediately, instead of waiting for the weekly
automation.

Run locally:
    pip install requests
    export GITHUB_TOKEN=ghp_xxxxxxxx     # personal access token, 'repo' scope
    export GITHUB_REPOSITORY=lenouu/mobile-expense-management
    python scripts/import_all_issues.py

Add --include-icebox to also create the 3 unscheduled future-ideas issues
(US65-US67), which otherwise stay out of GitHub until you decide to
schedule them into a sprint.

After running this, set automation/state.json's "last_sprint_uploaded" to 8
so the weekly workflow doesn't try to re-create anything later:
    echo '{"last_sprint_uploaded": 8}' > automation/state.json
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from github_issues_lib import load_data, create_issues_for_sprint


def main():
    include_icebox = "--include-icebox" in sys.argv
    data = load_data()
    max_sprint = max(int(k) for k in data["sprints"].keys())

    total_created, total_skipped = 0, 0
    for sprint_num in range(0, max_sprint + 1):
        title = data["sprints"][str(sprint_num)]["title"]
        print(f"\n=== {title} ===")
        created, skipped = create_issues_for_sprint(data, sprint_num)
        total_created += created
        total_skipped += skipped

    if include_icebox:
        print("\n=== Icebox / Future ===")
        from github_issues_lib import get_repo, get_existing_issue_titles, create_issue
        owner, repo = get_repo(data)
        existing_titles = get_existing_issue_titles(owner, repo)
        for issue in data["issues"]:
            if issue["sprint"] is not None:
                continue
            if issue["title"] in existing_titles:
                print(f"  SKIP (already exists): {issue['title']}")
                total_skipped += 1
                continue
            result = create_issue(owner, repo, issue["title"], issue["body"], issue["labels"])
            print(f"  CREATED #{result['number']}: {issue['title']}")
            total_created += 1

    print(f"\nTotal: {total_created} created, {total_skipped} already existed.")


if __name__ == "__main__":
    main()
