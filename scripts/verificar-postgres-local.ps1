param([Parameter(Mandatory)][string]$PostgresBin, [switch]$Offline)
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$raizTemporaria = Join-Path $raiz '.local'
$temporario = Join-Path $raizTemporaria ('verificacao-pg-' + [Guid]::NewGuid().ToString('N'))
foreach ($binario in @('initdb.exe', 'pg_ctl.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin $binario))) { throw 'Diretório de binários PostgreSQL inválido.' }
}
$variaveis = @('JAMBU_TEST_DB_URL','JAMBU_TEST_DB_USER','JAMBU_TEST_DB_PASSWORD')
$anteriores = @{}
foreach ($nome in $variaveis) { $anteriores[$nome] = [Environment]::GetEnvironmentVariable($nome, 'Process') }
$iniciado = $false
Push-Location $raiz
try {
    $null = New-Item -ItemType Directory -Path $temporario
    $senha = [Guid]::NewGuid().ToString('N')
    $senhaArquivo = Join-Path $temporario 'senha.tmp'
    [IO.File]::WriteAllText($senhaArquivo, $senha)
    & (Join-Path $PostgresBin 'initdb.exe') -D (Join-Path $temporario 'data') -U jambu_test -A scram-sha-256 "--pwfile=$senhaArquivo" --no-locale -E UTF8
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao inicializar PostgreSQL descartável.' }
    Remove-Item -LiteralPath $senhaArquivo
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start(); $porta = $listener.LocalEndpoint.Port; $listener.Stop()
    $processo = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList @(
        '-D', 'data', '-l', 'postgres.log', '-o', ('"-h 127.0.0.1 -p ' + $porta + '"'), '-w', 'start'
    ) -WorkingDirectory $temporario -WindowStyle Hidden -PassThru
    $processo.WaitForExit()
    if ($processo.ExitCode -ne 0) { throw 'Falha ao iniciar PostgreSQL descartável.' }
    $iniciado = $true
    $env:JAMBU_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$porta/postgres"
    $env:JAMBU_TEST_DB_USER = 'jambu_test'
    $env:JAMBU_TEST_DB_PASSWORD = $senha
    $argumentos = @('clean','verify')
    if ($Offline) { $argumentos = @('-o') + $argumentos }
    & mvn.cmd @argumentos
    if ($LASTEXITCODE -ne 0) { throw 'Build/testes falharam.' }
    & (Join-Path $PSScriptRoot 'verificar-ambiente-dev.ps1')
    & (Join-Path $PSScriptRoot 'verificar-jar.ps1')
    Write-Output 'Gate completo com PostgreSQL local descartável passou.'
} finally {
    if ($iniciado) {
        $processo = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList @('-D','data','-m','fast','-w','stop') `
            -WorkingDirectory $temporario -WindowStyle Hidden -PassThru
        $processo.WaitForExit()
        if ($processo.ExitCode -ne 0) { Write-Warning 'O PostgreSQL de teste não foi encerrado; artefatos preservados.'; $iniciado = $true }
        else { $iniciado = $false }
    }
    foreach ($nome in $variaveis) { [Environment]::SetEnvironmentVariable($nome, $anteriores[$nome], 'Process') }
    Pop-Location
    if (-not $iniciado -and (Test-Path -LiteralPath $temporario)) {
        $resolvido = (Resolve-Path -LiteralPath $temporario).Path
        $limite = [IO.Path]::GetFullPath($raizTemporaria) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolvido.StartsWith($limite, [StringComparison]::OrdinalIgnoreCase)) { throw 'Limpeza fora da pasta de teste recusada.' }
        Remove-Item -LiteralPath $resolvido -Recurse -Force
    }
}
