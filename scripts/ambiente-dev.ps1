# Compartilhado somente por comandos locais de desenvolvimento; não é carregado nos testes.
function Importar-AmbienteDev {
    param(
        [string]$ArquivoComposeEnv = (Join-Path $PSScriptRoot '../../../jambu-docker/.env'),
        [string]$ArquivoApiEnv = (Join-Path $PSScriptRoot '../.env.local')
    )
    $permitidas = @(
        'JAMBU_AUTH_CHAVE_REPETICAO', 'JAMBU_AUTH_ORIGEM_WEB', 'JAMBU_AUTH_COOKIE_SEGURO',
        'JAMBU_AUTH_DURACAO_ACESSO', 'JAMBU_AUTH_INATIVIDADE', 'JAMBU_AUTH_LIMITE_ABSOLUTO',
        'JAMBU_DB_URL', 'JAMBU_DB_USER', 'JAMBU_DB_PASSWORD', 'JAMBU_BIND_ADDRESS',
        'JAMBU_LIMITE_DISHES', 'JAMBU_LIMITE_REVIEW', 'JAMBU_URL_COMPARTILHAMENTO',
        'JAMBU_GOOGLE_PLACES_HABILITADO', 'JAMBU_GOOGLE_PLACES_API_KEY',
        'JAMBU_GOOGLE_PLACES_TIMEOUT', 'JAMBU_GOOGLE_PLACES_BASE_URL',
        'JAMBU_GOOGLE_PLACES_LIMITE_DIA', 'JAMBU_REVISORES_RESTAURANTES', 'JAMBU_AREAS_CATALOGO', 'JAMBU_LIMITE_CADASTROS_DIA', 'JAMBU_LIMITE_BUSCAS_AMPLIADAS_DIA', 'JAMBU_CASAMENTO_OVERTURE', 'JAMBU_CANDIDATOS_CASAMENTO',
        'JAMBU_FOTOS_DIRETORIO_LOCAL', 'JAMBU_FOTOS_STORAGE_TIPO',
        'JAMBU_FOTOS_SUPABASE_URL', 'JAMBU_FOTOS_SUPABASE_KEY', 'JAMBU_FOTOS_SUPABASE_BUCKET',
        'JAMBU_FOTOS_MODERACAO_HABILITADA', 'JAMBU_FOTOS_MODERACAO_PROVEDOR',
        'JAMBU_FOTOS_MODERACAO_BASE_URL', 'JAMBU_FOTOS_MODERACAO_API_KEY',
        'JAMBU_FOTOS_MODERACAO_TIMEOUT', 'JAMBU_MODERADORES_FOTOS'
    )
    function Ler-VariaveisLiterais([string]$Caminho, [string[]]$Chaves) {
        $valores = @{}
        if (Test-Path -LiteralPath $Caminho) {
            foreach ($linha in Get-Content -LiteralPath $Caminho) {
                if ([string]::IsNullOrWhiteSpace($linha) -or $linha.TrimStart().StartsWith('#')) { continue }
                if ($linha -notmatch '^\s*([A-Z_]+)\s*=(.*)$') { throw 'Formato inválido no arquivo de ambiente local; use CHAVE=valor literal.' }
                $chave = $Matches[1]
                $valor = $Matches[2].Trim()
                if ($chave -notin $Chaves) { continue }
                if ($valor.Length -ge 2 -and (($valor.StartsWith('"') -and $valor.EndsWith('"')) -or ($valor.StartsWith("'") -and $valor.EndsWith("'")))) {
                    $valor = $valor.Substring(1, $valor.Length - 2)
                }
                $valores[$chave] = $valor
            }
        }
        return $valores
    }
    $locais = Ler-VariaveisLiterais $ArquivoApiEnv $permitidas
    foreach ($chave in $locais.Keys) {
        # Variável não vazia do processo vence o arquivo. Para desativar Places, use HABILITADO=false.
        # Não executar ou interpolar o conteúdo; Windows pode representar variável ausente como "".
        if ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($chave, 'Process'))) {
            [Environment]::SetEnvironmentVariable($chave, $locais[$chave], 'Process')
        }
    }
    $compose = Ler-VariaveisLiterais $ArquivoComposeEnv @('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD')
    if (-not $env:JAMBU_DB_URL -and $compose.POSTGRES_DB) {
        $env:JAMBU_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/' + [Uri]::EscapeDataString($compose.POSTGRES_DB)
    }
    if (-not $env:JAMBU_DB_USER) { $env:JAMBU_DB_USER = $compose.POSTGRES_USER }
    if (-not $env:JAMBU_DB_PASSWORD) { $env:JAMBU_DB_PASSWORD = $compose.POSTGRES_PASSWORD }
    if (-not $env:JAMBU_DB_URL -or -not $env:JAMBU_DB_USER -or -not $env:JAMBU_DB_PASSWORD) {
        throw 'Configure JAMBU_DB_* no processo/.env.local ou o .env do Compose existente.'
    }
}
