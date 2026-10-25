param(
    [Parameter(Mandatory)][Guid]$RestauranteId,
    [Uri]$BaseUrl = 'http://127.0.0.1:8080',
    [ValidatePattern('^[a-z][a-z0-9_]{2,18}$')][string]$Prefixo = 'jambu_demo',
    [Parameter(Mandatory)][SecureString]$Senha
)
$ErrorActionPreference = 'Stop'
# A carga escreve somente por endpoints normais e exige um restaurante já validado.
# Não contém SQL de fixture, chave Google, cadastro manual ou bypass de validação.
if ($BaseUrl.Scheme -ne 'http' -or $BaseUrl.Host -ne '127.0.0.1' -or
    $BaseUrl.UserInfo -or $BaseUrl.Query -or $BaseUrl.Fragment -or $BaseUrl.AbsolutePath -ne '/') {
    throw 'A carga demonstrativa é restrita à API local http://127.0.0.1:porta.'
}
if ($RestauranteId -eq [Guid]::Empty) { throw 'Informe o UUID local de um restaurante selecionado.' }
$base = $BaseUrl.AbsoluteUri.TrimEnd('/')
$credencial = [pscredential]::new('demonstracao', $Senha)
$senhaTexto = $credencial.GetNetworkCredential().Password
if ($senhaTexto.Length -lt 12 -or $senhaTexto.Length -gt 128) { throw 'Use senha de demonstração entre 12 e 128 caracteres.' }
$sessoes = [Collections.Generic.List[string]]::new()
function Solicitar([string]$Metodo, [string]$Caminho, $Corpo = $null, [hashtable]$Cabecalhos = @{}, [int[]]$Aceitos = @(200)) {
    $parametros = @{
        Uri="$base$Caminho"; Method=$Metodo; Headers=$Cabecalhos
        MaximumRedirection=0; TimeoutSec=15
    }
    $suportados = (Get-Command Invoke-WebRequest).Parameters
    if ($suportados.ContainsKey('SkipHttpErrorCheck')) { $parametros.SkipHttpErrorCheck = $true }
    if ($suportados.ContainsKey('UseBasicParsing')) { $parametros.UseBasicParsing = $true }
    if ($null -ne $Corpo) {
        $parametros.ContentType = 'application/json'
        $parametros.Body = [Text.Encoding]::UTF8.GetBytes(($Corpo | ConvertTo-Json -Depth 8 -Compress))
    }
    $status = 0
    $conteudo = $null
    try {
        $resposta = Invoke-WebRequest @parametros
        $status = [int]$resposta.StatusCode
        $conteudo = $resposta.Content
    }
    catch {
        if ($_.Exception.Response -and $null -ne $_.Exception.Response.StatusCode) {
            $status = [int]$_.Exception.Response.StatusCode
        } else {
            throw "Falha de conexão com a API local em $Metodo $Caminho; credenciais omitidas."
        }
    }
    if ($status -notin $Aceitos) {
        throw "HTTP $status em $Metodo $Caminho. Nenhum dado existente foi sobrescrito. Verifique senha, prefixo e restaurante."
    }
    [pscustomobject]@{
        Status = $status
        Dados = if ($conteudo) { $conteudo | ConvertFrom-Json } else { $null }
    }
}
try {
    $contas = @()
    foreach ($sufixo in @('ana', 'bruno')) {
        $username = "${Prefixo}_$sufixo"
        $cadastro = @{username=$username; email="$username@example.test"; celular='98999999999'; senha=$senhaTexto}
        $null = Solicitar 'POST' '/api/auth/cadastro' $cadastro @{} @(201,409)
        # Em conflito, autenticar impede reutilizar/alterar uma conta alheia.
        $sessao = (Solicitar 'POST' '/api/auth/login' @{identificador=$username; senha=$senhaTexto}).Dados
        $sessoes.Add($sessao.token)
        $headers = @{Authorization="Bearer $($sessao.token)"}
        $conta = (Solicitar 'GET' '/api/usuarios/me' $null $headers).Dados
        $contas += [pscustomobject]@{Id=$conta.id; Username=$conta.username; Headers=$headers}
    }
    $null = Solicitar 'PUT' "/api/usuarios/$($contas[1].Id)/seguimento" $null $contas[0].Headers @(204)
    $exemplos = @(
        @{Autor=0; Chave='meal_1'; Pratos=@(@{nome='Prato demonstrativo A';nota=5.0}); Modalidade='PRESENCIAL'},
        @{Autor=0; Chave='meal_2'; Pratos=@(@{nome='Prato demonstrativo B';nota=3.5},@{nome='Sobremesa demonstrativa';nota=4.5}); Modalidade='DELIVERY'},
        @{Autor=1; Chave='meal_3'; Pratos=@(@{nome='Prato demonstrativo C';nota=4.5}); Modalidade='RETIRADA'}
    )
    $ids = @()
    $arquivoFoto = Get-Item -LiteralPath (Join-Path $PSScriptRoot 'foto-demonstracao.jpg')
    foreach ($exemplo in $exemplos) {
        $conta = $contas[$exemplo.Autor]
        # Todo post tem pelo menos 1 foto (T16); a imagem é fictícia (cor lisa), sem pessoas nem metadados.
        $foto = (Invoke-RestMethod "$base/api/fotos" -Method Post -Headers $conta.Headers -Form @{ arquivo = $arquivoFoto } -TimeoutSec 15)
        $headers = $conta.Headers.Clone()
        # Chave estável: repetir a carga não duplica Meals.
        $headers['Idempotency-Key'] = "${Prefixo}:v1:$RestauranteId`:$($exemplo.Chave)"
        $publicacao = Solicitar 'POST' '/api/meals' @{
            restauranteId=$RestauranteId.ToString(); dishes=$exemplo.Pratos; fotoIds=@($foto.id)
            review='Conteúdo fictício para demonstração interna; não representa avaliação real do estabelecimento.'
            comeriaNovamente=$true; modalidade=$exemplo.Modalidade; dataRefeicao='2026-10-20'
        } $headers @(201, 409)
        if ($publicacao.Status -eq 201) { $ids += $publicacao.Dados.id; continue }
        # 409: a carga já rodou e a chave guarda o post com a foto daquela vez. A foto nova fica solta e some em 24 h.
        $proprias = (Solicitar 'GET' "/api/usuarios/$($conta.Id)/meals?limite=50" $null $conta.Headers).Dados
        $existente = @($proprias.itens | Where-Object { $_.dishes[0].nome -eq $exemplo.Pratos[0].nome }) | Select-Object -First 1
        if (-not $existente) { throw 'Chave da carga já usada com outro conteúdo, sem o post esperado; confira os dados locais.' }
        $ids += $existente.id
    }
    $timeline = (Solicitar 'GET' '/api/timeline?limite=50' $null $contas[0].Headers).Dados
    foreach ($id in $ids) {
        if ($id -notin @($timeline.itens.id)) { throw 'Meal da carga ausente na timeline; confira exclusões e dados locais.' }
    }
    [pscustomobject]@{
        Usuarios = @($contas.Username)
        RestauranteId = $RestauranteId
        Meals = $ids
        QuantidadeMealsDaCarga = $ids.Count
    }
}
finally {
    foreach ($token in $sessoes) {
        try { $null = Solicitar 'POST' '/api/auth/logout' $null @{Authorization="Bearer $token"} @(204) }
        catch { Write-Warning 'Não foi possível revogar uma sessão da carga; ela expira conforme a configuração da API.' }
    }
    $senhaTexto = $null
    $credencial = $null
}
