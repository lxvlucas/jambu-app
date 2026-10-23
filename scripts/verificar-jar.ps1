$ErrorActionPreference = 'Stop'
$raizApi = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$jar = Join-Path $raizApi 'target/jambu-api-0.0.1-SNAPSHOT.jar'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
$variaveis = @(
    'JAMBU_DB_URL', 'JAMBU_DB_USER', 'JAMBU_DB_PASSWORD',
    'JAMBU_GOOGLE_PLACES_HABILITADO', 'JAMBU_GOOGLE_PLACES_API_KEY',
    'JAMBU_FOTOS_DIRETORIO_LOCAL', 'JAMBU_FOTOS_STORAGE_TIPO',
    'JAMBU_FOTOS_MODERACAO_HABILITADA', 'JAMBU_FOTOS_MODERACAO_API_KEY'
)
$anteriores = @{}
foreach ($nome in $variaveis) { $anteriores[$nome] = [Environment]::GetEnvironmentVariable($nome, 'Process') }
$processoApi = $null
function Parar-ApiTeste {
    if ($null -ne $script:processoApi) {
        if (-not $script:processoApi.HasExited) {
            Stop-Process -Id $script:processoApi.Id -Force -ErrorAction SilentlyContinue
            $script:processoApi.WaitForExit()
        }
        $script:processoApi = $null
    }
}
function Iniciar-ApiTeste([string]$sufixo) {
    $saida = Join-Path $raizApi "target/jar-$sufixo.log"
    $erro = Join-Path $raizApi "target/jar-$sufixo-erro.log"
    $script:processoApi = Start-Process -FilePath $java -ArgumentList @('-jar', "`"$jar`"", '--server.port=0', '--spring.profiles.active=dev') `
        -WorkingDirectory $raizApi -WindowStyle Hidden -PassThru -RedirectStandardOutput $saida -RedirectStandardError $erro
    for ($tentativa = 0; $tentativa -lt 60; $tentativa++) {
        if ($script:processoApi.HasExited) { throw "A API encerrou antes de iniciar. Consulte target/jar-$sufixo.log." }
        $conteudo = Get-Content -LiteralPath $saida -Raw -ErrorAction SilentlyContinue
        if ($conteudo -match 'Tomcat started on port (\d+)') {
            $base = "http://127.0.0.1:$($Matches[1])"
            if ((Invoke-RestMethod "$base/actuator/health").status -eq 'UP') { return $base }
        }
        Start-Sleep -Seconds 1
    }
    throw 'A API não ficou pronta em 60 segundos.'
}
try {
    if (-not $env:JAMBU_TEST_DB_URL -or -not $env:JAMBU_TEST_DB_USER -or -not $env:JAMBU_TEST_DB_PASSWORD) {
        throw 'Execute pelo script verificar.ps1, com o PostgreSQL descartável.'
    }
    $env:JAMBU_DB_URL = $env:JAMBU_TEST_DB_URL
    $env:JAMBU_DB_USER = $env:JAMBU_TEST_DB_USER
    $env:JAMBU_DB_PASSWORD = $env:JAMBU_TEST_DB_PASSWORD
    $env:JAMBU_GOOGLE_PLACES_HABILITADO = 'false'
    $env:JAMBU_GOOGLE_PLACES_API_KEY = ''
    $env:JAMBU_FOTOS_DIRETORIO_LOCAL = Join-Path $raizApi 'target/storage-fotos-smoke'
    $env:JAMBU_FOTOS_STORAGE_TIPO = 'local'
    $env:JAMBU_FOTOS_MODERACAO_HABILITADA = 'false'
    $env:JAMBU_FOTOS_MODERACAO_API_KEY = ''
    $base = Iniciar-ApiTeste 'primeira-inicializacao'
    $nome = 'smoke_' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
    $senha = [Guid]::NewGuid().ToString('N')
    $cadastro = @{ username=$nome; email="$nome@example.test"; celular='98999999999'; senha=$senha } | ConvertTo-Json
    $null = Invoke-RestMethod "$base/api/auth/cadastro" -Method Post -ContentType 'application/json' -Body $cadastro
    $login = @{ identificador=$nome; senha=$senha } | ConvertTo-Json
    $sessao = Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body $login
    $headers = @{ Authorization="Bearer $($sessao.token)"; 'Idempotency-Key'='smoke-persistencia' }
    $headersCadastro = @{ Authorization="Bearer $($sessao.token)"; 'Idempotency-Key'='smoke-restaurante' }
    $nomeRestaurante = 'Restaurante smoke ' + [Guid]::NewGuid().ToString('N')
    $dadosRestaurante = @{ nome=$nomeRestaurante; latitude=-2.53; longitude=-44.30; categoria='RESTAURANTE' } | ConvertTo-Json
    $cadastroRestaurante = Invoke-RestMethod "$base/api/restaurantes/cadastros" -Method Post -ContentType 'application/json' -Headers $headersCadastro -Body $dadosRestaurante
    if ($cadastroRestaurante.situacao -ne 'CRIADO') { throw 'O cadastro local não concluiu.' }
    $restauranteIdSmoke = $cadastroRestaurante.restaurante.id
    $termo = [Uri]::EscapeDataString($nomeRestaurante)
    $busca = Invoke-RestMethod "$base/api/restaurantes?termo=$termo" -Headers $headers
    if ($busca.estadoExterno -ne 'NAO_SOLICITADO' -or -not $busca.podeVerMais -or $busca.locais[0].id -ne $restauranteIdSmoke) {
        throw 'A busca não priorizou o catálogo local com opção de ampliar.'
    }
    $ampliada = Invoke-RestMethod "$base/api/restaurantes?termo=$termo&ampliar=true" -Headers $headers
    if ($ampliada.estadoExterno -ne 'DESATIVADO' -or $ampliada.locais[0].id -ne $restauranteIdSmoke -or -not $ampliada.podeAdicionar) {
        throw 'A ampliação com Google desativado não preservou o fluxo local.'
    }
    $pedido = @{ restauranteId=$restauranteIdSmoke; dishes=@(@{ nome='Prato de teste'; nota=4.5 }); comeriaNovamente=$true } | ConvertTo-Json -Depth 5
    $meal = Invoke-RestMethod "$base/api/meals" -Method Post -ContentType 'application/json' -Headers $headers -Body $pedido
    Parar-ApiTeste
    $base = Iniciar-ApiTeste 'reinicializacao'
    $cadastroRepetido = Invoke-RestMethod "$base/api/restaurantes/cadastros" -Method Post -ContentType 'application/json' -Headers $headersCadastro -Body $dadosRestaurante
    if ($cadastroRepetido.restaurante.id -ne $restauranteIdSmoke) { throw 'O cadastro não preservou seu UUID após reinício.' }
    $persistida = Invoke-RestMethod "$base/api/meals/$($meal.id)" -Headers $headers
    if ($persistida.id -ne $meal.id -or $persistida.notaOverall -ne 4.5) { throw 'A Meal não persistiu corretamente após reinício.' }
    $repetida = Invoke-RestMethod "$base/api/meals" -Method Post -ContentType 'application/json' -Headers $headers -Body $pedido
    if ($repetida.id -ne $meal.id) { throw 'A idempotência não persistiu após reinício.' }
    $null = Invoke-RestMethod "$base/api/auth/logout" -Method Post -Headers $headers
    $statusNegado = 0
    $iwrParams = @{ Uri="$base/api/usuarios/me"; Headers=$headers }
    $parametrosSuportados = (Get-Command Invoke-WebRequest).Parameters
    if ($parametrosSuportados.ContainsKey('SkipHttpErrorCheck')) { $iwrParams.SkipHttpErrorCheck = $true }
    if ($parametrosSuportados.ContainsKey('UseBasicParsing')) { $iwrParams.UseBasicParsing = $true }
    try {
        $negado = Invoke-WebRequest @iwrParams
        $statusNegado = [int]$negado.StatusCode
    } catch {
        if ($_.Exception.Response -and $null -ne $_.Exception.Response.StatusCode) {
            $statusNegado = [int]$_.Exception.Response.StatusCode
        } else { throw }
    }
    if ($statusNegado -ne 401) { throw 'Logout não revogou a sessão.' }
    $senhaDemo = ConvertTo-SecureString ([Guid]::NewGuid().ToString('N')) -AsPlainText -Force
    $prefixoDemo = 'demo_' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
    $carga1 = & (Join-Path $PSScriptRoot 'carregar-demonstracao.ps1') -BaseUrl $base `
        -RestauranteId $restauranteIdSmoke -Prefixo $prefixoDemo -Senha $senhaDemo
    $carga2 = & (Join-Path $PSScriptRoot 'carregar-demonstracao.ps1') -BaseUrl $base `
        -RestauranteId $restauranteIdSmoke -Prefixo $prefixoDemo -Senha $senhaDemo
    if ($carga1.QuantidadeMealsDaCarga -ne 3 -or ($carga1.Meals -join ',') -ne ($carga2.Meals -join ',')) {
        throw 'Repetir a carga demonstrativa não preservou as mesmas três Meals.'
    }
    Write-Output 'Smoke do JAR passou: saúde, login, cadastro e busca local, ampliação sem Google, Meal, reinício, idempotência dos dois cadastros, logout e carga demonstrativa repetida; zero chamadas Google.'
}
finally {
    Parar-ApiTeste
    foreach ($nome in $variaveis) { [Environment]::SetEnvironmentVariable($nome, $anteriores[$nome], 'Process') }
}
