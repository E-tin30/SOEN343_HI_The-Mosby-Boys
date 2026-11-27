# Metrics: SonarQube Exports

One folder per measurement, each scanned at its tag with the same server, scanner and settings:

| Folder | Tag |
|---|---|
| `phase3/` | `phase3` |
| `phase4-refactored/` | `phase4-refactored` |
| `phase4/` | `phase4` |

Each folder holds:

| File | Contents |
|---|---|
| `measures.json` | Per-file `ncloc`, `complexity`, `cognitive_complexity` and `code_smells` (Java, TypeScript, CSS, XML). Shape of the `api/measures/component_tree` response. SonarQube omits a measure when its value is 0. |
| `code_smells.json` | The open code-smell issues, with rule keys, severity, locations and effort. Shape of the `api/issues/search` response. |
| `sonar_version.txt` | SonarQube server version. |

## Scan setup

**Server:** SonarQube Community `26.11.0.134175`, run locally in Docker on `http://localhost:9000`:

```
docker run -d --name sonarqube -p 9000:9000 sonarqube:community
```

**Scanner:** `org.sonarsource.scanner.maven:sonar-maven-plugin:5.9.0.7382`.

**Settings:** quality profile Sonar way; TypeScript included (the frontend `.tsx` files are analyzed alongside the Java sources); `sonar.scm.disabled=true`.

**Scan command** (PowerShell, repo root, tag checked out, `SONAR_TOKEN` set from `.env`):

```
.\mvnw.cmd clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:5.9.0.7382:sonar "-DskipTests" "-Dsonar.projectKey=dishes-be-crazy" "-Dsonar.projectName=dishes-be-crazy" "-Dsonar.host.url=http://localhost:9000" "-Dsonar.token=$env:SONAR_TOKEN" "-Dsonar.scm.disabled=true"
```

`-DskipTests` only skips running the tests; it does not change the measures.

## Export commands

Replace `<snapshot>` with the folder name.

```
$auth = "<user token>:"
mkdir docs\metrics\<snapshot> -Force
curl.exe -u $auth "http://localhost:9000/api/measures/component_tree?component=dishes-be-crazy&qualifiers=FIL&metricKeys=ncloc,complexity,cognitive_complexity,code_smells&ps=500" -o docs\metrics\<snapshot>\measures.json
curl.exe -u $auth "http://localhost:9000/api/issues/search?componentKeys=dishes-be-crazy&types=CODE_SMELL&ps=500" -o docs\metrics\<snapshot>\code_smells.json
curl.exe -u $auth "http://localhost:9000/api/server/version" -o docs\metrics\<snapshot>\sonar_version.txt
```

One request covers `measures.json`: the project has fewer than 500 files.
