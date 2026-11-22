# Asklep — Hospital Case Manager (backend)

JSON API for **Asklep**, a clinic and hospital management system. It is built on **ZTF_J2EE**, a
small teaching Java EE framework (Servlets + JDBC). The user interface is a separate React
application (`hospital-case-frontend`); this repository contains no HTML pages.

### Framework authors
- **Prof. Dr. Cleber S. Oliveira** (ZTF_J2EE)
- Special thanks: **Prof. Dr. Marcia Aparecida Silva Bissaco**

---

## Architecture

| Layer | Package / file | Role |
|---|---|---|
| Routing and ACL | `utils.Router` + `WEB-INF/etc/routes.json` | Every route lives under `/api/*` and declares its verb, session requirement, groups, permissions, `strictGroup` and `allowFirstAccess`. CSRF is checked on every state-changing request. |
| Controllers | `controllers.*` (`Controller extends HttpServlet`) | Thin JSON endpoints: read parameters, call the model, audit, answer with the standard envelope. |
| Models | `models.*` | Business rules and SQL (prepared statements only). |
| Database | `database.*` | Connection pool, automatic provisioning (`script.sql`), numbered migrations, `DBQuery` builder and the `Sql` helper. |
| Utilities | `utils.*` | Configuration, JSON responses, CSRF, password hashing (BCrypt + pepper), rate limiting, validation, security headers. |
| Mail / SMS | `mail.*` | SMTP and Twilio senders with a simulation mode for development. |

### Response envelope
```json
{ "status": true,  "message": "…", "data": { … } }
{ "status": false, "error": "ERR_CODE", "message": "…", "details": { … } }
```
Messages shown to people are in Portuguese. Route names, parameters, envelope keys and code are in
English. Database rows keep their column names (for example `nome`, `data_hora`, `id_paciente`).

### Naming conventions
- Code (classes, methods, variables), routes, query parameters and JSON envelope keys: **English**.
- Database schema and data, and every text the user reads: **Portuguese**.

---

## Modules

| Module | Who uses it | Main routes |
|---|---|---|
| Authentication | everyone | `/api/auth/login`, `/api/auth/session`, `/api/auth/logout`, `/api/auth/forgot-password`, `/api/auth/reset-password`, `/api/auth/change-password` |
| Home dashboard | staff | `/api/dashboard`, `/api/dashboard/team` |
| Patients | reception | `/api/patients`, `/api/patients/{id}`, `/api/patients/{id}/history` |
| Appointments | reception, doctors | `/api/appointments`, `/api/appointments/slots`, `/api/appointments/{id}/check-in`, `/api/appointments/{id}/reschedule` |
| Exam schedule | reception | `/api/exams`, `/api/exams/slots`, `/api/exams/{id}/status` |
| Triage | nursing | `/api/triage`, `/api/triage/{id}`, `/api/triages/{id}/amend` |
| Encounter, records, prescriptions, exam requests | doctors | `/api/encounters`, `/api/encounters/{id}/…`, `/api/prescriptions/{id}`, `/api/exam-requests/{id}/cancel` |
| Vaccination room | reception, nursing | `/api/vaccines`, `/api/vaccines/appointments/{id}/administer` |
| Medical order review | reception | `/api/medical-orders`, `/api/medical-orders/{id}/review` |
| Professionals and specialties | administrator | `/api/professionals`, `/api/specialties` |
| Users and permissions | administrator | `/api/admin/users`, `/api/admin/groups` |
| Audit log and notifications | administrator | `/api/audit-log`, `/api/audit-log/notifications` |
| Patient portal | patients | `/api/public/catalog`, `/api/portal/register`, `/api/portal/*` |
| Framework examples | support, manager, admin | `/api/tickets`, `/api/profile`, `/api/files`, `/api/examples/*` |

### Main rules
- **Access**: clinical routes belong to clinical groups with `strictGroup` (the administrator has no
  clinical access). Doctors and nurses only read a patient's clinical data when they have a care link
  with that patient. Every read and change of clinical data is audited.
- **Scheduling**: slots follow each doctor's weekly availability; no overlaps for the doctor, the
  patient or the exam location; concurrent bookings are serialized (`SELECT … FOR UPDATE` + `UNIQUE`).
  Rescheduling cancels the original appointment and creates a linked one.
- **Medical records**: drafts are versioned; a finished record is immutable (database trigger);
  later changes are addenda.
- **Patient portal**: a patient only sees their own data. A CPF that already has a record is linked
  only with a 6-digit code sent to the record's e-mail. Exams that require a medical order need an
  attached PDF/JPG/PNG (checked by its bytes) or a request from an Asklep doctor. There is no limit on
  how many appointments or exams a patient books.
- **Security**: BCrypt (cost 12) + HMAC-SHA256 pepper, login lockout, rate limiting, CSRF token,
  session fixation protection, mandatory password change on first access, security headers on every
  response, append-only audit log.

---

## Database

MariaDB 10.4+ or MySQL 8+ (schema `hospital`). On MySQL with binary logging on (the default), a
user without SUPER needs `SET PERSIST log_bin_trust_function_creators = 1;` (run once as root) so the
migrations can create their triggers. `WEB-INF/etc/script.sql` is the base schema and is applied when
tables are missing. Later changes are numbered files in `WEB-INF/etc/migrations/` (applied once, in
order, and recorded in `schema_migracoes`). An applied migration is never edited; a new one is added.
Migrations may use MariaDB's `ADD COLUMN IF NOT EXISTS`: on MySQL the runner rewrites it before running.
Migrations `002`–`007` load fictitious demo data and an expanded catalog of exams, vaccines,
specialties and doctors.

## Project layout

```
src/main/java          backend sources
src/main/webapp        WEB-INF (routes.json, config.json, script.sql, migrations, libraries, storage)
src/test/java          JUnit 5 tests (unit and integration)
tools/tests            command-line test runner
diagrams               UML in PlantUML + PNG (use cases, domain and architecture classes, database, sequence, states, activity, deployment)
lib                    compile-only and test-only libraries
```

The project is an Eclipse **Dynamic Web Project** (no Maven) targeting **Java 21** and the
`javax.servlet` API (**Tomcat 9**).
