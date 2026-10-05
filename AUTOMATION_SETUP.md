# Upload your issues to GitHub — do it now

This package uses GitHub's own free automation feature, **GitHub Actions**.
You don't need to create any token, password, or install anything extra
on your computer to get started.

Every issue now also carries a **Technology** label (`technology-backend`,
`technology-frontend`, `technology-both`, `technology-infra`, or
`technology-process`), on top of priority, epic, sprint and assignee — so
your team can filter the Issues tab by which part of the stack a task
touches.

## Step 1 — Upload these files to your repository

Copy this whole folder's contents into the root of
`lenouu/mobile-expense-management`, keeping the exact paths:

```
.github/workflows/weekly-issues.yml
scripts/github_issues_lib.py
scripts/weekly_rollout.py
scripts/import_all_issues.py
automation/state.json
data/issues.json
AUTOMATION_SETUP.md
```

Easiest way, no Git knowledge needed: on github.com, open your repo →
**Add file → Upload files** → drag the extracted folder in → commit
directly to `main`.

## Step 2 — Turn on write permissions for Actions (one-time)

Repo → **Settings → Actions → General** → under **Workflow permissions**,
select **"Read and write permissions"** → Save. (This lets the automation
create issues and save its own progress file — otherwise it would run but
fail with a permissions error.)

## Step 3 — Create all the issues right now

Go to your repo's **Actions** tab → click **Weekly Issue Rollout** in the
left sidebar → click the **Run workflow** button → **Run workflow** again
to confirm.

That single click uploads **Sprint 0**'s issues and its milestone. Open
the **Issues** tab — they'll be there within a few seconds.

**To get everything today instead of one sprint a week:** click
**Run workflow** again right away (it will roll out Sprint 1), and again
for Sprint 2, and so on — 9 clicks total gets you all 96 scheduled issues
today. It's safe to click it as many times as you like: it always checks
what already exists and never creates the same issue twice.

From here on, you don't need to do anything — the same workflow will also
fire automatically every Monday at 08:00 UTC and pick up wherever it left
off, until all 9 sprints are in.

## Optional — the 3 "icebox" ideas (mascot features)

US65–US67 aren't scheduled into a sprint, so the steps above skip them on
purpose. If you want them created too, you'll need Python installed
locally (this one isn't a button-click):

```
pip install requests
export GITHUB_TOKEN=ghp_xxxxxxxx        # Settings → Developer settings → Personal access tokens → generate one with "repo" scope
export GITHUB_REPOSITORY=lenouu/mobile-expense-management
python scripts/import_all_issues.py --include-icebox
```

## About assignees

GitHub can only auto-assign a real GitHub username, not a plain name. Each
issue's `assignee_username` field in `data/issues.json` is currently blank;
the suggested person (from the workload-balanced plan) is written into the
issue body text instead. To get real GitHub assignment too, fill in each
teammate's GitHub username in that field before clicking "Run workflow"
for that sprint — once an issue is created, editing the file afterwards
won't change it retroactively.

## Changing things later

- Schedule/day: edit the `cron:` line in `.github/workflows/weekly-issues.yml` (UTC time).
- Any story's text, labels, sprint, or technology tag: edit `data/issues.json` before that sprint's issues are created.
