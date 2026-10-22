param(
    [Parameter(Mandatory)][string]$Arquivo,
    [Parameter(Mandatory)][ValidatePattern('^\d{4}-\d{2}-\d{2}\.\d+$')][string]$Release,
    [Parameter(Mandatory)][ValidateSet('CDLA-Permissive-2.0','Apache-2.0','CC0-1.0')][string[]]$Licencas,
    [Parameter(Mandatory)][Security.SecureString]$Token,
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)
$ErrorActionPreference = 'Stop'
$destino = [Uri]$BaseUrl
if ($destino.Scheme -ne 'http' -or $destino.Host -ne '127.0.0.1' -or $destino.UserInfo -or $destino.Query -or $destino.Fragment -or $destino.AbsolutePath -ne '/') {
    throw 'Este comando de desenvolvimento aceita somente http://127.0.0.1:porta.'
}
$arquivoResolvido = (Resolve-Path -LiteralPath $Arquivo).Path
if ((Get-Item -LiteralPath $arquivoResolvido).Length -gt 32MB) { throw 'Recorte o GeoJSON para no máximo 32 MiB por arquivo.' }
$geo = Get-Content -LiteralPath $arquivoResolvido -Raw -Encoding UTF8 | ConvertFrom-Json
if ($geo.type -ne 'FeatureCollection' -or -not $geo.features) { throw 'Informe um GeoJSON FeatureCollection original do Overture.' }
$ponteiro = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Token)
try {
    $textoToken = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ponteiro)
    $headers = @{ Authorization = "Bearer $textoToken" }
    $features = @($geo.features)
    for ($inicio = 0; $inicio -lt $features.Count; $inicio += 100) {
        $fim = [Math]::Min($inicio + 99, $features.Count - 1)
        $lote = @{ release = $Release; licencas = @($Licencas); features = @($features[$inicio..$fim]) } | ConvertTo-Json -Depth 40 -Compress
        $parametros = @{
            Uri = "$($BaseUrl.TrimEnd('/'))/api/moderacao/restaurantes/importacoes/overture"
            Method = 'Post'
            ContentType = 'application/json; charset=utf-8'
            Headers = $headers
            Body = [Text.Encoding]::UTF8.GetBytes($lote)
        }
        $resultado = Invoke-RestMethod @parametros
        $resultado.itens | Group-Object resultado | Select-Object Name,Count
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ponteiro)
    $textoToken = $null
    $headers = $null
}
