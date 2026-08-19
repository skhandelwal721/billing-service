# Runbook — charging

**Service:** `billing-service`
**Owning team:** billing-platform
**Escalation:** `#northwind-billing`, on-call schedule `billing-platform_schedule`

## Endpoints

| Endpoint | SLO |
| --- | --- |
| `POST /v1/invoices/{invoiceId}/charge` | p99 < 3s, 99.95% availability |
| `POST /v1/charges` | TBD |

## Alarms

| Alarm | First response |
| --- | --- |
| `ChargeErrorRate` (P1) | Check acquirer status, then `tax-service`. Every 5xx here is a charge that didn't happen. |
| `ChargeDeclineRate` (P2) | A spike usually means an acquirer-side rule change, not our bug. |
| `AcquirerLatencyP99` (P2) | Check the Worldpay status page before assuming it's us. |
| `TaxServiceErrorRate` (P2) | Charges will be failing closed. Escalate to `#northwind-tax`. |

## Rollback

**Read this before rolling back.** Deploys are reversible; charges are not.

Rolling this service back does not undo money that has already moved. A charge taken by the
version being rolled back stays taken, and the only way to reverse it is a refund against the
`acquirerReference` we recorded. That means:

1. The previous version must be able to refund every reference the new version could have
   created. If a change introduces a new acquirer, a new reference format, or a new settlement
   route, the previous version cannot refund those charges and rollback strands them.
2. Any change that alters what we charge, how we route it, or how references are issued needs
   an explicit rollback plan naming who refunds what — "redeploy the previous artifact" is not
   a rollback plan for this service.

Check `ChargeErrorRate` and the acquirer settlement report for the affected window before
declaring a rollback complete.

## Adding a card network

Amex went live in 4.12. Anything further needs, at minimum: its own alarms, a documented refund
path from the previous release, and agreement with finance on how the network appears in the
reconciliation export.
