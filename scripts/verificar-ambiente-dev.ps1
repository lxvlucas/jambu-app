$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ambiente-dev.ps1')
$pasta = Join-Path ([IO.Path]::GetTempPath()) ('jambu-env-' + [Guid]::NewGuid().ToString('N'))
$api = Join-Path $pasta 'api.env'
$compose = Join-Path $pasta 'compose.env'
$nomes = @('JAMBU_DB_URL','JAMBU_DB_USER','JAMBU_DB_PASSWORD','JAMBU_GOOGLE_PLACES_API_KEY','JAMBU_GOOGLE_PLACES_HABILITADO')
$anteriores = @{}
foreach ($nome in $nomes) { $anteriores[$nome] = [Environment]::GetEnvironmentVariable($nome, 'Process') }
try {
    $null = New-Item -ItemType Directory -Path $pasta
    foreach ($nome in $nomes) { [Environment]::SetEnvironmentVariable($nome, $null, 'Process') }
    @('POSTGRES_DB=teste', 'POSTGRES_USER=teste', 'POSTGRES_PASSWORD=senha-compose') | Set-Content -LiteralPath $compose
    @('JAMBU_DB_USER=usuario-arquivo', 'JAMBU_GOOGLE_PLACES_API_KEY=''$(throw "nao-executar")''',
      'JAMBU_GOOGLE_PLACES_HABILITADO=false', 'PATH=nao-substituir') | Set-Content -LiteralPath $api
    $pathAnterior = $env:PATH
    $env:JAMBU_DB_USER = 'usuario-processo'
    Importar-AmbienteDev -ArquivoComposeEnv $compose -ArquivoApiEnv $api
    $verificacoes = @{
        Precedencia=($env:JAMBU_DB_USER -eq 'usuario-processo')
        ComposeSenha=($env:JAMBU_DB_PASSWORD -eq 'senha-compose')
        ComposeUrl=($env:JAMBU_DB_URL -eq 'jdbc:postgresql://127.0.0.1:5432/teste')
        PathPreservado=($env:PATH -eq $pathAnterior)
        ValorLiteral=($env:JAMBU_GOOGLE_PLACES_API_KEY -eq '$(throw "nao-executar")')
        BooleanoLiteral=($env:JAMBU_GOOGLE_PLACES_HABILITADO -eq 'false')
    }
    $falhas = @($verificacoes.Keys | Where-Object { -not $verificacoes[$_] })
    if ($falhas.Count) {
        throw "Falha nas verificações do ambiente: $($falhas -join ', '). Valores omitidos."
    }
    'linha sem igual' | Set-Content -LiteralPath $api
    $rejeitou = $false
    try { Importar-AmbienteDev -ArquivoComposeEnv $compose -ArquivoApiEnv $api } catch { $rejeitou = $true }
    if (-not $rejeitou) { throw 'Arquivo inválido não foi rejeitado.' }
    'Ambiente local: precedência, conteúdo literal, variáveis permitidas e rejeição de formato passaram.'
}
finally {
    foreach ($nome in $nomes) { [Environment]::SetEnvironmentVariable($nome, $anteriores[$nome], 'Process') }
    # Arquivos exatos criados acima; nenhuma remoção recursiva.
    foreach ($arquivo in @($api, $compose)) { if (Test-Path -LiteralPath $arquivo) { Remove-Item -LiteralPath $arquivo } }
    if (Test-Path -LiteralPath $pasta) { Remove-Item -LiteralPath $pasta }
}
