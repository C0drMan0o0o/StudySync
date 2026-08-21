# Add System.Net.Http assembly to make concurrent web requests
Add-Type -AssemblyName System.Net.Http

# Helper function to extract error response body across different PowerShell and .NET versions
function Get-ErrorResponseBody {
    param($ErrorRecord)
    
    if (-not $ErrorRecord -or -not $ErrorRecord.Exception) {
        return $null
    }
    
    $exc = $ErrorRecord.Exception
    
    # Check if it has a Response property (common to WebException and HttpResponseException)
    if ($exc.Response) {
        $response = $exc.Response
        # Check if it is a System.Net.Http.HttpResponseMessage (PowerShell 6+)
        if ($response -is [System.Net.Http.HttpResponseMessage]) {
            try {
                return $response.Content.ReadAsStringAsync().Result
            } catch {}
        }
        # Check if it is a System.Net.WebResponse (PowerShell 5.1)
        elseif ($response -is [System.Net.WebResponse] -or $response.GetType().GetMethod("GetResponseStream")) {
            try {
                $stream = $response.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $body = $reader.ReadToEnd()
                    $reader.Dispose()
                    $stream.Dispose()
                    return $body
                }
            } catch {}
        }
    }
    
    # Fallback to ErrorDetails message
    if ($ErrorRecord.ErrorDetails -and $ErrorRecord.ErrorDetails.Message) {
        return $ErrorRecord.ErrorDetails.Message
    }
    
    return $null
}

# Determine BaseUrl and Concurrency
$BaseUrl = $env:BASE_URL
if ([string]::IsNullOrEmpty($BaseUrl)) {
    $BaseUrl = "https://studysync-backend-a947.onrender.com"
}

$Concurrency = 20
if ($args.Count -ge 1) {
    $tempConcurrency = 0
    if ([int]::TryParse($args[0], [ref]$tempConcurrency)) {
        $Concurrency = $tempConcurrency
    }
} elseif ($env:CONCURRENCY) {
    if ([int]::TryParse($env:CONCURRENCY, [ref]$Concurrency)) {
        # Parsed successfully
    }
}

$RunId = "$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())-$PID"
$Email = "concurrency-test-$RunId@example.com"

Write-Host "== Render Concurrent Booking Test (concurrency=$Concurrency) =="
Write-Host "Target URL: $BaseUrl"

# Wakeup / Polling loop for Render cold start
Write-Host "-- Checking if service is awake (timeout 120s)..."
$MaxWakeupAttempts = 24
$WakeupInterval = 5
$Awake = $false

$HttpClient = New-Object System.Net.Http.HttpClient
$HttpClient.Timeout = [TimeSpan]::FromSeconds(15) # Warmup read timeout

for ($i = 1; $i -le $MaxWakeupAttempts; $i++) {
    try {
        # Check /rooms endpoint. Any HTTP response status indicates server is awake.
        $Task = $HttpClient.GetAsync("$BaseUrl/rooms")
        $null = $Task.Wait(5000) # Connection timeout limit (5s)
        if ($Task.IsCompleted) {
            $Response = $Task.Result
            Write-Host "   Service is awake! (HTTP Status: $([int]$Response.StatusCode))"
            $Awake = $true
            break
        }
    } catch {
        # Silent retry on connection failures / timeouts during cold start
    }
    Write-Host "   Waiting for service to wake up (attempt $i/$MaxWakeupAttempts)..."
    Start-Sleep -Seconds $WakeupInterval
}

$HttpClient.Dispose()

if (-not $Awake) {
    Write-Error "ERROR: Service at $BaseUrl did not wake up within 120 seconds."
    exit 1
}

# Register test user
Write-Host "-- Registering test user $Email"
$RegisterBody = @{
    email    = $Email
    password = "password123"
    name     = "Concurrency Test"
} | ConvertTo-Json -Compress

try {
    $RegisterResponse = Invoke-WebRequest -Uri "$BaseUrl/auth/register" -Method Post -ContentType "application/json" -Body $RegisterBody -UseBasicParsing
} catch {
    Write-Error "Registration failed"
    $errBody = Get-ErrorResponseBody $_
    if ($errBody) {
        Write-Error "Response body: $errBody"
    }
    exit 1
}

if ($RegisterResponse.StatusCode -ne 201) {
    Write-Error "Registration failed (HTTP $($RegisterResponse.StatusCode))"
    exit 1
}

$RegisterObj = $RegisterResponse.Content | ConvertFrom-Json
$Token = $RegisterObj.token

# Create test room
Write-Host "-- Creating test room"
$RoomBody = @{
    name     = "Concurrency Test Room $RunId"
    capacity = 4
    location = "Render Test"
} | ConvertTo-Json -Compress

