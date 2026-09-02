# Runbook — pre-charge risk checks

**Service:** `billing-service`
**Owning team:** billing-platform
**Escalation:** `#northwind-billing`, then `#northwind-fraud` for exposure limits

## What the guard does

`ChargeRiskGuard#check` runs before anything is sent to an acquirer. A declined charge is a
support ticket; a fraudulent charge that settles is a chargeback plus a refund we may not be
able to route. So the check is deliberately on the *pre*-acquirer side of the charge path.

| Check | Outcome when it fails |
| --- | --- |
| invoice amount within the network's exposure ceiling | `409`, no money moves, manual review |
| network has an agreed exposure ceiling at all | `409`, no money moves, escalate to `#northwind-fraud` |

## Where it is called from

`ChargeController#charge`, explicitly, before delegating to `ChargeService`.

**There is no filter, interceptor or AOP advice applying this globally.** That was a deliberate
call — a silent global guard on a money path is harder to reason about than an explicit line at
the entrypoint — but it has a consequence worth being blunt about:

> **Any new charge entrypoint must call `ChargeRiskGuard#check` itself.** A controller that
> calls `ChargeService#charge` without it takes unguarded, unlimited charges, and nothing in
> the build or at startup will tell you. If you are adding a way to charge, this is the line
> you must not forget.

`ChargeService` deliberately does *not* call the guard: it is also used by replay and backfill
tooling, where the check has already been made upstream and re-running it would decline
charges that were already approved.

## Exposure ceilings

| Network | Ceiling |
| --- | --- |
| `VISA` | 5000.00 |
| `MASTERCARD` | 5000.00 |

Ceilings come from the chargeback exposure we carry per scheme, so there is **no default**. A
network with no entry is declined outright rather than treated as unlimited.
`ChargeRiskGuardTest#everyNetworkHasACeiling` enumerates `CardNetwork.values()` so that adding
a network without agreeing a limit fails the build.

## Adding a card network

Before the first charge on a new network can be taken:

1. agree an exposure ceiling with `#northwind-fraud` and add it to `NetworkChargeCeilings`;
2. confirm the refund path for the network exists — see [`docs/api/refunds.md`](../api/refunds.md);
3. confirm finance can book it — see [`docs/api/settlement.md`](../api/settlement.md).

All three, or charges on the new network are taken and then cannot be limited, refunded or
settled.
