param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Requests = 50
)

# Proximo dia em que a Dra. Helena (PRO2) atende, a pelo menos 3 dias de hoje
$date = (Get-Date).Date.AddDays(3)
while ($date.DayOfWeek -in "Sunday", "Monday") { $date = $date.AddDays(1) }
$startsAt = $date.ToString("yyyy-MM-dd") + "T15:00"

Write-Host "Disparando $Requests pedidos em paralelo: PRO2, consulta, $startsAt"

[Net.ServicePointManager]::DefaultConnectionLimit = 200
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient

$tasks = foreach ($i in 1..$Requests) {
    $json = '{"professionalId":"PRO2","serviceId":"S1","startsAt":"' + $startsAt + '","patientName":"Paciente ' + $i + '"}'
    $body = New-Object System.Net.Http.StringContent($json, [Text.Encoding]::UTF8, "application/json")
    $client.PostAsync("$BaseUrl/bookings", $body)
}
[void][System.Threading.Tasks.Task]::WaitAll($tasks)

$created = @($tasks | Where-Object { [int]$_.Result.StatusCode -eq 201 }).Count
$conflicts = @($tasks | Where-Object {
    [int]$_.Result.StatusCode -eq 409 -and $_.Result.Content.ReadAsStringAsync().Result -match '"codigo":"CONFLITO"'
}).Count

Write-Host ""
Write-Host ("201 Created : {0}" -f $created)
Write-Host ("409 CONFLITO: {0}" -f $conflicts)
Write-Host ""
Write-Host "Resultado: $created reserva criada, $conflicts recusadas pelo banco."
