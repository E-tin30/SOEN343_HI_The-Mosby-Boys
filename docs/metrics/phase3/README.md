# Phase 3 Metrics: SonarQube Export

This folder holds the SonarQube measures and issues exported for Phase 3 of `dishes-be-crazy`.

| File | Contents |
|---|---|
| `measures.json` | Per-file measures (`ncloc`, `complexity`, `cognitive_complexity`) for 197 components (Java, TypeScript, CSS, XML). Shape matches the SonarQube `api/measures/component_tree` response. |
| `code_smells.json` | The 4 open code-smell issues, with rule keys, severity, locations and effort. Shape matches the SonarQube `api/issues/search` response. |
| `sonar_version.txt` | SonarQube server version used for the export: `26.11.0.134175`. |

## Scan setup

**Scanner plugin:** `org.sonarsource.scanner.maven:sonar-maven-plugin:5.9.0.7382` (confirmed by the scan log, goal `sonar` run as `default-cli`).

**Server:** SonarQube Community, run locally in Docker:

```
docker run -d --name sonarqube -p 9000:9000 sonarqube:community
```

The server listens on `http://localhost:9000`.

**Scan command:** *Not recorded.* The exact command could not be recovered from shell history or the project. The known shape is a Maven invocation of `sonar:sonar` with the token supplied from the environment (`SONAR_TOKEN` in `.env`). Replace this section with the exact command once it is confirmed:

```
./mvnw clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar   
    -Dsonar.projectKey=dishes-be-crazy   
    -Dsonar.projectName='dishes-be-crazy'   
    -Dsonar.host.url=http://localhost:9000   
    -Dsonar.token=<TOKEN> 
    -Dsonar.scm.disabled=true
```

**Settings:**

- **Quality profile:** Sonar way. *Not verified from the export files.* Confirm in the SonarQube UI under the project's quality profiles.
- **`sonar.scm.disabled=true`:** *Not verified.* This was recommended after the scan failed with a JGit "Missing blob" error during SCM blame (see the troubleshooting note below). If the export was produced with blame enabled, the scan failed; the exports are only valid if the scan completed.
- **TypeScript:** included. `measures.json` contains 56 components with `"language":"ts"`, and the frontend `.tsx` files are analyzed alongside the Java sources.

## Export commands

**Export commands:** *Not recorded.* The files look like the output of SonarQube Web API calls, but the requests themselves were not found. Replace this section with the exact requests once confirmed. The expected form is:

```
mkdir docs\metrics\phase3 -Force
$auth = "$($env:SONAR_TOKEN):"
curl.exe -u $auth "http://localhost:9000/api/measures/component_tree?component=dishes-be-crazy&qualifiers=FIL&metricKeys=ncloc,complexity,cognitive_complexity,code_smells&ps=500" -o docs\metrics\phase3\measures.json
curl.exe -u $auth "http://localhost:9000/api/issues/search?componentKeys=dishes-be-crazy&types=CODE_SMELL&ps=500" -o docs\metrics\phase3\code_smells.json
curl.exe -u $auth "http://localhost:9000/api/server/version" -o docs\metrics\phase3\sonar_version.txt
```

Note that `measures.json` reports 197 components on a single page (`pageSize` 500), so one request covers the export.

## Troubleshooting: "Missing blob" during SCM blame

A scan can fail with:

```
org.eclipse.jgit.errors.MissingObjectException: Missing blob 01d688ef...
```

The blob exists and passes `git fsck` and `git multi-pack-index verify`. The failure is in the scanner's JGit, not the repository. The workaround is `-Dsonar.scm.disabled=true`, which skips blame and therefore author attribution and "new code" by blame. Coverage and rule results are unaffected.
