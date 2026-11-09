#requires -Version 5.1
<#
    Builds the FinFlow two-sprint review deck (Sprints 1 & 2) as a native .pptx.

    Why this is a hybrid build
    --------------------------
    PowerPoint writes the canonical, guaranteed-valid package, so slide structure,
    masters, layouts, theme and speaker notes are all created through PowerPoint
    COM automation. That is also what makes the result a real, fully editable deck.

    Shapes.AddPicture deadlocks when PowerPoint runs without an interactive window
    in this session, so pictures are injected afterwards at the OOXML level. The
    injection follows the same rules PowerPoint itself uses: unique media part
    names, numeric relationship ids unique within their part, and a <a:blip
    r:embed> that resolves in that slide's own .rels file.

    Run from the repository root:
        powershell -NoProfile -ExecutionPolicy Bypass -File powerPoint\build-deck.ps1
#>
param(
    [string]$OutPath = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression             | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem  | Out-Null
Add-Type -AssemblyName System.Drawing                    | Out-Null

$Root     = Split-Path -Parent $PSScriptRoot
$ImgDir   = Join-Path $Root 'powerPoint'
$UmlDir   = Join-Path $Root 'docs\architecture\UML  and Modeling Diagrams'
$FigmaDir = Join-Path $Root 'mobile\figmaInspiration'
$Final    = if ($OutPath) { $OutPath } else { Join-Path $ImgDir 'FinFlow-Sprint-1-and-2-Review.pptx' }
$Temp     = Join-Path $ImgDir '_build-temp.pptx'
$Extract  = Join-Path $ImgDir '_pkg'

# ------------------------------------------------------------------- palette
$NAVY   = 0x02284C   # #02284C  logo dark half: headings and body
$GREEN  = 0x01916D   # #01916D  logo bright half: primary accent
$DGREEN = 0x016B52
$LGREEN = 0xE6F4F0
$LNAVY  = 0xEAF1F8
$BG     = 0xF5FAFF   # page wash, straight from mobile/src/constants/theme.ts
$BODY   = 0x335372
$MUTED  = 0x94A3B8
$BORDER = 0xE2E8F0
$WHITE  = 0xFFFFFF
$BLACK  = 0x101820
$DANGER = 0xD93025
$AMBER  = 0xB45309
$FONT   = 'Segoe UI'
$MONO   = 'Consolas'

# COM takes points; the layout is authored in a 1000 x 562.5 unit space (16:9),
# where one unit is 0.72 pt.
$PT = 0.72
function P([double]$u) { $u * $PT }

$script:Pics = New-Object System.Collections.ArrayList

# ============================================================== shape helpers
function Add {
    param($slide, [string]$text, [double]$l, [double]$t, [double]$w, [double]$h,
          [double]$size, [int]$col, [bool]$bold = $false, [bool]$italic = $false,
          [string]$align = 'l', [string]$font = $FONT, [double]$space = 0)
    $tb = $slide.Shapes.AddTextbox(1, (P $l), (P $t), (P $w), (P $h))
    $tf = $tb.TextFrame
    $tf.WordWrap = -1
    $tf.AutoSize = 0
    $tf.MarginLeft = 0; $tf.MarginRight = 0; $tf.MarginTop = 0; $tf.MarginBottom = 0
    $tf.VerticalAnchor = 1
    $tr = $tf.TextRange
    $tr.Text = $text
    if ($text -ne '') {
        $tr.Font.Name = $font
        $tr.Font.Size = $size * $PT
        $tr.Font.Color.RGB = $col
        $tr.Font.Bold = [int]$bold
        $tr.Font.Italic = [int]$italic
        # The PowerPoint COM Font object has no character-spacing property, so the
        # small overlines are set without letter spacing.
        switch ($align) {
            'c' { $tr.ParagraphFormat.Alignment = 2 }
            'r' { $tr.ParagraphFormat.Alignment = 3 }
            default { $tr.ParagraphFormat.Alignment = 1 }
        }
    }
    return $tb
}

function Bullets {
    param($slide, [string[]]$items, [double]$l, [double]$t, [double]$w, [double]$h,
          [double]$size, [int]$col, [string]$font = $FONT, [double]$space = 4, [string]$prefix = '- ')
    $tb = $slide.Shapes.AddTextbox(1, (P $l), (P $t), (P $w), (P $h))
    $tf = $tb.TextFrame
    $tf.WordWrap = -1; $tf.AutoSize = 0
    $tf.MarginLeft = 0; $tf.MarginRight = 0; $tf.MarginTop = 0; $tf.MarginBottom = 0
    $tf.VerticalAnchor = 1
    $tr = $tf.TextRange
    $tr.Text = (($items | ForEach-Object { $prefix + $_ }) -join "`r")
    $tr.Font.Name = $font
    $tr.Font.Size = $size * $PT
    $tr.Font.Color.RGB = $col
    $tr.ParagraphFormat.SpaceWithin = $space
    return $tb
}

function Rect {
    param($slide, [double]$l, [double]$t, [double]$w, [double]$h, [int]$fill, [int]$line = -1)
    $sh = $slide.Shapes.AddShape(1, (P $l), (P $t), (P $w), (P $h))   # msoShapeRectangle
    $sh.Fill.Solid(); $sh.Fill.ForeColor.RGB = $fill
    if ($line -lt 0) { $sh.Line.Visible = 0 } else { $sh.Line.Visible = -1; $sh.Line.ForeColor.RGB = $line; $sh.Line.Weight = 0.75 }
    $sh.Shadow.Visible = 0
    return $sh
}

function Round {
    param($slide, [double]$l, [double]$t, [double]$w, [double]$h, [int]$fill, [int]$line = -1)
    $sh = $slide.Shapes.AddShape(5, (P $l), (P $t), (P $w), (P $h))   # msoShapeRoundedRectangle
    $sh.Fill.Solid(); $sh.Fill.ForeColor.RGB = $fill
    if ($line -lt 0) { $sh.Line.Visible = 0 } else { $sh.Line.Visible = -1; $sh.Line.ForeColor.RGB = $line; $sh.Line.Weight = 0.75 }
    $sh.Shadow.Visible = 0
    return $sh
}

function Ellipse {
    param($slide, [double]$l, [double]$t, [double]$w, [double]$h, [int]$fill)
    $sh = $slide.Shapes.AddShape(9, (P $l), (P $t), (P $w), (P $h))   # msoShapeOval
    $sh.Fill.Solid(); $sh.Fill.ForeColor.RGB = $fill
    $sh.Line.Visible = 0
    $sh.Shadow.Visible = 0
    return $sh
}

# Records an image for OOXML injection, preserving aspect ratio inside the box.
function Pic {
    param($slide, [string]$name, [string]$path, [double]$l, [double]$t, [double]$w, [double]$h)
    if (-not (Test-Path -LiteralPath $path)) { throw "missing image: $path" }
    $img = [System.Drawing.Image]::FromFile($path)
    $ar = $img.Width / $img.Height
    $img.Dispose()
    $boxAr = $w / $h
    if ($ar -gt $boxAr) { $nw = $w; $nh = $w / $ar } else { $nh = $h; $nw = $h * $ar }
    $null = $script:Pics.Add([pscustomobject]@{
        Slide = $slide.SlideIndex
        Name  = $name
        Path  = $path
        L     = $l + ($w - $nw) / 2
        T     = $t + ($h - $nh) / 2
        W     = $nw
        H     = $nh
    })
}

function HeaderBar {
    param($slide, [string]$eyebrow, [string]$title)
    Rect $slide 0 0 1000 87 $NAVY | Out-Null
    Rect $slide 0 87 1000 4.2 $GREEN | Out-Null
    Add $slide $eyebrow.ToUpper() 46 11 900 22 7.2 $MUTED $true $false 'l' $FONT 1.4 | Out-Null
    Add $slide $title 46 31 920 42 18 $WHITE $true | Out-Null
}

function FooterBar {
    param($slide, [int]$n)
    Rect $slide 46 524 908 0.9 $BORDER | Out-Null
    Add $slide 'FinFlow  |  Expense Tracker  |  Agile Project Management' 46 530 700 18 6.2 $MUTED | Out-Null
    Add $slide "$n" 800 530 154 18 6.2 $MUTED $false $false 'r' | Out-Null
}

function Note {
    param($slide, [string]$text)
    $slide.NotesPage.Shapes.Placeholders.Item(2).TextFrame.TextRange.Text = $text
}

function Card {
    param($slide, [double]$l, [double]$t, [double]$w, [double]$h, [string]$title, [string[]]$lines,
          [int]$accent = $GREEN, [double]$titleSize = 10, [double]$bodySize = 7.6)
    Round $slide $l $t $w $h $WHITE $BORDER | Out-Null
    Rect  $slide $l ($t + 12) 4.4 ($h - 24) $accent | Out-Null
    Add   $slide $title ($l + 21) ($t + 12) ($w - 38) 22 $titleSize $NAVY $true | Out-Null
    if ($lines -and $lines.Count -gt 0) {
        Bullets $slide $lines ($l + 21) ($t + 46) ($w - 38) ($h - 56) $bodySize $BODY $FONT 4 | Out-Null
    }
}

function Pill {
    param($slide, [double]$l, [double]$t, [double]$w, [double]$h, [string]$line1, [string]$line2,
          [int]$fill = $LGREEN, [int]$fg = $DGREEN)
    Round $slide $l $t $w $h $fill | Out-Null
    Add $slide $line1 ($l + 12) ($t + 11) ($w - 24) 26 12 $fg $true | Out-Null
    if ($line2) { Add $slide $line2 ($l + 12) ($t + 38) ($w - 24) 22 7.4 $BODY }
}

function Stat {
    param($slide, [double]$l, [double]$t, [double]$w, [double]$h, [string]$big, [string]$small)
    Round $slide $l $t $w $h $LGREEN | Out-Null
    Add $slide $big   ($l + 14) ($t + 8)  ($w - 28) 30 11.5 $DGREEN $true | Out-Null
    Add $slide $small ($l + 14) ($t + 38) ($w - 28) 24 6.6 $BODY | Out-Null
}

function StatNavy {
    param($slide, [double]$l, [double]$t, [double]$w, [double]$h, [string]$big, [string]$small)
    Round $slide $l $t $w $h $LNAVY | Out-Null
    Add $slide $big   ($l + 14) ($t + 10) ($w - 28) 30 12 $NAVY $true | Out-Null
    Add $slide $small ($l + 14) ($t + 42) ($w - 28) 30 6.4 $BODY | Out-Null
}

# ================================================================ build deck
$ppt = $null; $pres = $null
try {
    $ppt  = New-Object -ComObject PowerPoint.Application
    $pres = $ppt.Presentations.Add(0)
    $pres.PageSetup.SlideWidth  = 13.333
    $pres.PageSetup.SlideHeight = 7.5

    function NewSlide {
        param([int]$index, [int]$bg, [string]$notes)
        $s = $pres.Slides.Add($index, 12)      # 12 = ppLayoutBlank
        $s.FollowMasterBackground = 0
        $s.Background.Fill.Solid()
        $s.Background.Fill.ForeColor.RGB = $bg
        # Out-Null matters: a bare Note call would emit its result into this
        # function's output, so callers would receive an array instead of a slide.
        Note $s $notes | Out-Null
        return $s
    }

    # ------------------------------------------------------------ 01 cover
    $s = NewSlide 1 $BLACK @'
Good morning. We are the five-member Scrum team behind Expense Tracker, branded FinFlow, and this review covers the two three-week sprints we have just completed: Sprint 1 on the Account, Session and Admin Foundation, and Sprint 2 on Standalone Expense and Income Tracking.

Everything in this deck comes from our own project artefacts: the product vision and backlog documents, the Trello board, and the GitHub repository where the work was merged. The presentation takes about fifteen minutes and we are happy to take questions at the end.
'@
    Rect $s 0 0 1000 562.5 $BLACK | Out-Null
    Rect $s 0 0 12 562.5 $GREEN | Out-Null
    Pic $s 'logo-cover' (Join-Path $ImgDir 'logo.jpeg') 54 96 191 195 | Out-Null
    Add $s 'REPUBLIC OF CAMEROON  |  AGILE PROJECT MANAGEMENT  |  MR KUATE' 281 96 660 20 7.2 $MUTED $true $false 'l' $FONT 1.5 | Out-Null
    Add $s 'Expense Tracker' 281 129 660 74 30 $WHITE $true | Out-Null
    Add $s 'Two-Sprint Review' 281 205 660 76 18 $GREEN $true | Out-Null
    Rect $s 281 279 128 3.4 $GREEN | Out-Null
    Add $s 'Sprint 1: Account, Session & Admin Foundation   |   Sprint 2: Standalone Expense & Income Tracking' 281 298 660 26 8.4 $WHITE $true | Out-Null
    Add $s 'Delivered over two three-week Scrum sprints, 19 September to 9 October 2026, with a daily stand-up every school day at 10:00 for ten minutes.' 281 328 660 30 7.8 $MUTED | Out-Null
    Add $s 'Presented by Biloa Ekassi Lena, Tagne Fadhil, Djou Ningaye Simon, Nguma Kelimbom and Yuhnekeh Sidney Brown.' 281 358 660 30 7.8 $MUTED | Out-Null
    Add $s 'Track. Understand. Improve.' 281 400 660 24 8.4 $GREEN $true | Out-Null
    FooterBar $s 1 | Out-Null

    # ----------------------------------------------------------- 02 agenda
    $s = NewSlide 2 $BG @'
Here is the shape of the next fifteen minutes. We start with who we are and the cadence we committed to, then the two things the brief asks us to summarise: our daily stand-up practice and the Trello and GitHub workflow we set up.

After that we walk through the product vision update and take each sprint in turn, covering the goal, the user stories it carried and what the sprint actually produced. We finish with our Definition of Done and what Sprint 3 will pick up.
'@
    HeaderBar $s 'What we will cover' 'Agenda' | Out-Null
    FooterBar $s 2 | Out-Null
    $ag = @(
        @('1', 'Team & sprint cadence', 'Who we are, how long each sprint ran, and the rhythm we worked to.'),
        @('2', 'Daily stand-up practice', 'Ten minutes, every school day at 10:00, three questions each.'),
        @('3', 'Trello & GitHub workflow', 'How a story moved from a card to a reviewed merge on develop.'),
        @('4', 'Product vision update', 'The corrections we folded into the vision and the backlog in Sprint 1.'),
        @('5', 'Sprint 1 outcome', 'Account, session and admin foundation: US01 to US11.'),
        @('6', 'Sprint 2 outcome', 'Standalone expense and income tracking: US12 to US26.')
    )
    for ($k = 0; $k -lt 6; $k++) {
        $col = $k % 3; $row = [math]::Floor($k / 3)
        $l = 46 + $col * 307; $t = 117 + $row * 117
        Round $s $l $t 295 106 $WHITE $BORDER | Out-Null
        Rect  $s $l $t 3.6 106 $GREEN | Out-Null
        Ellipse $s ($l + 18) ($t + 17) 27 27 $LGREEN | Out-Null
        Add $s $ag[$k][0] ($l + 18) ($t + 22) 27 20 9.5 $DGREEN $true $false 'c' | Out-Null
        Add $s $ag[$k][1] ($l + 54) ($t + 18) 226 22 10 $NAVY $true | Out-Null
        Add $s $ag[$k][2] ($l + 18) ($t + 51) 259 46 7.2 $BODY | Out-Null
    }

    # ------------------------------------------------------------- 03 team
    $s = NewSlide 3 $BG @'
Five of us, and everyone writes code. Two of us carry a Scrum role on top of development. Sidney is our Product Owner: he owns the backlog and its order, writes the user stories and acceptance criteria, and accepts or rejects work at the end of each sprint. Fadhil is our Scrum Master: he runs sprint planning, the daily stand-up, the review and the retrospective, and his main job is clearing blockers.

Lena, myself and Simon are developers, implementing and testing the stories assigned each sprint and reviewing each other's pull requests.

Both sprints ran three weeks, covering 19 September to 9 October 2026.
'@
    HeaderBar $s 'Who we are' 'Team, Roles & Sprint Cadence' | Out-Null
    FooterBar $s 3 | Out-Null
    $members = @(
        @('Yuhnekeh Sidney Brown', 'Product Owner', 'Owns and orders the backlog; writes user stories and acceptance criteria; accepts or rejects work at sprint review.'),
        @('Tagne Fadhil', 'Scrum Master', 'Runs sprint planning, the daily stand-up, review and retrospective; removes blockers; guards the Definition of Done.'),
        @('Biloa Ekassi Lena', 'Developer', 'Implements and tests the stories assigned each sprint; reviews pull requests before merge.'),
        @('Nguma Kelimbom', 'Developer', 'Implements and tests the stories assigned each sprint; reviews pull requests before merge.'),
        @('Djou Ningaye Simon', 'Developer', 'Implements and tests the stories assigned each sprint; reviews pull requests before merge.')
    )
    Rect $s 46 116 682 26 $NAVY | Out-Null
    Add $s 'MEMBER' 58 120 240 18 7.2 $WHITE $true $false 'l' $FONT 1.2 | Out-Null
    Add $s 'ROLE' 308 120 150 18 7.2 $WHITE $true $false 'l' $FONT 1.2 | Out-Null
    Add $s 'RESPONSIBILITY' 478 120 244 18 7.2 $WHITE $true $false 'l' $FONT 1.2 | Out-Null
    for ($r = 0; $r -lt 5; $r++) {
        $t = 142 + $r * 51
        if ($r % 2 -eq 1) { Rect $s 46 $t 682 51 $LNAVY | Out-Null }
        Rect $s 46 ($t + 50) 682 0.9 $BORDER | Out-Null
        Add $s $members[$r][0] 58 ($t + 7) 240 18 7.9 $NAVY $true | Out-Null
        Add $s $members[$r][1] 308 ($t + 7) 160 18 7.5 $DGREEN $true | Out-Null
        Add $s $members[$r][2] 478 ($t + 7) 244 42 6.6 $BODY | Out-Null
    }
    Pill $s 750 116 204 90 '3 weeks' 'per sprint' | Out-Null
    Pill $s 750 216 204 90 '19 Sep - 9 Oct' '2026' | Out-Null
    Pill $s 750 316 204 100 '5 members' 'all developers' | Out-Null
    Card $s 750 428 204 94 'Two sprints delivered' @('Sprint 1 - weeks 1 to 3', 'Sprint 2 - weeks 4 to 6') $GREEN 8.6 7 | Out-Null

    # ---------------------------------------------------------- 04 stand-up
    $s = NewSlide 4 $BG @'
Our daily stand-up was the single most important ritual of both sprints. It ran every school day at ten in the morning and was capped at ten minutes.

Each developer answered three questions: what I finished yesterday, what I am doing today, and what is blocking me. The third question is the one that earned its place - blockers were captured in the meeting and the Scrum Master took them away immediately instead of us discovering them at the end of the sprint.

As Scrum Master, Fadhil protected the ten minutes. If a topic needed real discussion we took it offline right after, which is why the stand-up stayed short every single day.
'@
    HeaderBar $s 'How we stayed in sync' 'The Daily Stand-up' | Out-Null
    FooterBar $s 4 | Out-Null
    Add $s 'Every school day at 10:00, ten minutes, three questions each. Nothing else was allowed into the meeting.' 46 113 908 24 8.6 $BODY | Out-Null
    $q = @(
        @('1', 'What did I do yesterday?', 'Each developer reported what they finished the previous day and how it moved the Sprint Goal forward.'),
        @('2', 'What am I doing today?', 'The work planned for the present day of the sprint, so the team always knew what was in flight and who held it.'),
        @('3', 'What is blocking me?', 'Any blocker was raised out loud and picked up immediately by the Scrum Master rather than left to fester.')
    )
    for ($k = 0; $k -lt 3; $k++) {
        $l = 46 + $k * 307; $t = 152; $w = 295; $h = 188
        Round $s $l $t $w $h $WHITE $BORDER | Out-Null
        Rect  $s $l $t $w 4.2 $GREEN | Out-Null
        Ellipse $s ($l + 22) ($t + 22) 38 38 $LGREEN | Out-Null
        Add $s $q[$k][0] ($l + 22) ($t + 29) 38 28 13 $DGREEN $true $false 'c' | Out-Null
        Add $s $q[$k][1] ($l + 22) ($t + 74) ($w - 44) 40 10.5 $NAVY $true | Out-Null
        Add $s $q[$k][2] ($l + 22) ($t + 124) ($w - 44) 56 7.4 $BODY | Out-Null
    }
    $st = @(
        @('10 minutes', 'Hard cap, every day'),
        @('Every school day', 'Mon to Fri, 10:00am'),
        @('3 questions', 'Each developer answers'),
        @('Blockers owned', 'SM picks them up live')
    )
    for ($k = 0; $k -lt 4; $k++) {
        $l = 46 + $k * 231; $t = 358; $w = 215; $h = 78
        Round $s $l $t $w $h $LNAVY | Out-Null
        Add $s $st[$k][0] ($l + 14) ($t + 12) ($w - 28) 26 10 $NAVY $true | Out-Null
        Add $s $st[$k][1] ($l + 14) ($t + 40) ($w - 28) 24 7.2 $BODY | Out-Null
    }
    Add $s 'Ten minutes was the rule we actually kept. Longer problems went to a separate conversation straight after, so the stand-up never turned into a design meeting.' 46 452 908 16 6.6 $MUTED $false $true | Out-Null

    # ---------------------------------------------------------- 05 workflow
    $s = NewSlide 5 $BG @'
This is the part of the brief that asks how Trello and GitHub connect, so let us be concrete.

Trello was our single board of record. The lists ran left to right as Sprint 0 through Sprint 8, which meant the board itself showed the build order rather than hiding it in a spreadsheet. Each user story was a card with its acceptance criteria as a checklist and a priority label from P0 to P3. Technical setup work, such as CI/CD and the database schema, got its own cards so it was obvious what was product work and what was engineering groundwork.

GitHub held one shared repository with main and develop. Every story got its own branch off develop, named by story number and feature. Nobody committed directly to develop or main - you branched, built, opened a pull request back into develop, and another developer reviewed it before it merged.

The connector between the two was the GitHub Issue: one story, one Trello card, one issue, one branch, one pull request. When the review passed and the merge landed, the card moved to done.

The honest status: the branch naming convention and the GitHub half are the parts we have genuinely exercised so far. You can see the real branch names on this slide straight from our repository. Writing the sprint backlog out as one GitHub Issue per story is the piece we are still finishing.
'@
    HeaderBar $s 'Trello meets GitHub' 'How the Work Actually Flowed' | Out-Null
    FooterBar $s 5 | Out-Null
    $steps = @(
        @('BACKLOG', 'A user story exists as a Trello card with its acceptance criteria as a checklist and a P0 to P3 priority label.'),
        @('ISSUE', 'The card maps to exactly one GitHub Issue, so the story has a single home and a single tracking number.'),
        @('BRANCH', 'A branch is cut from develop, named for the story. Nothing is ever committed straight to develop or main.'),
        @('BUILD', 'The developer implements and tests the story on that branch against the acceptance criteria.'),
        @('PULL REQUEST', 'A PR is opened against develop and linked back to the issue it closes.'),
        @('REVIEW & MERGE', 'Another developer reviews the PR before it merges. The card then moves to done on the board.')
    )
    for ($k = 0; $k -lt 6; $k++) {
        $l = 46 + $k * 153; $t = 118; $w = 137; $h = 148
        Rect  $s ($l + 8) $t ($w - 17) 3.6 $GREEN | Out-Null
        Round $s $l ($t + 12) ($w - 17) $h $WHITE $BORDER | Out-Null
        Rect  $s $l ($t + 12) 3.6 $h $GREEN | Out-Null
        Add $s $steps[$k][0] ($l + 13) ($t + 26) ($w - 43) 24 8 $DGREEN $true $false 'l' $FONT 0.6 | Out-Null
        Add $s $steps[$k][1] ($l + 13) ($t + 56) ($w - 43) 96 6.3 $BODY | Out-Null
        if ($k -lt 5) { Add $s '>' ($l + $w - 15) ($t + 76) 16 20 11 $GREEN $true $false 'c' }
    }
    $bx = @(
        @('Trello board', 'One board, lists running Sprint 0 through Sprint 8, so the board itself shows the build order. Every story is a card: acceptance criteria as a checklist, priority as a P0 to P3 label.'),
        @('GitHub repository', 'One shared repo with main and develop. Every feature branches off develop, opens a pull request back into develop, and is reviewed by another developer before it merges.'),
        @('Engineering work is visible too', 'Technical setup tasks such as CI/CD, the database schema and seeded categories got their own cards, keeping product work clearly separate from groundwork.')
    )
    for ($k = 0; $k -lt 3; $k++) { Card $s (46 + $k * 307) 282 295 126 $bx[$k][0] @($bx[$k][1]) $GREEN 9.4 7 | Out-Null }
    Add $s 'Branch naming in practice, from our own repository:' 46 422 908 20 7.5 $NAVY $true | Out-Null
    Rect $s 46 444 908 62 $NAVY | Out-Null
    Add $s 'US17_create_an_expense_category' 62 454 880 20 8.4 $WHITE $false $false 'l' $MONO | Out-Null
    Add $s 'US09_manage_default_categories     US10_monitor_platform_health     US01_create_account     US07_admin_authentication' 62 476 880 20 8.4 $WHITE $false $false 'l' $MONO | Out-Null

    # ------------------------------------------------------------ 06 vision
    $s = NewSlide 6 $BG @'
Sprint 1 was not only code. The corrections we received on our first submission were folded back into the Product Vision document, and two things changed materially.

First, the vision statement itself. We had framed Expense Tracker as a planning product. The corrected vision centres it on recording income and expenses with minimal friction, and treats planning as something the user may optionally add. That reads as a small wording change but it reordered our whole backlog.

Second, three product principles became non-negotiable. Tracking is always on from account creation. Budgeting is a goal, not a gate, so it can never block a transaction. And overspending is a legitimate outcome of a goal you missed, not an error the system prevents.

Everything else follows from the order on the blue strip: track, optionally plan, monitor without blocking, then understand whether the user saved or fell into debt.
'@
    HeaderBar $s 'Sprint 1 also corrected our foundations' 'Product Vision, Updated' | Out-Null
    FooterBar $s 6 | Out-Null
    Card $s 46 118 454 122 'The vision statement' @('"To provide a simple and intelligent personal financial platform that helps users record their income and expenses with minimal friction, optionally plan and track spending goals, understand their financial activity, and improve their ability to save money."') $GREEN 10 7.3 | Out-Null
    Card $s 512 118 442 122 'What the review changed' @('We had stated the product as a planning tool. The correction re-centred it on tracking first, with budgeting as an optional layer the user can add whenever they choose.') $AMBER 10 7.3 | Out-Null
    $pr = @(
        @('Tracking is always on', 'Income and expense tracking works from account creation, with no setup barrier and no budget required first.'),
        @('Budgeting is a goal, not a gate', 'A budget is an optional goal layer, like a personal streak. It never blocks, pauses or gates recording income or expenses.'),
        @('Overspending is allowed', 'Once a budget is validated the system never prevents spending. Overspending surfaces as a negative balance.')
    )
    for ($k = 0; $k -lt 3; $k++) { Card $s (46 + $k * 307) 254 295 112 $pr[$k][0] @($pr[$k][1]) $GREEN 9.4 7.2 | Out-Null }
    Rect $s 46 380 908 33 $NAVY | Out-Null
    Add $s 'The financial cycle the vision now fixes:   TRACK  (always on)   >   PLAN  (optional)   >   MONITOR  (never blocks)   >   UNDERSTAND  (savings or debt)' 62 387 880 20 7.8 $WHITE $true | Out-Null
    $fx = @(
        @('Total income', 'sum of all valid income records in the period'),
        @('Savings', 'total income  -  total actual expenses'),
        @('Debt', 'total actual expenses  -  total income, carried into the next period')
    )
    for ($k = 0; $k -lt 3; $k++) {
        $l = 46 + $k * 307; $t = 424
        Round $s $l $t 295 78 $LNAVY | Out-Null
        Add $s $fx[$k][0] ($l + 14) ($t + 8) 265 22 8.6 $NAVY $true | Out-Null
        Add $s $fx[$k][1] ($l + 14) ($t + 32) 265 40 6.6 $BODY | Out-Null
    }
    Add $s 'Savings and debt are calculated from real income and real expenses, so they work whether or not the user ever sets a budget.' 46 510 908 16 6.6 $MUTED $false $true | Out-Null

    # -------------------------------------------------------------- 07 tech
    $s = NewSlide 7 $BG @'
Before either sprint could deliver a story, we needed a foundation, and most of that came out of Sprint 0.

On the left is our entity relationship model. It was designed up front because every story in both sprints sits on it: users and their account state, the category tables, and the income and expense records.

On the right are three screenshots of our live Swagger documentation. Having the API described before the screens were built meant the mobile side and the backend side could work in parallel instead of blocking each other, and it gave us a contract to test against.

The stack: Java and Spring Boot with PostgreSQL behind Hibernate on the backend, React Native with TypeScript on the client, and JUnit plus Testcontainers for tests.
'@
    HeaderBar $s 'What sits underneath both sprints' 'Technical Foundation' | Out-Null
    FooterBar $s 7 | Out-Null
    Round $s 46 116 420 338 $WHITE $BORDER | Out-Null
    Add $s 'Entity relationship model' 62 128 388 22 9.6 $NAVY $true | Out-Null
    Add $s 'Users, accounts, categories, income and expenses, designed before feature work started.' 62 152 388 18 6.6 $BODY | Out-Null
    Pic $s 'er' (Join-Path $UmlDir 'Expense_Tracker_ER_Diagram.png') 58 176 396 266 | Out-Null
    Add $s 'REST API surface, documented in Swagger' 494 116 460 22 9.6 $NAVY $true | Out-Null
    Add $s 'Every endpoint the mobile client calls is described and testable from the Swagger UI.' 494 140 460 18 6.6 $BODY | Out-Null
    $sw = @('SwaggerImage1.png', 'SwaggerImage2.png', 'SwaggerImage3.png')
    for ($k = 0; $k -lt 3; $k++) {
        $t = 164 + $k * 97
        Round $s 494 $t 460 89 $WHITE $BORDER | Out-Null
        Pic $s ('sw' + $k) (Join-Path $ImgDir $sw[$k]) 500 ($t + 5) 448 79 | Out-Null
    }
    $stk = @(
        @('Backend', 'Java 17, Spring Boot, PostgreSQL persistence through Hibernate ORM.'),
        @('Mobile', 'React Native and TypeScript, styled from a single tokenised design system.'),
        @('Testing', 'JUnit controller tests; Testcontainers for integration coverage.')
    )
    for ($k = 0; $k -lt 3; $k++) {
        $l = 46 + $k * 307; $t = 466
        Round $s $l $t 295 58 $LNAVY | Out-Null
        Add $s $stk[$k][0] ($l + 12) ($t + 7) 269 20 8.2 $NAVY $true | Out-Null
        Add $s $stk[$k][1] ($l + 12) ($t + 28) 269 28 6.2 $BODY | Out-Null
    }

    # ----------------------------------------------------------- 08 sprint 1
    $s = NewSlide 8 $BG @'
Sprint 1 ran the first three weeks and its theme was identity: proving who a user is, and proving what an administrator is not allowed to do.

The Sprint Goal was that a user can create an account and securely access the application, while the platform can be administered separately from user accounts.

It carried eleven stories. On the left, the account and session work - creating an account, logging in and out, keeping the session secure, editing a profile and recovering a forgotten password. In the middle, the administration stories - a separate admin login, managing user account status, managing the default expense categories that new users start with, and monitoring platform health.

The story on the right is the one I would single out. US11 is not a feature, it is a constraint: an administrator must be technically prevented from reaching any user's financial data. We treated it as an architectural requirement from the first day of the sprint, because a privacy rule that only exists in the user interface is not a privacy rule.
'@
    HeaderBar $s 'Sprint 1  |  weeks 1 to 3' 'Sprint 1: Account, Session & Admin Foundation' | Out-Null
    FooterBar $s 8 | Out-Null
    Rect $s 46 116 908 62 $NAVY | Out-Null
    Add $s 'Sprint Goal: a user can create an account and securely access the application, and the platform can be administered separately from user accounts.' 62 126 880 44 9.8 $WHITE $true | Out-Null
    Card $s 46 192 295 246 'Account & session' @('US01  Create a personal account', 'US02  Log in with credentials', 'US03  Log out securely', 'US04  Secure session handling', 'US05  View and edit profile', 'US06  Reset a forgotten password') $GREEN 10 8 | Out-Null
    Card $s 353 192 295 246 'Administration' @('US07  Separate admin authentication', 'US08  Manage user account status', 'US09  Manage default categories', 'US10  Monitor platform health and errors') $NAVY 10 8 | Out-Null
    Card $s 660 192 294 246 'The hard rule' @("US11  An administrator must be technically prevented from reaching any user's income, expenses, budgets, savings, debt or reports.", '', 'Enforced at the access control layer of the API, not merely hidden in the interface.') $DANGER 10 8 | Out-Null
    $s1 = @(
        @('3 weeks', 'sprint length'),
        @('11', 'user stories US01 to US11'),
        @('P0 and P1', 'priority mix'),
        @('6 + 5', 'account stories + admin stories')
    )
    for ($k = 0; $k -lt 4; $k++) { Stat $s (46 + $k * 231) 450 215 70 $s1[$k][0] $s1[$k][1] }

    # -------------------------------------------------------- 09 sprint 1 UI
    $s = NewSlide 9 $BG @'
These are the Sprint 1 screens, taken from our Figma file, and they are all on the administrative and account side of the product.

Across the top are the four admin console screens: Admin Home with live platform status and error counts, Admin Analytics, Admin Users for account status management, and Admin Categories where the default category set is maintained.

Notice what is absent. There is no screen, and no route, that lets an administrator look at a person's income, expenses or budget. That is US11 made visible in the design rather than only asserted in a document.

On the right is the sign-up screen. Validation happens before submission - the password checklist is live, and a duplicate email or phone number is refused. That maps directly onto the acceptance criteria for US01.
'@
    HeaderBar $s 'Sprint 1  |  the screens' 'Admin Console & Account Creation' | Out-Null
    FooterBar $s 9 | Out-Null
    $dash = [char]0x2014
    $shots = @(
        @("ADMIN-01 $dash Admin Home.png", 'Admin Home', 'Platform status, live health and error counts, with no route into user finances.'),
        @("ADMIN-02 $dash Admin Analytics.png", 'Admin Analytics', 'Technical and platform-level metrics only.'),
        @("ADMIN-03 $dash Admin Users.png", 'Admin Users', 'Account status management: activate, suspend, review.'),
        @("ADMIN-05 $dash Admin Categories.png", 'Admin Categories', 'The default category set new accounts are seeded from.')
    )
    for ($k = 0; $k -lt 4; $k++) {
        $l = 46 + $k * 126; $t = 118; $w = 112
        Round $s $l $t $w 272 $WHITE $BORDER | Out-Null
        Pic $s ('sh' + $k) (Join-Path $FigmaDir $shots[$k][0]) ($l + 7) ($t + 7) ($w - 14) 258 | Out-Null
        Add $s $shots[$k][1] $l 396 $w 18 7.4 $NAVY $true $false 'c' | Out-Null
        Add $s $shots[$k][2] $l 414 $w 54 5.8 $BODY $false $false 'c' | Out-Null
    }
    Round $s 576 118 378 272 $WHITE $BORDER | Out-Null
    Add $s 'Sign-up and secure account creation' 592 128 346 22 9.4 $NAVY $true | Out-Null
    Pic $s 'signup' (Join-Path $FigmaDir 'SignUpImage.png') 596 152 112 228 | Out-Null
    Bullets $s @(
        'Required-field validation with a live password checklist, so a user cannot submit an account that will fail to be created.',
        'Registration links straight into the backend: a created account can immediately proceed to log in.',
        'An existing identifier such as an email or phone number is rejected rather than silently duplicated.',
        'The sign-up flow and the admin console are visually distinct, because they are two different entry points into two different permission levels.'
    ) 722 154 218 228 6.4 $BODY $FONT 4 | Out-Null
    Add $s 'Security measures carried by this sprint: hashed credentials, token expiry and invalidation on logout, and admin role separation enforced at the API layer rather than in the UI.' 46 440 908 16 6.6 $MUTED $false $true | Out-Null

    # ----------------------------------------------------------- 10 sprint 2
    $s = NewSlide 10 $BG @'
Sprint 2 covered the second three weeks and is the sprint where the product became actually usable.

The Sprint Goal was that a user can track income and expenses from day one with no budget required first. We deliberately wrote fifteen stories with zero dependency on budgeting, because that is the corrected product principle in code rather than in prose.

Three families of stories. Income, US12 to US16: recording an income with an amount, source and date, then viewing, editing, deleting and totalling it. Categories, US17 to US19: creating a category, editing it, deleting an unused one, with new accounts seeded from the default set the admin maintains. And expenses, US20 to US26: recording an expense manually, then the whole history journey - viewing, opening details, editing, deleting, filtering by category and date, and searching for a specific transaction.

The zero on the right-hand stat is the point of the sprint. A user could complete every one of these fifteen stories without ever creating a budget.
'@
    HeaderBar $s 'Sprint 2  |  weeks 4 to 6' 'Sprint 2: Standalone Expense & Income Tracking' | Out-Null
    FooterBar $s 10 | Out-Null
    Rect $s 46 116 908 62 $NAVY | Out-Null
    Add $s 'Sprint Goal: a user can track income and expenses from day one, with no budget required first. The product becomes genuinely usable at the end of this sprint.' 62 126 880 44 9.8 $WHITE $true | Out-Null
    Card $s 46 192 295 246 'Income  |  US12 to US16' @('US12  Add an income record: amount, source, date', 'US13  View income records', 'US14  Edit an income record', 'US15  Delete an incorrect income entry', 'US16  Calculate total income for the period') $GREEN 9.6 8 | Out-Null
    Card $s 353 192 295 246 'Categories  |  US17 to US19' @('US17  Create an expense category', 'US18  Edit a category', 'US19  Delete an unused category', '', 'Categories are created without a budget existing, and new accounts are seeded with a sensible default set.') $NAVY 9.6 8 | Out-Null
    Card $s 660 192 294 246 'Expenses  |  US20 to US26' @('US20  Record an expense manually', 'US21  View expense history', 'US22  View expense details', 'US23  Edit an expense', 'US24  Delete an expense', 'US25  Filter by category and date', 'US26  Search expenses') $DGREEN 9.6 8 | Out-Null
    $s2 = @(
        @('3 weeks', 'sprint length'),
        @('15', 'user stories US12 to US26'),
        @('15 of 15', 'tracking stories'),
        @('0', 'budgets required to use it')
    )
    for ($k = 0; $k -lt 4; $k++) { Stat $s (46 + $k * 231) 450 215 70 $s2[$k][0] $s2[$k][1] }
    Add $s 'US20 records an expense whether or not a budget exists, and never blocks it because of budget status. US17 creates a category with no budget present.' 46 526 908 16 6.6 $MUTED $false $true | Out-Null

    # -------------------------------------------------------- 11 sprint 2 UI
    $s = NewSlide 11 $BG @'
This slide pairs the tracking screens with the API they were built against.

On the left is our general Figma design file, which holds the wired flows for the tracking journey: recording an expense, browsing the history, filtering, and the income screens alongside them.

On the right, the same Swagger documentation as before, now showing the income, expense and category endpoints that Sprint 2's screens consume. Building the screens against a documented contract is what let us see the endpoints rendered correctly in the API explorer before the mobile work was finished.

At the bottom, the technical work behind the stories that never appears as a card the user sees: default categories seeded onto every new account, and the CRUD endpoints that back the tracking screens.
'@
    HeaderBar $s 'Sprint 2  |  the screens' 'Tracking Screens & API' | Out-Null
    FooterBar $s 11 | Out-Null
    Round $s 46 116 420 350 $WHITE $BORDER | Out-Null
    Add $s 'Wired flows across both sprints' 62 128 388 22 9.6 $NAVY $true | Out-Null
    Pic $s 'figma' (Join-Path $ImgDir 'GeneralFigmaDesign.png') 78 156 356 300 | Out-Null
    Add $s 'Expense and income endpoints in Swagger' 494 116 460 22 9.6 $NAVY $true | Out-Null
    Add $s 'The mobile tracking screens are built against these documented, testable endpoints.' 494 140 460 18 6.6 $BODY | Out-Null
    $sw2 = @('SwaggerImage1.png', 'SwaggerImage2.png', 'SwaggerImage3.png')
    for ($k = 0; $k -lt 3; $k++) {
        $t = 164 + $k * 97
        Round $s 494 $t 460 89 $WHITE $BORDER | Out-Null
        Pic $s ('sw2-' + $k) (Join-Path $ImgDir $sw2[$k]) 500 ($t + 5) 448 79 | Out-Null
    }
    Add $s 'Technical work that sat behind the stories' 494 462 460 20 8.4 $NAVY $true | Out-Null
    Add $s 'Default categories pre-seeded on account creation, and expense and income CRUD endpoints wired to their screens.' 494 484 460 36 6.4 $BODY | Out-Null

    # ---------------------------------------------------------- 12 outcomes
    $s = NewSlide 12 $BG @'
Let us be straight about where the work actually stands, because a sprint review is worth nothing if it overstates.

Sprint 1 closed in full. All eleven stories were done: account creation, login, logout, secure sessions, profile management, password reset, separate admin authentication, user account status management, default category management and platform health monitoring. US11, the privacy constraint, was honoured by never exposing a route from the admin area to user financial data.

Sprint 2, the second three weeks, delivered its foundation. US17 landed - users can create their own expense categories - and the technical work went in behind it: default categories seeded onto every new account, and the income and expense CRUD endpoints wired through to the screens. The admin dashboard and the sign-up flow are now linked end to end.

What remains open is the rest of the expense and income journey: US12 to US16 on income management, and US18 to US26 on the expense history. That is the honest picture, and those stories are the first commitment we take into Sprint 3.
'@
    HeaderBar $s 'Where both sprints landed' 'Sprint Outcomes' | Out-Null
    FooterBar $s 12 | Out-Null
    $oc = @(
        @('Sprint 1  |  closed', @('US01 to US11 closed, all eleven stories.', 'Account creation, login, logout and secure sessions.', 'Profile management and password reset.', 'Separate admin authentication.', 'User account status and default category management.', 'Platform health and error monitoring.', 'US11: no admin route to user financial data.'), $GREEN),
        @('Sprint 2  |  delivered', @('US17 closed: users create their own expense categories.', 'Default categories seeded onto every new account.', 'Income and expense CRUD endpoints delivered.', 'Expense and income screens linked to the API.', 'Admin dashboard and sign-up flow linked end to end.'), $DGREEN),
        @('Sprint 2  |  in progress', @('US12 to US16 income management is in progress.', 'US18 to US26 on the expense history journey are in progress.', 'Filtering, searching, editing and deleting expenses are next.', 'These carry into Sprint 3 as the first commitment.'), $AMBER),
        @('Carried into Sprint 3', @('Close out the remaining Sprint 2 tracking stories.', 'Then open the optional budgeting layer:', 'US27 to US33, budgeting as a goal layer.', 'A financial period of at least one week.', 'Overall budget, category allocations and validation.'), $NAVY)
    )
    for ($k = 0; $k -lt 4; $k++) {
        $col = $k % 2; $row = [math]::Floor($k / 2)
        Card $s (46 + $col * 462) (116 + $row * 198) 446 184 $oc[$k][0] $oc[$k][1] $oc[$k][2] 10 7.2 | Out-Null
    }
    Add $s 'Shown honestly: Sprint 1 closed in full, and Sprint 2 delivered its category and API foundation with the remaining tracking stories still open.' 46 516 908 16 6.6 $MUTED $false $true | Out-Null

    # ---------------------------------------------------------- 13 evidence
    $s = NewSlide 13 $BG @'
None of this needs to be taken on trust, so here is where to check it.

The repository holds twenty-nine commits and five pull requests merged into develop. The most recent five got there through the exact branch-and-review flow we described: US09 for default categories, US10 for platform health, the two Sprint 2 technical branches for seeded categories and the CRUD endpoints, and US17 for user-created categories.

On the right is the supporting evidence: six controller test classes covering admin categories, user category provisioning, expenses, income and monitoring, plus Swagger configuration, seed data and consistent exception handling.

One thing I want to flag honestly: writing the backlog out as one GitHub Issue per story is not finished. That is the one part of the plan that is more intention than execution today, and it is what we will close out next.
'@
    HeaderBar $s 'Verifiable in the repository' 'Evidence from Our Own Tooling' | Out-Null
    FooterBar $s 13 | Out-Null
    $ev = @(
        @('29', 'commits across the repository'),
        @('5', 'pull requests merged to develop'),
        @('19 Sep - 9 Oct', 'from first commit to latest merge'),
        @('29', 'Java source files in the backend')
    )
    for ($k = 0; $k -lt 4; $k++) { StatNavy $s (46 + $k * 231) 116 215 78 $ev[$k][0] $ev[$k][1] }
    Round $s 46 210 566 246 $WHITE $BORDER | Out-Null
    Add $s 'Work merged into develop, in order' 62 222 534 22 9.4 $NAVY $true | Out-Null
    $ml = @(
        'US09  add admin management of default expense categories',
        'US10  add admin platform health and error log monitoring',
        'S2-TECH-1  give new accounts their own copy of the default categories',
        'S2-TECH-2  expense and income CRUD endpoints and screens',
        'US17  let users create their own expense categories',
        'FEATURE  login and user type redirection',
        'FEATURE  sign-up page linked to the corresponding backend'
    )
    for ($k = 0; $k -lt 7; $k++) {
        $t = 252 + $k * 28
        Ellipse $s 64 ($t + 3) 17 17 $LGREEN | Out-Null
        Add $s ([char]0x2713) 64 ($t + 4) 17 14 6.6 $DGREEN $true $false 'c' | Out-Null
        Add $s $ml[$k] 90 ($t + 1) 508 20 6.8 $BODY $false $false 'l' $MONO | Out-Null
    }
    Round $s 628 210 326 246 $WHITE $BORDER | Out-Null
    Add $s 'Also in the repository' 644 222 294 22 9.4 $NAVY $true | Out-Null
    Bullets $s @(
        '6 controller test classes covering admin categories, user category provisioning, expenses, income and monitoring',
        'Swagger configuration documenting every public endpoint',
        'Seed data provisioning default categories and user accounts',
        'Global exception handling with a consistent error shape',
        'Architecture documents, UML diagrams and the ER model'
    ) 644 252 294 196 6.4 $BODY $FONT 4 | Out-Null
    Add $s 'Every item above can be checked directly in the shared repository: the same branches, pull requests and tests the workflow on slide 5 describes.' 46 472 908 16 6.6 $MUTED $false $true | Out-Null

    # --------------------------------------------------------------- 14 DoD
    $s = NewSlide 14 $BG @'
Two slides to go, and this one is about how we decided whether work was finished.

Our Definition of Done had seven conditions, and a story had to satisfy all of them. It had to be implemented as written, with every acceptance criterion verified. Validation and error handling had to be present, tests had to be green, and another developer had to review the pull request before it merged. Nothing knowingly broken could be left in, and finally the Product Owner checked the result against the criteria and accepted it.

The reason this matters to a review is that it is why we can say Sprint 1 closed in full. We were not counting stories that were nearly there. And it is also why Sprint 2 is reported as partly delivered rather than complete - the same bar applies to us when the news is less good.
'@
    HeaderBar $s 'The bar we held ourselves to' 'Definition of Done' | Out-Null
    FooterBar $s 14 | Out-Null
    Add $s 'A story was only counted as done when every one of these held. It is what let us claim Sprint 1 as closed rather than nearly closed.' 46 113 908 22 8.4 $BODY | Out-Null
    $dod = @(
        @('1', 'Implemented as written', 'The functionality exists and matches the story, with no half-built screens.'),
        @('2', 'Acceptance criteria pass', 'Every Given / When / Then criterion is verified, not assumed.'),
        @('3', 'Validation and error handling', 'Required fields, invalid values and failures are handled with a clear message.'),
        @('4', 'Tests pass', 'Relevant unit, integration and acceptance tests are green before review.'),
        @('5', 'Reviewed by someone else', 'At least one other developer reviews the pull request before it merges.'),
        @('6', 'No critical defect', 'Nothing known is blocking normal use of the feature.'),
        @('7', 'Product Owner accepts', 'The PO checks the result against the acceptance criteria and accepts or rejects it.')
    )
    for ($k = 0; $k -lt 7; $k++) {
        if ($k -lt 4) { $l = 46 + $k * 231; $t = 146 } else { $l = 46 + ($k - 4) * 231; $t = 318 }
        $w = 215
        Round $s $l $t $w 158 $WHITE $BORDER | Out-Null
        Rect  $s $l $t $w 4.2 $GREEN | Out-Null
        Ellipse $s ($l + 15) ($t + 18) 28 28 $LGREEN | Out-Null
        Add $s $dod[$k][0] ($l + 15) ($t + 22) 28 20 9.4 $DGREEN $true $false 'c' | Out-Null
        Add $s $dod[$k][1] ($l + 15) ($t + 56) ($w - 30) 36 8.2 $NAVY $true | Out-Null
        Add $s $dod[$k][2] ($l + 15) ($t + 100) ($w - 30) 50 6.3 $BODY | Out-Null
    }

    # --------------------------------------------------------- 15 next steps
    $s = NewSlide 15 $BG @'
To close, the immediate plan and what the two sprints taught us.

Sprint 3 has two commitments. First, close out the remaining Sprint 2 tracking stories so the tracking layer is complete. Second, and only then, open the budgeting layer as a goal rather than a gate: US27 to US33, covering a financial period of at least one week that the user defines, an overall budget, allocations to categories, validation that warns without blocking, and the edit window that locks the budget after the first six percent of the period or a small number of edits.

The retrospective is on the bottom row. We would keep the ten-minute stand-up exactly as it was - it was short, fixed, and never grew into a design meeting - and we would keep the rule that nothing reaches develop without a review.

We would change two things. The backlog needs to become one GitHub Issue per story so traceability is automatic, and we need to move cards on the Trello board daily rather than letting the board drift from what is actually merged.

And we are watching two risks: Sprint 4 bundles receipt OCR and mobile money parsing into one sprint, and the AI assistant in Sprint 7 is the first thing we would drop if the calendar tightens.

Thank you - we are happy to take questions.
'@
    HeaderBar $s 'What happens next' 'Next Steps & Retrospective' | Out-Null
    FooterBar $s 15 | Out-Null
    Add $s 'Sprint 3 opens the optional budgeting layer, built on the tracking foundation that already works.' 46 113 908 22 8.8 $BODY $true | Out-Null
    Card $s 46 146 446 140 'Close out Sprint 2' @('US12 to US16 and US18 to US26: the remaining income and expense tracking stories are the first commitment of Sprint 3.') $GREEN 10 7.4 | Out-Null
    Card $s 512 146 442 140 'Sprint 3: budgeting as a goal layer' @('US27 to US33. A user-defined financial period of at least one week, an overall budget, category allocations, validation that warns without blocking, and the edit window that locks the budget.') $GREEN 10 7.4 | Out-Null
    Rect $s 46 298 908 26 $NAVY | Out-Null
    Add $s 'RETROSPECTIVE  |  what we would keep and what we would change' 62 303 880 18 7.6 $WHITE $true $false 'l' $FONT 0.8 | Out-Null
    $rt = @(
        @('Keep', @('The ten-minute stand-up at 10:00 every school day. Short, fixed and never allowed to expand.', 'Branch off develop and review before merge. It kept a shared repository clean with five people in it.'), $GREEN),
        @('Change', @('Convert the sprint backlog into one GitHub Issue per story, so every branch and pull request traces to a story automatically.', 'Report story status on the Trello board daily rather than at the end of the sprint, so the board never drifts from reality.'), $AMBER),
        @('Watch', @('Sprint 4 combines receipt capture and mobile money parsing, two different technical problems in one sprint.', 'Sprint 7, the AI assistant, is the sprint to trim first if the calendar tightens.'), $DGREEN)
    )
    for ($k = 0; $k -lt 3; $k++) { Card $s (46 + $k * 307) 336 295 152 $rt[$k][0] $rt[$k][1] $rt[$k][2] 9.6 6.8 | Out-Null }

    # ------------------------------------------------------------ 16 closing
    $s = NewSlide 16 $BLACK @'
Thank you. To summarise in two sentences: we completed two three-week sprints with a ten-minute stand-up every school day at ten o'clock, and we ran the work through a Trello board and a GitHub repository where nothing reached develop without a review.

Sprint 1 closed in full on the account, session and admin foundation. Sprint 2 delivered the standalone tracking foundation - user-created categories, seeded defaults and the income and expense API wired to its screens - with the remaining expense and income stories continuing into Sprint 3.

Happy to take questions on the workflow, the stories, or the decisions behind them.
'@
    Rect $s 0 0 1000 562.5 $BLACK | Out-Null
    Rect $s 0 0 12 562.5 $GREEN | Out-Null
    Add $s 'THANK YOU' 90 148 820 20 8.4 $GREEN $true $false 'c' $FONT 2 | Out-Null
    Add $s 'Questions & Discussion' 90 180 820 58 25 $WHITE $true $false 'c' | Out-Null
    Rect $s 461 254 78 3 $GREEN | Out-Null
    Add $s 'Two three-week sprints, a daily stand-up every school day at 10:00, and a Trello and GitHub workflow that got real work merged into develop.' 150 274 700 28 8.6 $MUTED $false $false 'c' | Out-Null
    Add $s 'Sprint 1 closed in full; Sprint 2 delivered the tracking foundation and continues into Sprint 3.' 150 302 700 28 8.6 $MUTED $false $false 'c' | Out-Null
    Pic $s 'logo-close' (Join-Path $ImgDir 'logo.jpeg') 414 360 172 172 | Out-Null

    # ------------------------------------------------------------------ save
    if (Test-Path -LiteralPath $Temp) { Remove-Item -LiteralPath $Temp -Force }
    $pres.SaveAs($Temp, 24)
    Write-Output "SLIDES : $($pres.Slides.Count)"
    Write-Output "PICS   : $($script:Pics.Count)"
}
catch {
    Write-Output "ERROR  : $($_.Exception.Message)"
    Write-Output $_.ScriptStackTrace
    throw
}
finally {
    if ($pres) { try { $pres.Close() } catch {} }
    if ($ppt)  { try { $ppt.Quit() } catch {} }
    [GC]::Collect(); [GC]::WaitForPendingFinalizers()
}

# ===================================================== inject pictures (OOXML)
# Set FINFLOW_KEEP_TEMP=1 to keep the raw PowerPoint output and the extracted
# package for diagnosis instead of deleting them.
$KeepTemp = ($env:FINFLOW_KEEP_TEMP -eq '1')

function Esc([string]$t) {
    if ($null -eq $t) { return '' }
    $t.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;').Replace('"', '&quot;')
}
$EMU = 12192000.0 / 1000.0
function Emu([double]$u) { [string][long][math]::Round($u * $EMU) }

if (Test-Path -LiteralPath $Extract) { Remove-Item -LiteralPath $Extract -Recurse -Force }
[System.IO.Compression.ZipFile]::ExtractToDirectory($Temp, $Extract)

$mediaDir = Join-Path $Extract 'ppt\media'
if (-not (Test-Path -LiteralPath $mediaDir)) { $null = New-Item -ItemType Directory -Path $mediaDir -Force }

# Copy each distinct source image into the package once, even when it is used on
# several slides: media part names must be unique, and reusing one part is valid.
$bySource = @{}
$existing = @(Get-ChildItem $mediaDir -File -ErrorAction SilentlyContinue |
              Where-Object { $_.Name -match '^image(\d+)\.' } |
              ForEach-Object { [int]([regex]::Match($_.Name, '^image(\d+)\.').Groups[1].Value) })
$nextImg = if ($existing.Count) { ($existing | Measure-Object -Maximum).Maximum + 1 } else { 1 }
foreach ($p in $script:Pics) {
    if (-not $bySource.ContainsKey($p.Path)) {
        $ext = [System.IO.Path]::GetExtension($p.Path).TrimStart('.').ToLower()
        $name = 'image' + $nextImg + '.' + $ext
        $nextImg++
        Copy-Item -LiteralPath $p.Path -Destination (Join-Path $mediaDir $name) -Force
        $bySource[$p.Path] = $name
    }
}

$slideDir = Join-Path $Extract 'ppt\slides'
foreach ($grp in ($script:Pics | Group-Object Slide)) {
    $n = [int]$grp.Name
    $slidePath = Join-Path $slideDir ("slide$n.xml")
    $relsPath  = Join-Path $slideDir ("_rels\slide$n.xml.rels")
    $xml  = [System.IO.File]::ReadAllText($slidePath)
    $rels = [System.IO.File]::ReadAllText($relsPath)

    $used = @([regex]::Matches($rels, 'Id="rId(\d+)"') | ForEach-Object { [int]$_.Groups[1].Value })
    $nextRid = if ($used.Count) { ($used | Measure-Object -Maximum).Maximum + 1 } else { 1 }

    $extraRels = ''
    $extraSp   = ''
    foreach ($p in $grp.Group) {
        $rid = 'rId' + $nextRid
        $nextRid++
        $file = $bySource[$p.Path]
        $extraRels += '<Relationship Id="' + $rid + '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/' + $file + '"/>'
        $extraSp += '<p:pic><p:nvPicPr><p:cNvPr id="900" name="' + (Esc $p.Name) + '"/>' +
                    '<p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>' +
                    '<p:blipFill><a:blip r:embed="' + $rid + '"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>' +
                    '<p:spPr><a:xfrm><a:off x="' + (Emu $p.L) + '" y="' + (Emu $p.T) + '"/>' +
                    '<a:ext cx="' + (Emu $p.W) + '" cy="' + (Emu $p.H) + '"/></a:xfrm>' +
                    '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>'
    }
    $rels = $rels.Replace('</Relationships>', $extraRels + '</Relationships>')
    # Insert before the FIRST </p:spTree>. A replace-all would also match the
    # closing tag inside p:extLst further down, which places the picture outside
    # p:cSld and makes PowerPoint reject the file.
    $at = $xml.IndexOf('</p:spTree>')
    if ($at -lt 0) { throw "slide $n : no <p:spTree> to insert into" }
    $xml = $xml.Substring(0, $at) + $extraSp + $xml.Substring($at)
    [System.IO.File]::WriteAllText($relsPath, $rels, (New-Object System.Text.UTF8Encoding($false)))
    [System.IO.File]::WriteAllText($slidePath, $xml, (New-Object System.Text.UTF8Encoding($false)))
}

# register any new image extensions in [Content_Types].xml
$ctPath = Join-Path $Extract '[Content_Types].xml'
$ct = [System.IO.File]::ReadAllText($ctPath)
foreach ($f in Get-ChildItem $mediaDir -File) {
    $ext = $f.Extension.TrimStart('.').ToLower()
    if ($ext -eq 'jpg') { $ext = 'jpeg' }
    $mime = switch ($ext) { 'png' { 'image/png' } 'jpeg' { 'image/jpeg' } 'gif' { 'image/gif' } default { 'image/' + $ext } }
    if ($ct -notmatch ('Extension="' + $ext + '"')) {
        $ct = $ct.Replace('<Default Extension="rels"', '<Default Extension="' + $ext + '" ContentType="' + $mime + '"/><Default Extension="rels"')
    }
}
[System.IO.File]::WriteAllText($ctPath, $ct, (New-Object System.Text.UTF8Encoding($false)))

# repack: create every entry explicitly so separators are always forward slashes
$ms = New-Object System.IO.MemoryStream
$zip = New-Object System.IO.Compression.ZipArchive($ms, ([System.IO.Compression.ZipArchiveMode]::Create), $true)
$extractFull = (Resolve-Path $Extract).Path.TrimEnd('\')
foreach ($f in Get-ChildItem $Extract -Recurse -File) {
    $rel = $f.FullName.Substring($extractFull.Length + 1).Replace('\', '/')
    $entry = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
    $st = $entry.Open()
    $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
    $st.Write($bytes, 0, $bytes.Length)
    $st.Dispose()
}
$zip.Dispose()
$outBytes = $ms.ToArray()
$ms.Dispose()

# publish, tolerating a transient handle from a scanner or a previous build
$dir = Split-Path -Parent $Final
if (-not (Test-Path -LiteralPath $dir)) { $null = New-Item -ItemType Directory -Path $dir -Force }
$published = $false
for ($i = 1; $i -le 12; $i++) {
    try {
        $fs = New-Object System.IO.FileStream($Final, ([System.IO.FileMode]::Create), ([System.IO.FileAccess]::Write), ([System.IO.FileShare]::None))
        $fs.Write($outBytes, 0, $outBytes.Length)
        $fs.Flush(); $fs.Dispose()
        $published = $true; break
    } catch {
        Write-Output ("retry {0}/12: {1}" -f $i, $_.Exception.Message)
        Start-Sleep -Milliseconds 600
    }
}
if (-not $published) { throw "could not write $Final after 12 attempts" }

if (-not $KeepTemp) {
    Remove-Item -LiteralPath $Temp -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $Extract -Recurse -Force -ErrorAction SilentlyContinue
} else {
    Write-Output ("KEPT   : {0}" -f $Temp)
    Write-Output ("KEPT   : {0}" -f $Extract)
}

Write-Output ("WROTE  : {0}" -f $Final)
Write-Output ("BYTES  : {0:N0}" -f (Get-Item -LiteralPath $Final).Length)
