$ErrorActionPreference = "Stop"

$endpoints = @(
    @{ Name = "Gateway"; Url = "http://127.0.0.1:8080/health" },
    @{ Name = "Gateway Actuator"; Url = "http://127.0.0.1:8081/actuator/health" },
    @{ Name = "User Service"; Url = "http://127.0.0.1:8101/health" },
    @{ Name = "User Actuator"; Url = "http://127.0.0.1:8102/actuator/health" },
    @{ Name = "Business Service"; Url = "http://127.0.0.1:8201/health" },
    @{ Name = "Business Actuator"; Url = "http://127.0.0.1:8202/actuator/health" },
    @{ Name = "Trade Service"; Url = "http://127.0.0.1:8301/health" },
    @{ Name = "Trade Actuator"; Url = "http://127.0.0.1:8302/actuator/health" },
    @{ Name = "Nacos Console"; Url = "http://127.0.0.1:8848/nacos" },
    @{ Name = "RabbitMQ Management"; Url = "http://127.0.0.1:15672" },
    @{ Name = "Sentinel Dashboard"; Url = "http://127.0.0.1:8858" },
    @{ Name = "Frontend"; Url = "http://127.0.0.1:5173" }
)

$endpoints | ForEach-Object {
    [pscustomobject]@{
        Name = $_.Name
        Url = $_.Url
    }
} | Format-Table -AutoSize
