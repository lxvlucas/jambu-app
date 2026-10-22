param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$raizApi = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$nomeContainer = 'jambu-api-teste-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
$variaveis = @('POSTGRES_PASSWORD', 'JAMBU_TEST_DB_URL', 'JAMBU_TEST_DB_USER', 'JAMBU_TEST_DB_PASSWORD')
$anteriores = @{}
foreach ($nome in $variaveis) { $anteriores[$nome] = [Environment]::GetEnvironmentVariable($nome, 'Process') }
Push-Location $raizApi
try {
    & docker info --format '{{.ServerVersion}}'
    if ($LASTEXITCODE -ne 0) { throw 'Inicie o Docker Desktop antes de executar a verificação.' }
    & docker image inspect postgres:17-bookworm --format '{{.Id}}'
    if ($LASTEXITCODE -ne 0) { throw 'A imagem postgres:17-bookworm precisa estar disponível localmente.' }
    $env:POSTGRES_PASSWORD = [Guid]::NewGuid().ToString('N')
    & docker run --detach --name $nomeContainer --pull never --publish '127.0.0.1::5432' --tmpfs /var/lib/postgresql/data `
        --env POSTGRES_PASSWORD --env POSTGRES_USER=jambu_test --env POSTGRES_DB=jambu_test postgres:17-bookworm
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar PostgreSQL descartável.' }
    $pronto = $false
    for ($tentativa = 0; $tentativa -lt 30; $tentativa++) {
        & docker exec $nomeContainer pg_isready -h 127.0.0.1 -U jambu_test -d jambu_test *> $null
        if ($LASTEXITCODE -eq 0) { $pronto = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $pronto) { throw 'PostgreSQL de teste não ficou pronto.' }
    $porta = (& docker port $nomeContainer 5432/tcp).Trim().Split(':')[-1]
    $env:JAMBU_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:${porta}/jambu_test"
    $env:JAMBU_TEST_DB_USER = 'jambu_test'
    $env:JAMBU_TEST_DB_PASSWORD = $env:POSTGRES_PASSWORD
    $argumentosMaven = @('clean', 'verify')
    if ($Offline) { $argumentosMaven = @('-o') + $argumentosMaven }
    & mvn.cmd @argumentosMaven
    if ($LASTEXITCODE -ne 0) { throw 'A verificação Maven falhou.' }
    & (Join-Path $PSScriptRoot 'verificar-ambiente-dev.ps1')
    & (Join-Path $PSScriptRoot 'verificar-jar.ps1')
}
finally {
    # Somente o container efêmero cujo nome foi criado nesta execução.
    & docker rm --force --volumes $nomeContainer *> $null
    foreach ($nome in $variaveis) { [Environment]::SetEnvironmentVariable($nome, $anteriores[$nome], 'Process') }
    Pop-Location
}
