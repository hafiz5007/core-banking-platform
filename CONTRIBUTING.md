# Contributing

This is a portfolio and reference implementation rather than a product, so I'm not looking for
large feature contributions. These are all welcome:

- **Bug reports** — open an issue with a reproduction. Build and environment details help.
- **ADR challenges** — if you think a decision in [`docs/ADR/`](docs/ADR/) is wrong, say so. Open a
  discussion or a PR adding a superseding ADR. Disagreement with reasoning attached is the most
  useful thing you can send.
- **Documentation fixes** — typos, stale commands, clearer examples: PRs welcome.
- **Questions** — open a GitHub Discussion. Happy to explain any part of the design.

## Local development

```bash
cp .env.example .env
docker compose up --build
```

Full build, run, debug, test and publish commands are in
[`docs/development-guidance.md`](docs/development-guidance.md). The debugging and testing playbook
is in [`docs/DEBUG_TESTING.md`](docs/DEBUG_TESTING.md).

## Tests

```bash
mvn verify                      # unit + integration tests
mvn -B verify -Pquality         # + Spotless formatting and the JaCoCo coverage floor
```

Integration tests start a real PostgreSQL 16 through Testcontainers, so **Docker must be running**.
Requires JDK 21 and Maven 3.9+.

## House rules for code changes

These are enforced in review and most of them are load-bearing:

- Money is never a float — use `com.bank.common.money.Money`.
- The `domain/` layer imports no framework.
- Every schema change is a new Flyway migration. Never edit one that has been applied.
- No PII, PAN or secrets in logs.
- New integrations go behind a port with a stub adapter, selected by configuration.
- Anything that moves money is idempotent.

New behaviour needs a unit test for the domain rule and an `*IT` test for the wired path.

## License

MIT. By contributing you agree that your contribution is licensed under the MIT license.
