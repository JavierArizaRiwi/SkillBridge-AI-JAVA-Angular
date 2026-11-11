param([string]$Base = "http://localhost:8080")

$ErrorActionPreference = "Stop"
$script:fails = 0

function Call($Method, $Path, $Token, $Body) {
  $headers = @{}
  if ($Token) { $headers["Authorization"] = "Bearer $Token" }
  $req = @{ Method = $Method; Uri = "$Base$Path"; Headers = $headers; UseBasicParsing = $true }
  if ($Body) {
    if ($Body -is [string]) { $req["Body"] = $Body } else { $req["Body"] = ($Body | ConvertTo-Json) }
    $req["ContentType"] = "application/json"
  }
  try {
    $r = Invoke-WebRequest @req
    $json = $null
    if ($r.Content) { $json = $r.Content | ConvertFrom-Json }
    return @{ Status = [int]$r.StatusCode; Json = $json; Raw = $r.Content }
  } catch {
    if ($_.Exception.Response) { return @{ Status = [int]$_.Exception.Response.StatusCode; Json = $null; Raw = "" } }
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

function UserByEmail($Items, $Email) { $Items | Where-Object { $_.email -eq $Email } }

Write-Host "== E2E administracion de usuarios contra $Base ==" -ForegroundColor Cyan

Check "backend healthy" (Call "GET" "/actuator/health" $null).Status 200
$customer = Login "user@gmail.com"
$provider = Login "provider@gmail.com"
$admin    = Login "admin@gmail.com"
$customerId = $null

try {
  # 1. Sin sesion y sin permiso
  Check "sin token: listar es 401"          (Call "GET" "/api/admin/users" $null).Status 401
  Check "customer listar es 403"            (Call "GET" "/api/admin/users" $customer).Status 403
  Check "provider listar es 403"            (Call "GET" "/api/admin/users" $provider).Status 403

  # 2. Admin lista
  $list = Call "GET" "/api/admin/users?size=50" $admin
  Check "admin lista usuarios"              $list.Status 200
  $items = @($list.Json.items)
  $customerId = (UserByEmail $items "user@gmail.com").id
  $adminId    = (UserByEmail $items "admin@gmail.com").id
  Check "la respuesta no expone passwordHash" ($list.Raw -match "passwordHash") $false

  Check "customer cambiar rol es 403"       (Call "PUT" "/api/admin/users/$customerId/role" $customer @{ role = "ADMIN" }).Status 403
  Check "provider suspender es 403"         (Call "PUT" "/api/admin/users/$customerId/status" $provider @{ status = "SUSPENDED" }).Status 403

  # 3. Paginacion y orden
  $p = Call "GET" "/api/admin/users?size=1&sort=email,asc" $admin
  Check "paginacion: 1 por pagina"          @($p.Json.items).Count 1
  Check "orden por correo ascendente"       $p.Json.items[0].email "admin@gmail.com"
  Check "hay varias paginas"                ($p.Json.totalPages -ge 3) $true
  Check "orden por campo no permitido es 400" (Call "GET" "/api/admin/users?sort=passwordHash,asc" $admin).Status 400
  Check "tamano de pagina excesivo es 400"  (Call "GET" "/api/admin/users?size=1000" $admin).Status 400

  # 4. Detalle
  $d = Call "GET" "/api/admin/users/$customerId" $admin
  Check "detalle de usuario"                $d.Status 200
  Check "detalle trae el correo correcto"   $d.Json.email "user@gmail.com"
  Check "detalle inexistente es 404"        (Call "GET" "/api/admin/users/$([guid]::NewGuid())" $admin).Status 404

  # 5. Reglas de negocio y validaciones
  Check "admin no puede suspenderse (422)"  (Call "PUT" "/api/admin/users/$adminId/status" $admin @{ status = "SUSPENDED" }).Status 422
  Check "admin no puede cambiar su rol (422)" (Call "PUT" "/api/admin/users/$adminId/role" $admin @{ role = "CUSTOMER" }).Status 422
  Check "mismo estado es 422"               (Call "PUT" "/api/admin/users/$customerId/status" $admin @{ status = "ACTIVE" }).Status 422
  Check "mismo rol es 422"                  (Call "PUT" "/api/admin/users/$customerId/role" $admin @{ role = "CUSTOMER" }).Status 422
  Check "estado invalido es 400"            (Call "PUT" "/api/admin/users/$customerId/status" $admin '{"status":"BANNED"}').Status 400
  Check "rol ausente es 400"                (Call "PUT" "/api/admin/users/$customerId/role" $admin '{}').Status 400

  # 6. Suspension: bloquea login y deja sin efecto el token ya emitido
  Check "antes: token del customer funciona (403 por rol)" (Call "GET" "/api/offerings/me" $customer).Status 403
  $s = Call "PUT" "/api/admin/users/$customerId/status" $admin @{ status = "SUSPENDED" }
  Check "admin suspende al customer"        $s.Status 200
  Check "el estado quedo SUSPENDED"         $s.Json.status "SUSPENDED"
  Check "login de cuenta suspendida es 403" (Call "POST" "/api/auth/login" $null @{ email = "user@gmail.com"; password = "12345678" }).Status 403
  Check "su token deja de servir (401)"     (Call "GET" "/api/offerings/me" $customer).Status 401
  Check "admin reactiva la cuenta"          (Call "PUT" "/api/admin/users/$customerId/status" $admin @{ status = "ACTIVE" }).Status 200
  Check "puede iniciar sesion otra vez"     (Call "POST" "/api/auth/login" $null @{ email = "user@gmail.com"; password = "12345678" }).Status 200
  Check "su token vuelve a servir (403 por rol)" (Call "GET" "/api/offerings/me" $customer).Status 403

  # 7. Cambio de rol: inmediato en el backend
  Check "admin sube al customer a PROVIDER" (Call "PUT" "/api/admin/users/$customerId/role" $admin @{ role = "PROVIDER" }).Status 200
  Check "rol inmediato: el token viejo ya es PROVIDER" (Call "GET" "/api/offerings/me" $customer).Status 200
  Check "admin lo devuelve a CUSTOMER"      (Call "PUT" "/api/admin/users/$customerId/role" $admin @{ role = "CUSTOMER" }).Status 200
  Check "vuelve a ser 403"                  (Call "GET" "/api/offerings/me" $customer).Status 403
}
finally {
  # Deja a user@gmail.com como estaba, aunque el script se corte a la mitad
  if ($customerId) {
    Call "PUT" "/api/admin/users/$customerId/status" $admin @{ status = "ACTIVE" } | Out-Null
    Call "PUT" "/api/admin/users/$customerId/role" $admin @{ role = "CUSTOMER" } | Out-Null
  }
}

Write-Host ""
if ($script:fails -eq 0) { Write-Host "E2E VERDE" -ForegroundColor Green } else { Write-Host "E2E con $($script:fails) fallos" -ForegroundColor Red }
exit $script:fails
