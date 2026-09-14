<#
.SYNOPSIS
    Creates the .env file the Core Banking Platform reads, generating the secrets for you.

.DESCRIPTION
    Every setting and secret for this platform lives in one gitignored .env at the repository root
    (see .env.example for the annotated template). This script writes that file.

    Two profiles:

      Local    Everything off. The platform starts with no broker, no service tokens and no edge
               auth. Fastest way to get it running; not safe to expose.

      Sandbox  Service-to-service JWTs and Kafka switched on, and a generated database password.
               Matches docs/DEPLOYMENT_SANDBOX.md.

    Secrets are generated with a cryptographic RNG, never a fixed default. The service-auth secret
    is 48 random bytes base64-encoded, comfortably over the 32-byte minimum the services enforce at
    startup.

    The file is written as UTF-8 *without* a byte-order mark. Windows PowerShell's Set-Content adds
    a BOM, and a BOM on the first line of a .env makes docker compose read the first key name with
    invisible leading bytes — so this script writes the bytes itself.

.PARAMETER Profile
    Local (default) or Sandbox.

.PARAMETER Path
    Where to write. Defaults to .env beside the repository root.

.PARAMETER Force
    Overwrite an existing file. Without this the script refuses, so you cannot silently destroy
    secrets that other people or environments are already using.

.PARAMETER SetSessionVariables
    Also set the values as environment variables in the current PowerShell session, for running a
    service from the IDE or `mvn spring-boot:run` outside docker compose.

.PARAMETER IssuerUri
    The IdP issuer URI for edge authentication. Supplying it turns edge auth on.

.PARAMETER NewDatabasePassword
    Rotate the database password instead of keeping the one already in .env. PostgreSQL stores the
    role password in its data volume, so a new password does not update the database - it locks the
    services out of it. Rotating therefore also needs `docker compose down -v`, which destroys the
    data. Left off, an existing password is preserved so switching profiles is safe.

.EXAMPLE
    .\scripts\Setup-Environment.ps1
    Local profile, everything off.

.EXAMPLE
    .\scripts\Setup-Environment.ps1 -Profile Sandbox
    Generates secrets and switches on service auth and Kafka.

.EXAMPLE
    .\scripts\Setup-Environment.ps1 -Profile Sandbox -IssuerUri https://idp.internal/realms/bank -Force
    Sandbox with edge authentication, overwriting an existing .env.

.EXAMPLE
    .\scripts\Setup-Environment.ps1 -SetSessionVariables
    Also exports the values into this shell, for running a service outside compose.
#>
[CmdletBinding()]
param(
    [ValidateSet('Local', 'Sandbox')]
    [string] $Profile = 'Local',

    [string] $Path,

    [switch] $Force,

    [switch] $SetSessionVariables,

    [string] $IssuerUri,

    [switch] $NewDatabasePassword
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# --------------------------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------------------------

function New-Secret {
    <#
        Cryptographically random bytes, base64 encoded. 48 bytes -> 64 base64 characters, which is
        well above the 32-byte floor ServiceAuthProperties enforces.
    #>
    param([int] $ByteCount = 48)

    $bytes = New-Object byte[] $ByteCount
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

function Write-Utf8NoBom {
    # Set-Content -Encoding utf8 emits a BOM on Windows PowerShell 5.1; docker compose then reads
    # the first key with invisible leading bytes. Write the bytes ourselves instead.
    param([string] $FilePath, [string] $Content)

    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($FilePath, $Content, $encoding)
}

function Get-RepositoryRoot {
    # The script lives in <root>/scripts, so the root is its parent.
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    return (Resolve-Path (Join-Path $scriptDir '..')).Path
}

# --------------------------------------------------------------------------------------------
# Resolve target
# --------------------------------------------------------------------------------------------

$repoRoot = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $repoRoot '..')).Path

if (-not $Path) {
    $Path = Join-Path $repoRoot '.env'
}

if ((Test-Path $Path) -and (-not $Force)) {
    Write-Host ''
    Write-Warning "$Path already exists."
    Write-Host "  Re-run with -Force to overwrite it." -ForegroundColor Yellow
    Write-Host "  Overwriting regenerates the secrets, which invalidates every token already issued" -ForegroundColor Yellow
    Write-Host "  and locks the services out of an existing database." -ForegroundColor Yellow
    Write-Host ''
    exit 1
}

$isSandbox = ($Profile -eq 'Sandbox')

