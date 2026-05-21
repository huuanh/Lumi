param(
    [string]$Device = "emulator-5554",
    [string]$Package = "com.lumi.retouch",
    [string]$Activity = "com.lumi.retouch/.MainActivity",
    [string]$Apk = ".\app\build\outputs\apk\debug\app-debug.apk",
    [string]$Screenshot = ".\ld_smoke.png"
)

$ErrorActionPreference = "Stop"

function Wait-ForText {
    param(
        [string]$Text,
        [int]$TimeoutSeconds = 12
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $oldPreference = $ErrorActionPreference
        $ErrorActionPreference = "SilentlyContinue"
        adb -s $Device shell uiautomator dump /sdcard/window.xml 2>$null | Out-Null
        adb -s $Device pull /sdcard/window.xml .\ld_window.xml 2>$null | Out-Null
        $ErrorActionPreference = $oldPreference
        if (Select-String -Path .\ld_window.xml -Pattern "text=`"$Text`"" -Quiet) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Wait-ForPattern {
    param(
        [string]$Pattern,
        [int]$TimeoutSeconds = 20
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $oldPreference = $ErrorActionPreference
        $ErrorActionPreference = "SilentlyContinue"
        adb -s $Device shell uiautomator dump /sdcard/window.xml 2>$null | Out-Null
        adb -s $Device pull /sdcard/window.xml .\ld_window.xml 2>$null | Out-Null
        $ErrorActionPreference = $oldPreference
        if (Select-String -Path .\ld_window.xml -Pattern $Pattern -Quiet) {
            return $true
        }
        Start-Sleep -Milliseconds 700
    }
    return $false
}

adb -s $Device install -r $Apk
adb -s $Device shell am force-stop $Package
adb -s $Device shell am start -n $Activity | Out-Null

if (-not (Wait-ForText -Text "Sample" -TimeoutSeconds 15)) {
    throw "Smoke failed: Sample button did not appear."
}

adb -s $Device shell input tap 480 685

if (-not (Wait-ForPattern -Pattern "1 mesh face|No face detected|face detected" -TimeoutSeconds 30)) {
    throw "Smoke failed: face scan did not finish."
}

adb -s $Device shell screencap -p /sdcard/ld_smoke.png
$oldPreference = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
adb -s $Device pull /sdcard/ld_smoke.png $Screenshot 2>$null | Out-Null
$ErrorActionPreference = $oldPreference

$focus = adb -s $Device shell dumpsys window | Select-String -Pattern "mCurrentFocus|mFocusedApp"
if (-not ($focus -match [regex]::Escape($Package))) {
    throw "Smoke failed: app is not foreground."
}

Write-Host "LD smoke passed: $Screenshot"
