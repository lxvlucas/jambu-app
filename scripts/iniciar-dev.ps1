param(
    [string]$ArquivoComposeEnv = (Join-Path $PSScriptRoot '../../../jambu-docker/.env'),
    [string]$ArquivoApiEnv = (Join-Path $PSScriptRoot '../.env.local'),
    [ValidateRange(0, 65535)][int]$Porta = 8080,
    [switch]$Jar
)
$ErrorActionPreference = 'Stop'
$raizApi = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'ambiente-dev.ps1')
$nomes = @('JAMBU_DB_URL','JAMBU_DB_USER','JAMBU_DB_PASSWORD','JAMBU_BIND_ADDRESS',
    'JAMBU_AUTH_CHAVE_REPETICAO','JAMBU_AUTH_ORIGEM_WEB','JAMBU_AUTH_COOKIE_SEGURO',
    'JAMBU_AUTH_DURACAO_ACESSO','JAMBU_AUTH_INATIVIDADE','JAMBU_AUTH_LIMITE_ABSOLUTO',
    'JAMBU_LIMITE_DISHES','JAMBU_LIMITE_REVIEW','JAMBU_URL_COMPARTILHAMENTO',
    'JAMBU_GOOGLE_PLACES_HABILITADO','JAMBU_GOOGLE_PLACES_API_KEY',
    'JAMBU_GOOGLE_PLACES_TIMEOUT','JAMBU_GOOGLE_PLACES_BASE_URL',
    'JAMBU_FOTOS_DIRETORIO_LOCAL','JAMBU_FOTOS_STORAGE_TIPO',
    'JAMBU_FOTOS_SUPABASE_URL','JAMBU_FOTOS_SUPABASE_KEY','JAMBU_FOTOS_SUPABASE_BUCKET',
    'JAMBU_FOTOS_MODERACAO_HABILITADA','JAMBU_FOTOS_MODERACAO_PROVEDOR',
    'JAMBU_FOTOS_MODERACAO_BASE_URL','JAMBU_FOTOS_MODERACAO_API_KEY',
    'JAMBU_FOTOS_MODERACAO_TIMEOUT','JAMBU_MODERADORES_USERNAMES')
$anteriores = @{}
foreach ($nome in $nomes) { $anteriores[$nome] = [Environment]::GetEnvironmentVariable($nome, 'Process') }
Push-Location $raizApi
try {
    Importar-AmbienteDev -ArquivoComposeEnv $ArquivoComposeEnv -ArquivoApiEnv $ArquivoApiEnv
    if ($Jar) {
        $arquivoJar = Join-Path $raizApi 'target/jambu-api-0.0.1-SNAPSHOT.jar'
        if (-not (Test-Path -LiteralPath $arquivoJar)) { throw 'Gere o JAR com scripts/verificar.ps1 antes de iniciar com -Jar.' }
        $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
        & $java -jar $arquivoJar '--spring.profiles.active=dev' "--server.port=$Porta"
    } else {
        & mvn.cmd spring-boot:run '-Dspring-boot.run.profiles=dev' "-Dspring-boot.run.arguments=--server.port=$Porta"
    }
    if ($LASTEXITCODE -ne 0) { throw 'A inicialização da API falhou.' }
}
finally {
    foreach ($nome in $nomes) { [Environment]::SetEnvironmentVariable($nome, $anteriores[$nome], 'Process') }
    Pop-Location
}
