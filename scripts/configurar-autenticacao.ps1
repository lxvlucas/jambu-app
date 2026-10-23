param([string]$Arquivo = (Join-Path $PSScriptRoot '../.env.local'))
$ErrorActionPreference = 'Stop'
$texto = if (Test-Path -LiteralPath $Arquivo) { [IO.File]::ReadAllText((Resolve-Path -LiteralPath $Arquivo)) } else { '' }
if ($texto -match '(?m)^JAMBU_AUTH_CHAVE_REPETICAO=[ \t]*[^ \t\r\n]+') {
    Write-Output 'Chave de autenticação existente preservada.'
    return
}
$chave = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
if ($texto -match '(?m)^JAMBU_AUTH_CHAVE_REPETICAO=[^\r\n]*') {
    $texto = [regex]::Replace($texto, '(?m)^JAMBU_AUTH_CHAVE_REPETICAO=[^\r\n]*', 'JAMBU_AUTH_CHAVE_REPETICAO=' + $chave)
} else {
    $texto = $texto.TrimEnd() + "`nJAMBU_AUTH_CHAVE_REPETICAO=" + $chave + "`n"
}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($Arquivo), $texto, [Text.UTF8Encoding]::new($false))
Write-Output 'Chave local criada sem exibir o segredo. Preserve o arquivo fora do Git.'
