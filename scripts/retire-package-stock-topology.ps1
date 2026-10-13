param(
    [string]$ManagementBaseUrl = 'http://127.0.0.1:15672',
    [string]$Username = 'guest',
    [string]$Password = 'guest',
    [string]$VirtualHost = '/'
)

$ErrorActionPreference = 'Stop'
$taskAuth = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${Username}:${Password}"))
$taskHeaders = @{ Authorization = "Basic $taskAuth" }
$taskVhost = [Uri]::EscapeDataString($VirtualHost)
$taskBase = $ManagementBaseUrl.TrimEnd('/')
$taskExchange = 'package_stock_topic'
$taskTags = @('stock.occupied', 'stock.released', 'stock.sold.confirmed', 'stock.rollback', 'package.changed')
$taskQueues = @('food.business.search.sync.queue', 'food.business.package.stock.event.queue')
$taskBindings = Invoke-RestMethod -Uri "$taskBase/api/bindings/$taskVhost" -Headers $taskHeaders
foreach ($taskBinding in $taskBindings) {
    if ($taskBinding.source -eq $taskExchange -and $taskBinding.destination_type -eq 'queue' `
            -and $taskBinding.destination -in $taskQueues -and $taskBinding.routing_key -in $taskTags) {
        $taskDestination = [Uri]::EscapeDataString($taskBinding.destination)
        $taskProperties = [Uri]::EscapeDataString($taskBinding.properties_key)
        Invoke-RestMethod -Method Delete -Uri "$taskBase/api/bindings/$taskVhost/e/$taskExchange/q/$taskDestination/$taskProperties" -Headers $taskHeaders | Out-Null
        Write-Output "Removed retired binding: $($taskBinding.routing_key) -> $($taskBinding.destination)"
    }
}

# Delete only empty, unused legacy queues. Never purge pending audit messages.
foreach ($taskQueue in @('food.business.package.stock.event.queue', 'food.business.package.stock.event.dlq')) {
    try {
        $taskInfo = Invoke-RestMethod -Uri "$taskBase/api/queues/$taskVhost/$taskQueue" -Headers $taskHeaders
        if ($taskInfo.messages -gt 0 -or $taskInfo.consumers -gt 0) {
            Write-Output "Retained legacy queue with messages or consumers: $taskQueue"
            continue
        }
        Invoke-RestMethod -Method Delete -Uri "$taskBase/api/queues/$taskVhost/${taskQueue}?if-empty=true&if-unused=true" -Headers $taskHeaders | Out-Null
        Write-Output "Removed empty unused queue: $taskQueue"
    } catch {
        if ([int]$_.Exception.Response.StatusCode -ne 404) { throw }
    }
}

try {
    # Preserve any unknown external binding rather than force-delete the exchange.
    Invoke-RestMethod -Method Delete -Uri "$taskBase/api/exchanges/$taskVhost/${taskExchange}?if-unused=true" -Headers $taskHeaders | Out-Null
    Write-Output "Removed unused exchange: $taskExchange"
} catch {
    if ([int]$_.Exception.Response.StatusCode -notin @(400, 404)) { throw }
}
