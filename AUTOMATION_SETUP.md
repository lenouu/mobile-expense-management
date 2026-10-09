# Expense Tracker - create ALL issues in one run

Sprint 0, Sprint 1 and Sprint 2 are already done (Sprint 2 was created by hand), so this package
creates **Sprint 3 to Sprint 15 (all remaining issues) at once**, each one:
- assigned to its team member's GitHub account,
- attached to its sprint milestone (created automatically),
- labelled (sprint, epic, priority...).

Sprints last one week each (Sprint 2 = Week 5 ... Sprint 15 = Week 18).

## Team and GitHub accounts (already filled in `data/assignees.json`)

| Member | GitHub |
|---|---|
| Yuhnekeh Sidney Brown (Product Owner) | Yuhnekeh |
| Tagne Fadhil (Scrum Master) | tagnemanuel-afk |
| Biloa Ekassi Lena | lenouu |
| Nguma Kelimbom | N2K-keli |
| Djou Ningaye Stephane | simondns123-hue |

## Steps

1. Upload the contents of this folder to the repo (keep the `.github` folder), commit to the main branch.
2. Check that all 5 accounts are **collaborators** on the repo (invitation accepted). Otherwise GitHub
   refuses the assignment; the issue is still created (unassigned) and listed as a warning in the run log.
3. Repo -> Settings -> Actions -> General -> Workflow permissions -> **Read and write permissions** -> Save.
4. Actions tab -> **Create All Issues** -> **Run workflow**. Read the log at the end: it shows how many issues
   were created and assigned to each person.

Running it again is safe: issues that already exist (same title) are skipped and
`automation/state.json` is already set to "all done" after the first successful run.
No weekly schedule is used any more.
