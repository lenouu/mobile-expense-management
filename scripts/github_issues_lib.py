# -*- coding: utf-8 -*-
"""
Shared helpers for talking to the GitHub REST API to create milestones and
issues from data/issues.json.

Requires: pip install requests
Auth: reads a token from the GITHUB_TOKEN environment variable. Inside a
GitHub Actions workflow this is automatically provided as
${{ secrets.GITHUB_TOKEN }} — you do NOT need to create a personal access
token for this to work inside your own repo's Actions.
Repo: reads owner/name from the GITHUB_REPOSITORY environment variable
(automatically set inside Actions as "owner/repo"). Falls back to the
defaults stored in issues.json when run locally without that variable set.
"""
import json
import os
import sys
import requests

API_ROOT = "https://api.github.com"


def load_data(path="data/issues.json"):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_assignees(path="data/assignees.json"):
    """Maps a suggested owner's plain name (as used in issues.json) to
    their real GitHub username. Returns {} if the file is missing, so
    everything still works (just unassigned) before it's filled in."""
    if not os.path.exists(path):
        return {}
    with open(path, "r", encoding="utf-8") as f:
        raw = json.load(f)
    return {k: v for k, v in raw.items() if not k.startswith("_") and v}


def get_repo(data):
    env_repo = os.environ.get("GITHUB_REPOSITORY")
    if env_repo and "/" in env_repo:
        owner, name = env_repo.split("/", 1)
        return owner, name
    return data["repo_owner_default"], data["repo_name_default"]


def get_token():
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        sys.exit(
            "ERROR: GITHUB_TOKEN environment variable is not set.\n"
            "Inside GitHub Actions this is provided automatically as "
            "secrets.GITHUB_TOKEN.\n"
            "To run this locally instead, create a Personal Access Token "
            "(classic, 'repo' scope) at https://github.com/settings/tokens "
            "and run:\n"
            "  export GITHUB_TOKEN=ghp_xxxxxxxx   (macOS/Linux)\n"
            "  set GITHUB_TOKEN=ghp_xxxxxxxx      (Windows cmd)\n"
        )
    return token


def _headers():
    return {
        "Authorization": f"Bearer {get_token()}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }


def get_or_create_milestone(owner, repo, title, description=""):
    """Return the milestone number for `title`, creating it if needed."""
    url = f"{API_ROOT}/repos/{owner}/{repo}/milestones"
    page = 1
    while True:
        r = requests.get(url, headers=_headers(), params={"state": "all", "per_page": 100, "page": page})
        r.raise_for_status()
        items = r.json()
        if not items:
            break
        for m in items:
            if m["title"] == title:
                return m["number"]
        page += 1

    r = requests.post(url, headers=_headers(), json={"title": title, "description": description})
    r.raise_for_status()
    return r.json()["number"]


def get_existing_issue_titles(owner, repo):
    """Titles of every issue (open or closed) already in the repo, so we
    never create the same issue twice on a re-run."""
    titles = set()
    url = f"{API_ROOT}/repos/{owner}/{repo}/issues"
    page = 1
    while True:
        r = requests.get(url, headers=_headers(), params={"state": "all", "per_page": 100, "page": page})
        r.raise_for_status()
        items = r.json()
        if not items:
            break
        for it in items:
            if "pull_request" not in it:  # PRs show up in this endpoint too
                titles.add(it["title"])
        page += 1
    return titles


def create_issue(owner, repo, title, body, labels, milestone_number=None, assignee_username=""):
    url = f"{API_ROOT}/repos/{owner}/{repo}/issues"
    payload = {"title": title, "body": body, "labels": labels}
    if milestone_number is not None:
        payload["milestone"] = milestone_number
    if assignee_username:
        payload["assignees"] = [assignee_username]
    r = requests.post(url, headers=_headers(), json=payload)
    if r.status_code >= 400 and assignee_username:
        # Most likely cause: that username isn't a collaborator on the repo
        # yet, so GitHub refused the whole request. Retry unassigned rather
        # than losing the issue entirely, and say so.
        print(f"  WARNING: could not assign '{assignee_username}' on '{title}' "
              f"(are they added as a collaborator on the repo?). Creating it unassigned instead.")
        payload.pop("assignees", None)
        r = requests.post(url, headers=_headers(), json=payload)
    r.raise_for_status()
    return r.json()


def create_issues_for_sprint(data, sprint_num, assignees=None, verbose=True):
    """Create every not-yet-created issue for one sprint number.
    `assignees` maps a suggested owner's plain name to their GitHub
    username (see load_assignees) — pass {} or omit to leave everyone
    unassigned on GitHub for now.
    Returns (created_count, skipped_count)."""
    assignees = assignees or {}
    owner, repo = get_repo(data)
    sprint_info = data["sprints"][str(sprint_num)]
    milestone_number = get_or_create_milestone(
        owner, repo, sprint_info["title"], sprint_info["goal"]
    )
    existing_titles = get_existing_issue_titles(owner, repo)

    created, skipped = 0, 0
    for issue in data["issues"]:
        if issue["sprint"] != sprint_num:
            continue
        if issue["title"] in existing_titles:
            if verbose:
                print(f"  SKIP (already exists): {issue['title']}")
            skipped += 1
            continue
        assignee_username = assignees.get(issue.get("suggested_assignee_name", ""), "")
        result = create_issue(
            owner, repo,
            issue["title"], issue["body"], issue["labels"],
            milestone_number=milestone_number,
            assignee_username=assignee_username,
        )
        if verbose:
            who = f" -> {assignee_username}" if assignee_username else " (unassigned)"
            print(f"  CREATED #{result['number']}: {issue['title']}{who}")
        created += 1
    return created, skipped
