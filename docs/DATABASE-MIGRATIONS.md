# Database migration policy

## The constraint this policy exists for

`infra/deploy/deploy.sh` can instantly roll back the **application image** to the last
known-good SHA if health checks or smoke tests fail. It cannot roll back the
**database schema** — Flyway migrations are one-way, and there's no down-migration
step in this pipeline (by design: down-migrations that silently destroy data are their
own hazard).

That means a rollback always produces this combination:

```
old application image  +  new database schema (whatever the failed deploy's
                           migrations already applied before the failure)
```

never:

```
old application image  +  old database schema
```

If a migration isn't backward-compatible with the *previous* application version, a
rollback triggered by an unrelated failure (a bad frontend build, a flaky smoke test)
will leave the old backend running against a schema it doesn't understand — turning a
minor deploy hiccup into an outage.

## The policy: expand → deploy → contract

Every schema change that isn't purely additive-and-nullable must be split across at
least two deploys:

1. **Expand** (deploy N): add the new shape *alongside* the old one. Both the old and
   new application code must work against the resulting schema.
   - Adding a new nullable column: fine, this step alone is sufficient.
   - Renaming a column: add the new column, write to both old and new in application
     code, backfill, but do **not** drop the old column yet.
   - Changing a column's type: add a new column with the new type, dual-write, backfill.
   - Adding a `NOT NULL` constraint: add the column nullable first, backfill, add the
     constraint in a *later* migration once you're confident no old code path still
     inserts nulls.

2. **Deploy** (deploy N): ship the application code that uses the new shape. At this
   point both migration N's schema and the previous deploy's schema are valid for
   *some* version of the app that might end up running (the new version, or — if this
   deploy gets rolled back — the previous one).

3. **Contract** (deploy N+1, only after deploy N has been running successfully for a
   while): remove the old shape (drop the old column, drop the temporary dual-write
   code). By this point, rolling back deploy N+1 only ever lands on deploy N's schema,
   which deploy N's own application code already handles correctly.

## Rules of thumb

- **Never rename or drop a column in the same migration that a deploy's application
  code stops reading it.** Always one deploy of overlap minimum.
- **New columns are nullable (or have a default) until a later migration adds `NOT
  NULL`,** once you're sure every code path that inserts rows has been updated.
- **Never repurpose a column's meaning in place.** Add a new one.
- **Additive, backward-compatible migrations** (new nullable column, new table, new
  index) don't need this dance — they're safe to ship in the same deploy as the code
  that uses them, because the previous application version simply ignores the new
  column/table.
- Before merging any migration, ask: *"If this deploy's health check fails five
  minutes after this migration runs, and deploy.sh rolls back to the previous
  application image right now — does that old code still work against the resulting
  schema?"* If the answer isn't a confident yes, split the migration.

## Applying this to the current migration history

This policy starts now, going forward — it is not a retroactive audit of
`V1__init_schema.sql` through the latest migration in
`backend/src/main/resources/db/migration/`. Treat every *new* migration added from
here on as subject to this policy; call it out explicitly in the PR description when a
change needs the expand/contract split across two deploys.
