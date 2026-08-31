<#
.SYNOPSIS
    Seeds the reference and test data the platform needs, idempotently.

.DESCRIPTION
    Most flows in this platform cannot run against an empty database. A payment needs ledger
    accounts to post against; a debit needs an account with an overdraft; a bill payment needs a
    registered biller. Recreating those by hand every time is the thing this script removes.

    Everything it creates is deterministic and stable, so reports and comparisons across runs mean
    something. Re-running is safe: creation endpoints return 409 for an existing code, which this
    script treats as "already there" rather than an error.

    The data lives in PostgreSQL, which docker compose keeps in the named volume `pgdata`. It
    therefore survives `docker compose down` and `docker compose restart`. It does NOT survive
    `docker compose down -v`, which deletes the volume.

.PARAMETER BaseUrl
    Where to send requests. Default http://localhost:8080 (the gateway). Point it at a service port
    directly if the gateway is not running.

.PARAMETER Direct
    Address each service on its own port instead of going through the gateway. Use this when
    debugging a single service without the gateway up.

.PARAMETER AccessToken
    Bearer token for edge authentication. Only needed when CBP_GATEWAY_SECURITY_ENABLED=true.

.PARAMETER EnvFile
    The .env to read service-auth settings from. Defaults to .env at the repository root. When
    service auth is on, the script mints its own short-lived service tokens from the secret, so it
    can call ledger-service and risk-aml-service directly.

.PARAMETER Verify
    Do not write anything; just report what already exists.

.EXAMPLE
    .\scripts\Seed-TestData.ps1
    Seeds through the gateway.

.EXAMPLE
    .\scripts\Seed-TestData.ps1 -Direct
    Seeds by calling each service on its own port.

.EXAMPLE
    .\scripts\Seed-TestData.ps1 -Verify
    Reports what is present without changing anything.
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = 'http://localhost:8080',
    [switch] $Direct,
    [string] $AccessToken,
    [string] $EnvFile,
    [switch] $Verify
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path (Split-Path -Parent $PSCommandPath) '..')).Path
if (-not $EnvFile) { $EnvFile = Join-Path $repoRoot '.env' }

# --------------------------------------------------------------------------------------------
# Service auth: mint our own tokens so the script can call guarded services directly
# --------------------------------------------------------------------------------------------

$script:AuthEnabled = $false
$script:AuthSecret = ''
$script:AuthIssuer = 'core-banking-platform'

if (Test-Path $EnvFile) {
    foreach ($line in Get-Content $EnvFile) {
        if ($line -match '^\s*CBP_SERVICE_AUTH_ENABLED\s*=\s*(.*)$') { $script:AuthEnabled = ($Matches[1].Trim() -eq 'true') }
        if ($line -match '^\s*CBP_SERVICE_AUTH_SECRET\s*=\s*(.*)$')  { $script:AuthSecret  = $Matches[1].Trim() }
        if ($line -match '^\s*CBP_SERVICE_AUTH_ISSUER\s*=\s*(.*)$')  { $script:AuthIssuer  = $Matches[1].Trim() }
    }
}