# The database password is the one value that must NOT change casually. PostgreSQL stores the role
# password inside the data volume, so a new password in .env does not update the database - it just
# locks every service out of it, and the only way back is `docker compose down -v`, which destroys
# the data. So an existing password is reused by default; -NewDatabasePassword forces a fresh one.
$existingDbPassword = $null
if (Test-Path $Path) {
    foreach ($line in Get-Content $Path) {
        if ($line -match '^\s*CBP_POSTGRES_PASSWORD\s*=\s*(.+)$') { $existingDbPassword = $Matches[1].Trim() }
    }
}

# --------------------------------------------------------------------------------------------
# Values
# --------------------------------------------------------------------------------------------

if ($isSandbox) {
    $postgresPassword = New-Secret -ByteCount 24
    $serviceAuthSecret = New-Secret -ByteCount 48
    $serviceAuthEnabled = 'true'
    $kafkaEnabled = 'true'
    $organizationDefault = 'SANDBOX'
} else {
    # Local: the well-known demo credential, and every feature off. Nothing here is a secret,
    # which is the point — a local checkout should not pretend to be secure.
    $postgresPassword = 'banking'
    $serviceAuthSecret = ''
    $serviceAuthEnabled = 'true'
    $kafkaEnabled = 'false'
    $organizationDefault = 'DEFAULT'
}

# A secret is required whenever auth is on: ServiceAuthProperties refuses anything shorter than 32
# bytes at startup, so an enabled flag with an empty secret stops ledger-service and
# risk-aml-service from booting at all. Generate one here so the two can never disagree, whichever
# profile is selected and whoever edits the flags above.
if ($serviceAuthEnabled -eq 'true' -and [string]::IsNullOrWhiteSpace($serviceAuthSecret)) {
    $serviceAuthSecret = New-Secret -ByteCount 48
}

# Keep the database password unless explicitly told otherwise (see the note above the read).
$dbPasswordPreserved = $false
if ($existingDbPassword -and -not $NewDatabasePassword) {
    $postgresPassword = $existingDbPassword
    $dbPasswordPreserved = $true
}

$gatewaySecurityEnabled = 'false'
$gatewayIssuerUri = ''
if ($IssuerUri) {
    $gatewaySecurityEnabled = 'true'
    $gatewayIssuerUri = $IssuerUri
}

$generatedOn = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss zzz')

# --------------------------------------------------------------------------------------------
# Compose the file
# --------------------------------------------------------------------------------------------

$content = @"
####################################################################################################
# Core Banking Platform — environment
#
# Generated by scripts/Setup-Environment.ps1 on $generatedOn
# Profile: $Profile
#
# This file is gitignored and MUST NOT be committed. It contains live secrets.
# See .env.example for what each variable means.
####################################################################################################

# ---------------------------------------------------------------------------------------------
# Database
# ---------------------------------------------------------------------------------------------
CBP_POSTGRES_DB=banking
CBP_POSTGRES_USER=banking
CBP_POSTGRES_PASSWORD=$postgresPassword

# ---------------------------------------------------------------------------------------------
# Service-to-service authentication (ADR-008)
# ---------------------------------------------------------------------------------------------
# Shared HMAC secret: every holder can mint as well as verify, so this is a sandbox posture, not a
# production one. Production moves to RS256 + JWKS.
CBP_SERVICE_AUTH_ENABLED=$serviceAuthEnabled
CBP_SERVICE_AUTH_SECRET=$serviceAuthSecret
CBP_SERVICE_AUTH_ISSUER=core-banking-platform
CBP_SERVICE_AUTH_TOKEN_TTL=60
CBP_SERVICE_AUTH_CLOCK_SKEW=30
# Leave unset: receivers (ledger, risk-aml) default to requiring inbound identity, callers do not.
# CBP_SERVICE_AUTH_REQUIRE_INBOUND=

# ---------------------------------------------------------------------------------------------
# Edge authentication (api-gateway)
# ---------------------------------------------------------------------------------------------
CBP_GATEWAY_SECURITY_ENABLED=$gatewaySecurityEnabled
CBP_GATEWAY_JWT_ISSUER_URI=$gatewayIssuerUri
CBP_GATEWAY_RATE_LIMIT=120

# ---------------------------------------------------------------------------------------------
# Event backbone — Kafka (ADR-005)
# ---------------------------------------------------------------------------------------------
CBP_KAFKA_ENABLED=$kafkaEnabled
CBP_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
CBP_KAFKA_TOPIC_PAYMENT_EVENTS=payment.events
CBP_KAFKA_TOPIC_ACCOUNT_EVENTS=account.events
CBP_KAFKA_TOPIC_CARD_EVENTS=card.events
CBP_KAFKA_TOPIC_CUSTOMER_EVENTS=customer.events
CBP_KAFKA_CONSUMER_GROUP=notification-service
CBP_KAFKA_SEND_TIMEOUT_MS=10000

