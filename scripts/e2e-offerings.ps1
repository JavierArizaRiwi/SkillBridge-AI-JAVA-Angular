param([string]$Base = "http://localhost:8080")

$ErrorActionPreference = "Stop"
$script:fails = 0

function Call($Method, $Path, $Token, $Body) {
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $req = @{ Method = $Method; Uri = "$Base$Path"; Headers = $headers; UseBasicParsing = $true }
    if ($Body) { $req["Body"] = ($Body | ConvertTo-Json); $req["ContentType"] = "application/json" }
    try {
        $r = Invoke-WebRequest @req
        $json = $null
        if ($r.Content) { $json = $r.Content | ConvertFrom-Json }
        return @{ Status = [int]$r.StatusCode; Json = $json }
    } catch {
        if ($_.Exception.Response) { return @{ Status = [int]$_.Exception.Response.StatusCode; Json = $null } }
        throw
    }
}

function Check($Name, $Actual, $Expected) {
    if ($Actual -eq $Expected) {
        Write-Host "[OK]   $Name ($Actual)" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] $Name -> esperado $Expected, obtenido $Actual" -ForegroundColor Red
        $script:fails++
    }
}

function Login($Email) {
    (Call "POST" "/api/auth/login" $null @{ email = $Email; password = "12345678" }).Json.token
}

function InCatalog($Id) {
    $items = @((Call "GET" "/api/offerings" $null).Json)
    return [bool]($items | Where-Object { $_.id -eq $Id })
}

Write-Host "== E2E offerings contra $Base ==" -ForegroundColor Cyan

# 0. Backend arriba y sesiones
Check "backend healthy" (Call "GET" "/actuator/health" $null).Status 200
$customer = Login "user@gmail.com"
$provider = Login "provider@gmail.com"
$admin    = Login "admin@gmail.com"

# 1. Sin autenticar
Check "catalogo publico sin token"        (Call "GET"  "/api/offerings" $null).Status 200
Check "sin token: /me es 401"             (Call "GET"  "/api/offerings/me" $null).Status 401
Check "sin token: crear es 401"           (Call "POST" "/api/offerings" $null @{ title="x"; description="x"; category="X"; price=1 }).Status 401

# 2. Customer no puede crear
Check "customer crear es 403"             (Call "POST" "/api/offerings" $customer @{ title="x"; description="x"; category="X"; price=1 }).Status 403

# 3. Provider crea y el catalogo se actualiza (cache invalidada)
$title = "E2E " + (Get-Date -Format "HHmmss")
$created = Call "POST" "/api/offerings" $provider @{ title=$title; description="flujo e2e"; category="BACKEND"; price=100 }
Check "provider crea offering"            $created.Status 201
$id = $created.Json.id
Check "catalogo refleja el nuevo"         (InCatalog $id) $true

$mine = @((Call "GET" "/api/offerings/me" $provider).Json)
Check "provider lo ve en /me"             ([bool]($mine | Where-Object { $_.id -eq $id })) $true

# 4. Provider actualiza
$upd = Call "PUT" "/api/offerings/$id" $provider @{ title="$title (v2)"; description="flujo e2e"; category="BACKEND"; price=150 }
Check "provider actualiza"                $upd.Status 200
$cat = @((Call "GET" "/api/offerings" $null).Json) | Where-Object { $_.id -eq $id }
Check "catalogo muestra titulo nuevo"     $cat.title "$title (v2)"

# 5. ID manipulado / sin permiso
Check "customer PUT ajeno es 403"         (Call "PUT"  "/api/offerings/$id" $customer @{ title="hack"; description="x"; category="X"; price=1 }).Status 403
Check "customer desactivar ajeno es 403" (Call "POST" "/api/offerings/$id/deactivate" $customer).Status 403
Check "id inexistente es 404"             (Call "PUT"  "/api/offerings/$([guid]::NewGuid())" $provider @{ title="x"; description="x"; category="X"; price=1 }).Status 404
Check "offering sigue activo tras intentos ajenos" (InCatalog $id) $true

# 6. Provider desactiva y reactiva lo suyo
Check "provider desactiva"                (Call "POST" "/api/offerings/$id/deactivate" $provider).Status 204
Check "ya no esta en el catalogo"         (InCatalog $id) $false
Check "provider reactiva"                 (Call "POST" "/api/offerings/$id/activate" $provider).Status 204
Check "vuelve al catalogo"                (InCatalog $id) $true

# 7. Admin puede todo
Check "admin desactiva cualquiera"        (Call "POST" "/api/offerings/$id/deactivate" $admin).Status 204
Check "admin: ya no esta en el catalogo"  (InCatalog $id) $false
Check "admin reactiva cualquiera"         (Call "POST" "/api/offerings/$id/activate" $admin).Status 204
$all = @((Call "GET" "/api/admin/offerings" $admin).Json)
Check "admin lista todos e incluye el e2e" ([bool]($all | Where-Object { $_.id -eq $id })) $true
Check "provider en endpoint admin es 403" (Call "GET" "/api/admin/offerings" $provider).Status 403

# 8. Limpieza: deja el offering de prueba inactivo para no ensuciar el catalogo
Check "limpieza: desactiva el de prueba"  (Call "POST" "/api/offerings/$id/deactivate" $provider).Status 204

Write-Host ""
if ($script:fails -eq 0) { Write-Host "E2E VERDE" -ForegroundColor Green } else { Write-Host "E2E con $($script:fails) fallos" -ForegroundColor Red }
exit $script:fails
