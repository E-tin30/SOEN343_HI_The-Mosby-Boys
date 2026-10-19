<img width="1916" height="526" alt="image" src="https://github.com/user-attachments/assets/ff51ed18-90d5-44ae-a94a-d1c846d4efbc" />

## Team Members
- Ethan Elkaim 40311428 - Lead Developer
- Zayden Kung’u 40311065 - Project Manager (Team Leader)
- Ashton Levine 40306341 - Product Manager
- Adrian Charbonneau 40310777 - Prompt Engineer
- Yana Vassilyev 40270779 - DevOps
- Jalen Brayne 40319433 - QA

## Demo profile seed

`src/main/resources/demo/profiles.json` defines one fictional profile per role.
JSON does not support comments; `profileId` identifies the linked table row,
while Supabase Auth supplies the shared user ID.
The current schema stores credentials in Supabase Auth: `public."User".id` must
reference `auth.users.id`. The loader uses the Supabase Admin API to create missing
confirmed Auth accounts, then links their profile rows. It does not send invitation emails.

Add these to the untracked `.env`, alongside the existing database credentials:

```text
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your-server-side-service-role-key
DEMO_SEED_PASSWORD=your-demo-only-password
```

A new secret key can instead use `SUPABASE_SECRET_KEY`. Use a password of at least
8 characters that satisfies the project's password policy. It is shared by newly
created demo accounts and is never written to the JSON configuration or public tables.
Supabase Auth hashes it. Existing Auth accounts and passwords are left unchanged.

Demo login emails:

- `customer.demo@example.com`
- `restaurant.demo@example.com`
- `courier.demo@example.com`
- `operator.demo@example.com`

With `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, and `SUPABASE_DB_PASSWORD` exported in
the terminal environment, run:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo-seed
```

Spring Boot does not automatically load `.env`; configure the environment in your
IDE or shell first. This profile runs without an HTTP server. It reads the JSON
configuration, looks up or creates the four Auth accounts by their demo emails, then inserts the
User and linked Customer/Courier/Restaurant rows in one transaction. The database
connection needs permission to look up these Auth accounts and insert profiles.
Auth creation happens before the SQL transaction: if later steps fail, created
Auth accounts remain and are reused on retry. No existing account is deleted or reset.

Existing profiles are preserved and repeated runs do not add another profile for
those users. This loads missing demo data; it does not reset orders or restore
modified demo attributes. Courier state `OFF_DUTY`, restaurant status `OPEN`, and
uppercase role names are seed conventions to align with the team's service code.

Run the loader tests without connecting to Supabase:

```sh
./mvnw -Dtest=DemoProfileSeederTests test
```
