$f = "src\test\java\com\neelastack\integration\AuthorizationIntegrationTest.java"
$p = Get-Content $f -Raw

# Add JwtService import
if ($p -notmatch "import com\.neelastack\.security\.JwtService;") {
    $p = $p.Replace(
        "import com.neelastack.security.CurrentUserProvider;",
        "import com.neelastack.security.CurrentUserProvider;`r`nimport com.neelastack.security.JwtService;"
    )
}

# Add JwtService bean
if ($p -notmatch "private JwtService jwtService;") {
    $p = $p.Replace(
        "    @Autowired`r`n    private PasswordEncoder passwordEncoder;",
        "    @Autowired`r`n    private PasswordEncoder passwordEncoder;`r`n`r`n    @Autowired`r`n    private JwtService jwtService;"
    )
}

# Add direct JWT helper
if ($p -notmatch "private String issueAccessToken") {
    $marker = "    private String loginAndGetAccessToken(String email) throws Exception {"
    $helper = @"
    private String issueAccessToken(User user) {
        return jwtService.generateAccessToken(user, 15);
    }

"@
    $p = $p.Replace($marker, $helper + $marker)
}

# Replace only the two admin authorization test login calls
$p = $p.Replace(
    '        String token = loginAndGetAccessToken("real-admin@example.com");',
    '        User admin = userRepository.findByEmail("real-admin@example.com").orElseThrow();`r`n        String token = issueAccessToken(admin);'
)

$p = $p.Replace(
    '        String adminToken = loginAndGetAccessToken("admin-checking-in@example.com");',
    '        User admin = userRepository.findByEmail("admin-checking-in@example.com").orElseThrow();`r`n        String adminToken = issueAccessToken(admin);'
)

Set-Content -Path $f -Value $p -NoNewline
Write-Host "AuthorizationIntegrationTest updated successfully."
