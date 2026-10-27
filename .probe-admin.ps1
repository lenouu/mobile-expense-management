$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'Continue'

function Invoke-Api {
  param([string]$Method, [string]$Path, $Body, [string]$Token)
  $headers = @{}
  if ($Token) { $headers['Authorization'] = "Bearer $Token" }
  $args = @{ Uri = "http://localhost:8083$Path"; Method = $Method; Headers = $headers; TimeoutSec = 20; UseBasicParsing = $true }
  if ($Body) {
    $args['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress))
    $args['ContentType'] = 'application/json; charset=utf-8'
  }
  try {
    $r = Invoke-WebRequest @args
    return @{ ok = $true; status = $r.StatusCode; body = $r.Content }
  } catch {
    $resp = $_.Exception.Response
    $text = ''
    if ($resp) { $sr = New-Object System.IO.StreamReader($resp.GetResponseStream()); $text = $sr.ReadToEnd() }
    return @{ ok = $false; status = $(if ($resp) { [int]$resp.StatusCode } else { 0 }); body = $text }
  }
}

# --- token ---
$login = Invoke-Api 'POST' '/api/auth/login' @{ usernameOrEmail = 'admin.amina'; password = 'Password123!' }
$token = ($login.body | ConvertFrom-Json).accessToken
"token acquired: $($token.Substring(0,20))..."

function Probe($label, $method, $path, $body) {
  $res = Invoke-Api $method $path $body $token
  "`n### $label"
  "    $method $path  ->  HTTP $($res.status)"
  if (-not $res.ok) { "    ERROR BODY: $($res.body)"; return $null }
  $json = $res.body | ConvertFrom-Json
  if ($json.PSObject.Properties.Name -contains 'content') {
    "    PagedModel: content[$($json.content.Count)]  page=$($json.page.number) size=$($json.page.size) totalElements=$($json.page.totalElements) totalPages=$($json.page.totalPages)"
    if ($json.content.Count -gt 0) { "    first item: " + ($json.content[0] | ConvertTo-Json -Compress) }
  } else {
    "    " + ($res.body.Substring(0, [Math]::Min(600, $res.body.Length)))
  }
  return $json
}

"`n=============== MONITORING ==============="
$health = Probe 'health' 'GET' '/api/admin/health' $null
if ($health) { "    keys: " + (($health.PSObject.Properties.Name) -join ', '); "    errorsLast24Hours = $($health.errorsLast24Hours)" }

$errors = Probe 'error log, page 0 size 100' 'GET' '/api/admin/logs/errors?page=0&size=100' $null
Probe 'error log with from filter' 'GET' ("/api/admin/logs/errors?from=" + [uri]::EscapeDataString((Get-Date).AddDays(-7).ToUniversalTime().ToString('o')) + "&page=0&size=5") $null
Probe 'error log, bad from' 'GET' '/api/admin/logs/errors?from=notadate' $null

"`n=============== USERS ==============="
$users = Probe 'all users page 0 size 3' 'GET' '/api/admin/users?page=0&size=3&sort=userName,asc' $null
foreach ($st in 'ACTIVE','PENDING','SUSPENDED','DELETED') {
  $r = Invoke-Api 'GET' "/api/admin/users?status=$st&page=0&size=1" $null $token
  $j = $r.body | ConvertFrom-Json
  "    status=$st -> totalElements=$($j.page.totalElements)  (HTTP $($r.status))"
}
Probe 'search by name' 'GET' '/api/admin/users/search?name=a&page=0&size=2' $null
if ($users -and $users.content.Count -gt 0) {
  $id = $users.content[0].id
  Probe "user by id ($id)" 'GET' "/api/admin/users/$id" $null
  Probe 'user by username' 'GET' '/api/admin/users/by-username/admin.amina' $null
} else {
  Probe 'user by username' 'GET' '/api/admin/users/by-username/admin.amina' $null
}

"`n=============== CATEGORIES ==============="
Probe 'all categories' 'GET' '/api/admin/categories?page=0&size=50&sort=name,asc' $null
Probe 'active only' 'GET' '/api/admin/categories?active=true&page=0&size=50' $null
Probe 'inactive only' 'GET' '/api/admin/categories?active=false&page=0&size=50' $null
Probe 'category by id 1' 'GET' '/api/admin/categories/1' $null

"`n=============== AUTHORIZATION ==============="
$userLogin = Invoke-Api 'POST' '/api/auth/login' @{ usernameOrEmail = 'sara.mansouri'; password = 'Password123!' }
$userToken = ($userLogin.body | ConvertFrom-Json).accessToken
$asUser = Invoke-Api 'GET' '/api/admin/users' $null $userToken
"    USER token on /api/admin/users -> HTTP $($asUser.status)  $($asUser.body)"
$noToken = Invoke-Api 'GET' '/api/admin/users' $null $null
"    no token on /api/admin/users -> HTTP $($noToken.status)"