# ---------------------------------------------------------------------------------------------
# Inter-service wiring
# ---------------------------------------------------------------------------------------------
CBP_LEDGER_POSTING=grpc
CBP_LEDGER_SETTLEMENT_ACCOUNT=SETTLEMENT
CBP_SCREENING_ADAPTER=risk-service
CBP_AML_LARGE_AMOUNT_THRESHOLD=10000

# ---------------------------------------------------------------------------------------------
# Tenant
# ---------------------------------------------------------------------------------------------
CBP_ORGANIZATION_DEFAULT=$organizationDefault
"@

Write-Utf8NoBom -FilePath $Path -Content $content

# Lock the file down to the current user: NTFS ACLs, not chmod. icacls rather than Set-Acl on
# purpose - Set-Acl writes back the whole security descriptor including the SACL, which needs
# SeSecurityPrivilege and so fails for a normal user on their own file.
$aclApplied = $false
try {
    $account = "$env:USERDOMAIN\$env:USERNAME"
    $null = & icacls $Path /inheritance:r /grant:r "${account}:(F)" 2>&1
    $aclApplied = ($LASTEXITCODE -eq 0)
} catch {
    $aclApplied = $false
}

# --------------------------------------------------------------------------------------------
# Optionally export into this session
# --------------------------------------------------------------------------------------------

$exported = 0
if ($SetSessionVariables) {
    foreach ($line in ($content -split "`n")) {
        $trimmed = $line.Trim()
        if ($trimmed -match '^(CBP_[A-Z0-9_]+)=(.*)$') {
            Set-Item -Path ("Env:" + $Matches[1]) -Value $Matches[2]
            $exported++
        }
    }
}

# --------------------------------------------------------------------------------------------
# Report
# --------------------------------------------------------------------------------------------

Write-Host ''
Write-Host "Wrote $Path" -ForegroundColor Green
Write-Host "  Profile                : $Profile"
Write-Host "  Service auth           : $serviceAuthEnabled"
Write-Host "  Kafka                  : $kafkaEnabled"
Write-Host "  Edge auth              : $gatewaySecurityEnabled"
if ($isSandbox) {
    Write-Host "  Generated secrets      : CBP_SERVICE_AUTH_SECRET (48 bytes)"
}
if ($dbPasswordPreserved) {
    Write-Host "  Database password      : kept from the previous .env (volume stays usable)"
} elseif ($isSandbox) {
    Write-Host "  Database password      : NEWLY GENERATED - existing volume will reject it"
}
if (-not $aclApplied) {
    Write-Warning "  Could not tighten file permissions; check who can read $Path."
}
if ($SetSessionVariables) {
    Write-Host "  Exported to session    : $exported variables"
}

Write-Host ''
if ($isSandbox) {
    Write-Host 'Sandbox notes:' -ForegroundColor Cyan
    if ($dbPasswordPreserved) {
        Write-Host '  - The database password was KEPT from your previous .env, so the existing postgres'
        Write-Host '    volume and all its data keep working. Pass -NewDatabasePassword to rotate it, but'
        Write-Host '    that needs `docker compose down -v` afterwards, which destroys the data.'
    } else {
        Write-Host '  - A NEW database password was generated. An existing postgres volume still has the'
        Write-Host '    OLD one, so services will fail to connect until you run `docker compose down -v`.'
        Write-Host '    That destroys all data.' -ForegroundColor Yellow
    }
    if (-not $IssuerUri) {
        Write-Host '  - Edge auth is OFF: the gateway permits every request. Pass -IssuerUri to enable it.'
    }
    Write-Host ''
}

Write-Host 'Next:' -ForegroundColor Cyan
Write-Host '  docker compose build'
Write-Host '  docker compose up -d'
Write-Host '  docker compose ps          # kafka healthy, kafka-init Exited (0)'
Write-Host ''
Write-Host 'Then verify the switches actually took effect (docs/DEPLOYMENT_SANDBOX.md section 5):'
if ($serviceAuthEnabled -eq 'true') {
    Write-Host '  (Invoke-WebRequest -Uri http://localhost:8083/api/v1/ledger/trial-balance -SkipHttpErrorCheck).StatusCode'
    Write-Host '  # expect 401 — 200 means the secret did not reach the container'
} else {
    Write-Host '  curl.exe -s http://localhost:8083/actuator/health'
}
Write-Host ''