function ConvertTo-Base64Url {
    param([byte[]] $Bytes)
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-ServiceToken {
    <#
        Mints the same HS256 token the services mint for each other (ADR-008): sub and svc identify
        the caller, aud the target, scope what is being asked for, and exp keeps it short-lived.
    #>
    param(
        [Parameter(Mandatory)] [string] $Audience,
        [Parameter(Mandatory)] [string] $Scope,
        [string] $ServiceName = 'seed-script',
        [int]    $TtlSeconds = 300
    )

    $header = @{ alg = 'HS256'; typ = 'JWT' } | ConvertTo-Json -Compress
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $claims = [ordered]@{
        sub   = $ServiceName
        iss   = $script:AuthIssuer
        aud   = $Audience
        iat   = $now
        exp   = $now + $TtlSeconds
        jti   = [Guid]::NewGuid().ToString()
        svc   = $ServiceName
        scope = $Scope
    } | ConvertTo-Json -Compress

    $enc = [System.Text.Encoding]::UTF8
    $signingInput = (ConvertTo-Base64Url $enc.GetBytes($header)) + '.' + (ConvertTo-Base64Url $enc.GetBytes($claims))
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    try {
        $hmac.Key = $enc.GetBytes($script:AuthSecret)
        $sig = $hmac.ComputeHash($enc.GetBytes($signingInput))
    } finally {
        $hmac.Dispose()
    }
    return $signingInput + '.' + (ConvertTo-Base64Url $sig)
}

# --------------------------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------------------------

# Service -> direct port, used with -Direct.
$Ports = @{
    account      = 8081
    customer     = 8082
    ledger       = 8083
    payment      = 8084
    interest     = 8085
    card         = 8086
    notification = 8087
    risk         = 8088
    admin        = 8089
    reporting    = 8090
}

# Which audience and scope a guarded service expects.
$Guarded = @{
    ledger = @{ Audience = 'ledger-service';   Scope = 'ledger:post ledger:write' }
    risk   = @{ Audience = 'risk-aml-service'; Scope = 'risk:evaluate' }
}

function Get-ServiceBase {
    param([string] $Service)
    if ($Direct) { return "http://localhost:$($Ports[$Service])" }
    return $BaseUrl
}

$script:Created = 0
$script:Existed = 0
$script:Failed = 0

function Invoke-Seed {
    <#
        POSTs one item. 2xx means created, 409 means it was already there - both are success for a
        seed. Anything else is reported with the server's message so a failure is diagnosable.
    #>
    param(
        [Parameter(Mandatory)] [string] $Service,
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string] $Label,
        $Body,
        [string] $Method = 'POST'
    )

    $uri = (Get-ServiceBase $Service) + $Path
    $headers = @{
        'X-Correlation-Id'  = "seed-$([Guid]::NewGuid())"
        'X-Organization-Id' = 'DEFAULT'
    }
    if ($AccessToken) {
        $headers['Authorization'] = "Bearer $AccessToken"
    } elseif ($script:AuthEnabled -and $Guarded.ContainsKey($Service)) {
        $g = $Guarded[$Service]
        $headers['Authorization'] = 'Bearer ' + (New-ServiceToken -Audience $g.Audience -Scope $g.Scope)
    }

    if ($Verify) {
        Write-Host ("  [skip] " + $Label) -ForegroundColor DarkGray
        return $null
    }

    try {
        $params = @{
            Uri = $uri; Method = $Method; Headers = $headers; TimeoutSec = 30
            UseBasicParsing = $true
        }
        if ($null -ne $Body) {
            $params['Body'] = ($Body | ConvertTo-Json -Depth 6 -Compress)
            $params['ContentType'] = 'application/json'
        }
        $response = Invoke-WebRequest @params
        $script:Created++
        Write-Host ("  [new]  " + $Label) -ForegroundColor Green
        if ($response.Content) { return ($response.Content | ConvertFrom-Json) }
        return $null
    } catch {
        $status = $null
        if ($_.Exception.PSObject.Properties.Name -contains 'Response' -and $_.Exception.Response) {
            $status = [int] $_.Exception.Response.StatusCode
        }
        if ($status -eq 409) {
            $script:Existed++
            Write-Host ("  [have] " + $Label) -ForegroundColor DarkGray
            return $null
        }
        $script:Failed++
        Write-Host ("  [FAIL] " + $Label + "  -> HTTP $status") -ForegroundColor Red
        if ($status -eq 401) {
            Write-Host "         401: service auth is on and the token was not accepted." -ForegroundColor Yellow
            Write-Host "         Check CBP_SERVICE_AUTH_SECRET in $EnvFile matches the running containers." -ForegroundColor Yellow
        }
        if ($status -eq 404 -and -not $Direct) {
            Write-Host "         404 via the gateway usually means that route is not mapped; try -Direct." -ForegroundColor Yellow
        }
        return $null
    }
}

# --------------------------------------------------------------------------------------------
# State: ids of records that have no natural key, so a re-run reuses them instead of duplicating
# --------------------------------------------------------------------------------------------

$StateFile = Join-Path $repoRoot 'scripts/.seed-state.json'
$script:State = @{}
if (Test-Path $StateFile) {
    try {
        $loaded = Get-Content $StateFile -Raw | ConvertFrom-Json
        foreach ($p in $loaded.PSObject.Properties) { $script:State[$p.Name] = $p.Value }
    } catch {
        Write-Warning "Could not read $StateFile; treating it as empty."
    }
}

function Save-State {
    if ($Verify) { return }
    ($script:State | ConvertTo-Json -Depth 4) |
        Set-Content -Path $StateFile -Encoding UTF8
}