$RoomHeaders = @{
    "Authorization" = "Bearer $Token"
}

try {
    $RoomResponse = Invoke-WebRequest -Uri "$BaseUrl/rooms" -Method Post -ContentType "application/json" -Headers $RoomHeaders -Body $RoomBody -UseBasicParsing
} catch {
    Write-Error "Room creation failed"
    $errBody = Get-ErrorResponseBody $_
    if ($errBody) {
        Write-Error "Response body: $errBody"
    }
    exit 1
}

if ($RoomResponse.StatusCode -ne 201) {
    Write-Error "Room creation failed (HTTP $($RoomResponse.StatusCode))"
    exit 1
}

$RoomObj = $RoomResponse.Content | ConvertFrom-Json
$RoomId = $RoomObj.id
Write-Host "-- Room id: $RoomId"

# Pick a slot that's different on every run
$Start = (Get-Date).AddDays(1).AddMinutes((Get-Random -Minimum 0 -Maximum 100000))
$Start = [DateTime]::new($Start.Year, $Start.Month, $Start.Day, $Start.Hour, $Start.Minute, 0, 0)
$StartTime = $Start.ToString("yyyy-MM-ddTHH:mm:ss", [System.Globalization.CultureInfo]::InvariantCulture)
$EndTime = $Start.AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss", [System.Globalization.CultureInfo]::InvariantCulture)

Write-Host "-- Slot: $StartTime -> $EndTime"

$BookingBody = @{
    roomId    = [int]$RoomId
    startTime = $StartTime
    endTime   = $EndTime
} | ConvertTo-Json -Compress

Write-Host "-- Firing $Concurrency concurrent identical booking requests..."

$HttpClient = New-Object System.Net.Http.HttpClient
$HttpClient.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token)

$Tasks = New-Object System.Collections.Generic.List[System.Threading.Tasks.Task]

for ($i = 1; $i -le $Concurrency; $i++) {
    $Content = New-Object System.Net.Http.StringContent($BookingBody, [System.Text.Encoding]::UTF8, "application/json")
    $Task = $HttpClient.PostAsync("$BaseUrl/bookings", $Content)
    $Tasks.Add($Task)
}

try {
    [System.Threading.Tasks.Task]::WaitAll($Tasks)
} catch {
    # Some tasks may have failed, but we handle status in the loop below
}

$SuccessCount = 0
$ConflictCount = 0
$OtherCount = 0
$UnexpectedResults = New-Object System.Collections.Generic.List[PSCustomObject]

for ($i = 0; $i -lt $Concurrency; $i++) {
    $Task = $Tasks[$i]
    $ReqIdx = $i + 1
    
    if ($Task.Status -eq "RanToCompletion") {
        $Response = $Task.Result
        $StatusCode = [int]$Response.StatusCode
        $Body = $Response.Content.ReadAsStringAsync().Result
        
        if ($StatusCode -eq 201) {
            $SuccessCount++
        } elseif ($StatusCode -eq 409) {
            $ConflictCount++
        } else {
            $OtherCount++
            $UnexpectedResults.Add([PSCustomObject]@{
                Index      = $ReqIdx
                StatusCode = $StatusCode
                Body       = $Body
            })
        }
    } else {
        $OtherCount++
        $ExceptionMessage = if ($Task.Exception) {
            if ($Task.Exception.InnerException) {
                $Task.Exception.InnerException.Message
            } else {
                $Task.Exception.Message
            }
        } else {
            "Task status: $($Task.Status)"
        }
        $UnexpectedResults.Add([PSCustomObject]@{
            Index      = $ReqIdx
            StatusCode = 0
            Body       = "Request Exception: $ExceptionMessage"
        })
    }
}

$HttpClient.Dispose()

Write-Host ""
Write-Host "== Results =="
Write-Host "201 Created : $SuccessCount"
Write-Host "409 Conflict: $ConflictCount"
Write-Host "Other       : $OtherCount"

if ($OtherCount -gt 0) {
    Write-Host ""
    Write-Host "Unexpected status codes seen:"
    foreach ($Result in $UnexpectedResults) {
        Write-Host "  request $($Result.Index) -> $($Result.StatusCode):"
        $Result.Body -split "`n" | ForEach-Object { Write-Host "    $_" }
    }
}

Write-Host ""
if ($SuccessCount -eq 1 -and $ConflictCount -eq ($Concurrency - 1)) {
    Write-Host "PASS: exactly one booking succeeded, the rest were rejected as conflicts."
    Write-Host "The DB exclusion constraint held under concurrency."
    exit 0
} else {
    Write-Host "FAIL: expected exactly 1 success and $($Concurrency - 1) conflicts."
    Write-Host "If SUCCESS_COUNT > 1, the DB constraint did NOT prevent a double-booking."
    exit 1
}
