$files = @{
    "src\test\java\com\neelastack\service\ChangeRequestServiceTest.java" = @(
        "projectActivityService`r`n        );",
        "projectActivityService`n        );"
    )
    "src\test\java\com\neelastack\service\InvoiceServiceTest.java" = @(
        "currentUserProvider`r`n    );",
        "currentUserProvider`n    );"
    )
    "src\test\java\com\neelastack\service\MilestoneApprovalServiceTest.java" = @(
        "projectActivityService`r`n        );",
        "projectActivityService`n        );"
    )
    "src\test\java\com\neelastack\service\ProjectTaskServiceTest.java" = @(
        "projectActivityService`r`n        );",
        "projectActivityService`n        );"
    )
}

foreach ($file in $files.Keys) {
    $path = Join-Path (Get-Location) $file
    $text = Get-Content $path -Raw

    if ($text -notmatch "import com\.neelastack\.service\.NotificationService;") {
        $marker = "import com.neelastack.service."
        $pos = $text.IndexOf($marker)
        if ($pos -lt 0) { throw "Could not find service imports in $file" }

        $lineEnd = $text.IndexOf("`n", $pos)
        $text = $text.Insert($lineEnd + 1, "import com.neelastack.service.NotificationService;`r`n")
    }

    if ($text -notmatch "private NotificationService notificationService;") {
        $text = $text.Replace(
            "    @Mock`r`n",
            "    @Mock`r`n    private NotificationService notificationService;`r`n`r`n",
            1
        )
    }

    $changed = $false

    foreach ($snippet in $files[$file]) {
        if ($text.Contains($snippet)) {
            $replacement = $snippet.Replace("projectActivityService", "projectActivityService,`r`n            notificationService").Replace("currentUserProvider", "currentUserProvider,`r`n            notificationService")
            $text = $text.Replace($snippet, $replacement)
            $changed = $true
            break
        }
    }

    if (-not $changed) {
        Write-Host "Constructor pattern not automatically matched in $file"
    }

    Set-Content -Path $path -Value $text -NoNewline
}

mvn clean test *> test-output.txt
Get-Content test-output.txt -Tail 150