function Get-Resource {
    # GET that returns the parsed body, or $null for any non-2xx. Used to test whether a remembered
    # id still exists.
    param([string] $Service, [string] $Path)

    $headers = @{ 'X-Correlation-Id' = "seed-$([Guid]::NewGuid())"; 'X-Organization-Id' = 'DEFAULT' }
    if ($AccessToken) {
        $headers['Authorization'] = "Bearer $AccessToken"
    } elseif ($script:AuthEnabled -and $Guarded.ContainsKey($Service)) {
        $g = $Guarded[$Service]
        $headers['Authorization'] = 'Bearer ' + (New-ServiceToken -Audience $g.Audience -Scope $g.Scope)
    }
    try {
        $r = Invoke-WebRequest -Uri ((Get-ServiceBase $Service) + $Path) -Method GET -Headers $headers `
                               -TimeoutSec 30 -UseBasicParsing
        if ($r.Content) { return ($r.Content | ConvertFrom-Json) }
        return $null
    } catch {
        return $null
    }
}

function Get-OrCreate {
    <#
        Returns the id of a record that has no natural key. If the id from a previous run still
        resolves, it is reused; otherwise the record is created and the new id remembered.
    #>
    param(
        [Parameter(Mandatory)] [string] $Service,
        [Parameter(Mandatory)] [string] $StateKey,
        [Parameter(Mandatory)] [string] $GetPath,
        [Parameter(Mandatory)] [string] $CreatePath,
        [Parameter(Mandatory)] [string] $Label,
        [Parameter(Mandatory)] $Body
    )

    $known = $null
    if ($script:State.ContainsKey($StateKey)) { $known = $script:State[$StateKey] }

    if ($known) {
        $existing = Get-Resource -Service $Service -Path ($GetPath -replace '\{id\}', $known)
        if ($existing) {
            $script:Existed++
            Write-Host ("  [have] " + $Label + "  ($known)") -ForegroundColor DarkGray
            return $known
        }
        # The id is remembered but gone - the volume was probably wiped. Fall through and recreate.
        Write-Host ("  [gone] " + $Label + " no longer exists; recreating") -ForegroundColor Yellow
    }

    $created = Invoke-Seed -Service $Service -Path $CreatePath -Label $Label -Body $Body
    if ($created -and $created.PSObject.Properties.Name -contains 'id') {
        $script:State[$StateKey] = $created.id
        Save-State
        return $created.id
    }
    return $null
}

# --------------------------------------------------------------------------------------------
# The data
# --------------------------------------------------------------------------------------------

Write-Host ''
Write-Host 'Seeding core banking test data' -ForegroundColor Cyan
Write-Host ("  target      : " + $(if ($Direct) { 'direct service ports' } else { $BaseUrl }))
Write-Host ("  service auth: " + $(if ($script:AuthEnabled) { 'on (minting tokens)' } else { 'off' }))
if ($Verify) { Write-Host '  mode        : verify only, nothing will be written' -ForegroundColor Yellow }
Write-Host ''

# 1. Chart of accounts. Everything that moves money posts against these, so they come first.
Write-Host '1. Ledger chart of accounts' -ForegroundColor White
$ledgerAccounts = @(
    @{ code = 'DEP-001';           name = 'Customer Deposits';   accountType = 'LIABILITY' },
    @{ code = 'SETTLEMENT';        name = 'Settlement';          accountType = 'ASSET'     },
    @{ code = 'NOSTRO';            name = 'Nostro';              accountType = 'ASSET'     },
    @{ code = 'CARD-SETTLEMENT';   name = 'Card Settlement';     accountType = 'ASSET'     },
    @{ code = 'FEE-INCOME';        name = 'Fee Income';          accountType = 'INCOME'    },
    @{ code = 'INTEREST-EXPENSE';  name = 'Interest Expense';    accountType = 'EXPENSE'   }
)
foreach ($a in $ledgerAccounts) {
    Invoke-Seed -Service ledger -Path '/api/v1/ledger/accounts' -Label "ledger account $($a.code)" -Body @{
        code = $a.code; name = $a.name; accountType = $a.accountType; currencyCode = 'USD'
    } | Out-Null
}

# 2. Products. An account opened from CUR-STD carries a 500 overdraft, which is what makes the
#    debit flow testable without first crediting the account.
Write-Host ''
Write-Host '2. Account products' -ForegroundColor White
$products = @(
    @{ code = 'CUR-STD'; name = 'Standard Current'; accountType = 'CURRENT'; overdraftLimit = '500.00'; interestRatePercent = '0.00' },
    @{ code = 'SAV-STD'; name = 'Standard Savings'; accountType = 'SAVINGS'; overdraftLimit = '0.00';   interestRatePercent = '3.65' }
)
foreach ($p in $products) {
    Invoke-Seed -Service account -Path '/api/v1/products' -Label "product $($p.code)" -Body @{
        code = $p.code; name = $p.name; accountType = $p.accountType; currencyCode = 'USD'
        interestRatePercent = $p.interestRatePercent; monthlyFee = '0.00'; minBalance = '0.00'
        dailyLimit = '5000.00'; overdraftLimit = $p.overdraftLimit
    } | Out-Null
}

# 3. A customer, and 4. an account opened from the overdraft product.
#
# Neither has a natural key the API can dedupe on: onboarding does not expose a lookup by email,
# and opening from a product always creates a new account. Left alone they would duplicate on every
# run, which is exactly the drift that makes run-to-run reports meaningless. So the ids are
# remembered in a small state file and re-verified with a GET: if the record still resolves, the
# creation is skipped.
Write-Host ''
Write-Host '3. Customer' -ForegroundColor White

$customerId = Get-OrCreate -Service customer -StateKey 'customerId' -GetPath '/api/v1/customers/{id}' `
    -CreatePath '/api/v1/customers' -Label 'customer Ada Lovelace' -Body @{
        firstName = 'Ada'; lastName = 'Lovelace'; dateOfBirth = '1985-12-10'; nationality = 'GB'
        email = 'ada.seed@example.com'; phone = '+447700900123'; taxId = 'QQ123456C'
        dataProcessingConsent = $true; channel = 'WEB'
    }

Write-Host ''
Write-Host '4. Account with overdraft' -ForegroundColor White

if (-not $customerId) { $customerId = [Guid]::NewGuid().ToString() }
$accountId = Get-OrCreate -Service account -StateKey 'accountId' -GetPath '/api/v1/accounts/{id}' `
    -CreatePath '/api/v1/accounts/from-product' -Label 'account from CUR-STD' -Body @{
        productCode = 'CUR-STD'; primaryCustomerId = $customerId; additionalHolders = @(); mandateType = 'SINGLE'
    }
$account = if ($accountId) { Get-Resource -Service account -Path "/api/v1/accounts/$accountId" } else { $null }

# 5. Directories used by the channel payment flows.
Write-Host ''
Write-Host '5. Payment directories' -ForegroundColor White
Invoke-Seed -Service payment -Path '/api/v1/aliases' -Label 'alias ada.seed@example.com' -Body @{
    alias = 'ada.seed@example.com'; aliasType = 'EMAIL'; accountCode = 'SETTLEMENT'
} | Out-Null
Invoke-Seed -Service payment -Path '/api/v1/billers' -Label 'biller UTIL-CITYPOWER' -Body @{
    billerCode = 'UTIL-CITYPOWER'; name = 'City Power'; settlementAccount = 'SETTLEMENT'
} | Out-Null

# --------------------------------------------------------------------------------------------
# Report
# --------------------------------------------------------------------------------------------

Write-Host ''
Write-Host ('-' * 60)
Write-Host ("created: $script:Created   already present: $script:Existed   failed: $script:Failed")

if ($accountId) {
    Write-Host ''
    Write-Host 'Stable handles - the same on every run, so reports can reference them:' -ForegroundColor Cyan
    Write-Host ("  accountId     : " + $accountId)
    if ($account) { Write-Host ("  accountNumber : " + $account.accountNumber) }
    Write-Host ("  customerId    : " + $customerId)
    Write-Host ("  remembered in : " + $StateFile)
}

Write-Host ''
if ($script:Failed -gt 0) {
    Write-Host 'Some items failed. The platform is not fully seeded.' -ForegroundColor Red
    Write-Host 'Check the service is up:  docker compose ps' -ForegroundColor Yellow
    exit 1
}

Write-Host 'This data lives in the pgdata volume and survives `docker compose down` and restarts.' -ForegroundColor DarkGray
Write-Host 'It is deleted by `docker compose down -v`. Re-run this script to restore it.' -ForegroundColor DarkGray
Write-Host ''
