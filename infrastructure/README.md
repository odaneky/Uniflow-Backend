# Infrastructure

Reserved for infrastructure-as-code — environment provisioning, managed PostgreSQL, secrets, and
deployment pipelines.

It is intentionally empty. Committing speculative Terraform for a target platform that has not
been chosen produces code nobody has run against a real account, which is worse than no code at
all: it looks authoritative and is quietly wrong.

What is already decided and should be honoured by whatever lands here:

- **The schema is owned by Flyway.** No environment may run with `ddl-auto` set to anything other
  than `validate`. Deployment applies migrations; it never lets Hibernate generate DDL.
- **Configuration comes from the environment.** `application-prod.yml` deliberately declares
  `${DB_URL}`, `${DB_USERNAME}` and `${DB_PASSWORD}` with no defaults, so a misconfigured
  deployment fails at start-up rather than silently connecting somewhere unintended.
- **Migrations are forward-only in production.** `spring.flyway.clean-disabled` is `true` there.
