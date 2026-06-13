[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$headers = @{
    'apikey' = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlsbmhvYXdla2hvbHFsdW1neXRsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODA5ODQxOTIsImV4cCI6MjA5NjU2MDE5Mn0.w-V40lcq0_iBma2o7x01Z9ZjPB2UAoDw2YzhCl0tS90'
    'Authorization' = 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlsbmhvYXdla2hvbHFsdW1neXRsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODA5ODQxOTIsImV4cCI6MjA5NjU2MDE5Mn0.w-V40lcq0_iBma2o7x01Z9ZjPB2UAoDw2YzhCl0tS90'
}

$uri = "https://ylnhoawekholqlumgytl.supabase.co/rest/v1/user_schedules?select=user_code,user_name,shifts,extra_shifts,memos,todos,moods,shift_types&user_code=eq.I33J1S"
$resp = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing
$content = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())

$data = $content | ConvertFrom-Json

$today = (Get-Date).ToString("yyyy-MM-dd")
Write-Output "=== 송도여신 (I33J1S) - 오늘 ($today) 일정 ==="
Write-Output ""

$shifts = $data[0].shifts
$extraShifts = $data[0].extra_shifts
$memos = $data[0].memos
$todos = $data[0].todos
$moods = $data[0].moods
$shiftTypes = $data[0].shift_types

if ($shifts.PSObject.Properties[$today]) {
    $shiftId = $shifts.$today
    Write-Output "주요 근무: $shiftId"
    if ($shiftTypes.PSObject.Properties[$shiftId]) {
        $st = $shiftTypes.$shiftId
        Write-Output "  -> 라벨: $($st.label), 이모지: $($st.emoji), 약자: $($st.short), 시간: $($st.timeRange)"
    }
} else {
    Write-Output "주요 근무: (없음)"
}

if ($extraShifts.PSObject.Properties[$today]) {
    Write-Output "추가 근무: $($extraShifts.$today)"
} else {
    Write-Output "추가 근무: (없음)"
}

if ($memos.PSObject.Properties[$today]) {
    Write-Output "메모: $($memos.$today)"
} else {
    Write-Output "메모: (없음)"
}

if ($todos.PSObject.Properties[$today]) {
    Write-Output "할일: $($todos.$today)"
} else {
    Write-Output "할일: (없음)"
}

if ($moods.PSObject.Properties[$today]) {
    Write-Output "감정: $($moods.$today)"
} else {
    Write-Output "감정: (없음)"
}

Write-Output ""
Write-Output "=== 이번 주 근무 (참고) ==="
$startOfWeek = (Get-Date).AddDays(-([int](Get-Date).DayOfWeek))
for ($i = 0; $i -lt 7; $i++) {
    $d = $startOfWeek.AddDays($i).ToString("yyyy-MM-dd")
    $dayName = $startOfWeek.AddDays($i).ToString("ddd")
    if ($shifts.PSObject.Properties[$d]) {
        $sid = $shifts.$d
        $lbl = $sid
        if ($shiftTypes.PSObject.Properties[$sid]) {
            $lbl = "$($shiftTypes.$sid.emoji) $($shiftTypes.$sid.label)"
        }
        Write-Output "  $d ($dayName): $lbl"
    } else {
        Write-Output "  $d ($dayName): -"
    }
}
