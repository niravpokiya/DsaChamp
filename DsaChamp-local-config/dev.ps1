[CmdletBinding(DefaultParameterSetName = 'Setup')]
param(
    [Parameter(ParameterSetName = 'Setup')]
    [switch]$RebuildRunners,

    [Parameter(Mandatory, ParameterSetName = 'Run')]
    [ValidateSet('service-registry', 'api-gateway', 'User-service', 'Question-service',
                 'Execution-Submission-Service', 'Execution-worker-Service', 'frontend')]
    [string]$Service
)

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot

function Invoke-Checked {
    param([scriptblock]$Command)
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed (exit code $LASTEXITCODE): $Command"
    }
}

if (-not $Service) {
    $composeFile = Join-Path $PSScriptRoot 'docker-compose.yml'
    Invoke-Checked { docker compose -f $composeFile --profile local-db up -d --wait --wait-timeout 180 }

    foreach ($runner in @('cpp-runner', 'java-runner', 'python-runner')) {
        if (-not $RebuildRunners) {
            docker image inspect $runner 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { continue }
        }
        $runnerDir = Join-Path $PSScriptRoot $runner
        Invoke-Checked { docker build -t $runner $runnerDir }
    }
    Write-Host 'Local dependencies and runner images are ready.'
    return
}

if ($Service -eq 'frontend') {
    $previousApiUrl = $env:VITE_API_BASE_URL
    Push-Location (Join-Path $projectDir 'frontend')
    try {
        if (-not $env:VITE_API_BASE_URL) {
            $env:VITE_API_BASE_URL = 'http://localhost:8080/api'
        }
        if (-not (Test-Path 'node_modules/.bin/vite.cmd')) {
            Invoke-Checked { npm.cmd ci }
        }
        Invoke-Checked { npm.cmd run dev -- --strictPort }
    } finally {
        $env:VITE_API_BASE_URL = $previousApiUrl
        Pop-Location
    }
    return
}

$localSettings = @{
    'spring.application.name' = $Service
    'eureka.client.service-url.defaultZone' = 'http://localhost:8761/eureka/'
    'eureka.instance.hostname' = 'localhost'
    'eureka.instance.prefer-ip-address' = $false
    'spring.data.redis.host' = 'localhost'
    'spring.data.redis.port' = 6379
    'spring.kafka.bootstrap-servers' = 'localhost:9092'
}

$localJwt = $env:JWT_SECRET_KEY
if (-not $localJwt) { $localJwt = 'dsachamp-loopback-development-key-only' }
$testcaseDir = Join-Path $projectDir 'backend/testcases'
switch ($Service) {
    'service-registry' {
        $localSettings['server.port'] = 8761
        $localSettings['eureka.client.fetch-registry'] = $false
        $localSettings['eureka.client.register-with-eureka'] = $false
    }
    'api-gateway' {
        $localSettings['server.port'] = 8080
        $localSettings['jwt.secret'] = $localJwt
    }
    'User-service' {
        $localSettings['server.port'] = 8084
        $localSettings['spring.datasource.url'] = 'jdbc:postgresql://localhost:5432/dsachamp_userdb'
        $localSettings['spring.datasource.username'] = 'dsachamp'
        $localSettings['spring.datasource.password'] = 'dsachamp-local'
        $localSettings['spring.jpa.hibernate.ddl-auto'] = 'update'
        $localSettings['application.security.jwt.secretKey'] = $localJwt
    }
    'Question-service' {
        $localSettings['spring.application.name'] = 'question-service'
        $localSettings['server.port'] = 8081
        $localSettings['spring.data.mongodb.uri'] = 'mongodb://localhost:27017/question-service'
        $localSettings['spring.data.mongodb.database'] = 'question-service'
        $localSettings['spring.jackson.mapper.accept-case-insensitive-enums'] = $true
        $localSettings['testcase.storage.path'] = $testcaseDir
    }
    'Execution-Submission-Service' {
        $localSettings['server.port'] = 8092
        $localSettings['spring.data.mongodb.uri'] = 'mongodb://localhost:27017/execution-submissions'
        $localSettings['spring.data.mongodb.auto-index-creation'] = $true
        $localSettings['spring.kafka.producer.value-serializer'] = 'org.springframework.kafka.support.serializer.JsonSerializer'
    }
    'Execution-worker-Service' {
        $localSettings['server.port'] = 8098
        $localSettings['testcase.storage.path'] = $testcaseDir
        $localSettings['spring.kafka.consumer.group-id'] = 'executor-group'
        $localSettings['spring.kafka.consumer.auto-offset-reset'] = 'earliest'
        $localSettings['spring.kafka.consumer.value-deserializer'] = 'org.springframework.kafka.support.serializer.JsonDeserializer'
        $localSettings['spring.kafka.consumer.properties.spring.json.use.type.headers'] = $false
        $localSettings['spring.kafka.consumer.properties.spring.json.value.default.type'] = 'com.DsaDude.Execution_worker_Service.DTO.ExecutionJob'
    }
}

$localSettings['server.address'] = '127.0.0.1'
$previousSpringConfig = $env:SPRING_APPLICATION_JSON
Push-Location (Join-Path $projectDir "backend/$Service")
try {
    $env:SPRING_APPLICATION_JSON = $localSettings | ConvertTo-Json -Compress
    Invoke-Checked { .\mvnw.cmd spring-boot:run }
} finally {
    $env:SPRING_APPLICATION_JSON = $previousSpringConfig
    Pop-Location
}
