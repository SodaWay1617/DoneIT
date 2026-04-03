param(
    [Parameter(Mandatory = $true)]
    [string]$Password
)

$mavenRepo = 'target/maven-repo'
$dependencyDir = 'target/password-hash-lib'

mvn "-Dmaven.repo.local=$mavenRepo" -q -DskipTests compile dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=$dependencyDir"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

java -cp "target\classes;target\password-hash-lib\*" com.doneit.user.infrastructure.tools.PasswordHashGenerator $Password
