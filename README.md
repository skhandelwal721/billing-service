# billing-service

Owns invoices and card charging for Northwind Retail. Live since 2024 and processing
production traffic continuously — every charge this service takes is money moving.

## Responsibilities

- `POST /v1/charges` — charges an invoice against a card, applying tax. Preferred.
- `POST /v1/invoices/{invoiceId}/charge` — the original form, unchanged.

Supported card networks: **Visa** and **Mastercard** through the Worldpay acquirer, and
**American Express** through Amex Direct.

## Dependencies

| Service | Used for | Criticality |
| --- | --- | --- |
| `tax-service` | tax on the invoice subtotal | hard — we do not charge an untaxed amount |
| Worldpay acquirer (external) | Visa and Mastercard settlement | hard |
| Amex Direct (external) | American Express settlement | hard |

`payment-service` handles the storefront checkout path and holds its own gateway
relationship. The two services are siblings, not layered.

## Consumers

The charge response is a published contract. Known consumers:

| Consumer | Tier | Reads |
| --- | --- | --- |
| `coupon-service` | 1 | `cardNetwork`, `acquirerReference`, `subtotal`, `tax`, `total` — network promotion eligibility, chargeback matching, redemption audit |
| Finance reconciliation export | 1 | `cardNetwork`, `acquirerReference`, `total` — grouped by network for the daily settlement file. In-repo: `reconciliation/SettlementFileBuilder` |

`coupon-service` also subscribes to
[`northwind.billing.charge.completed`](docs/api/events.md).

See [`docs/api/charge.md`](docs/api/charge.md) for the response shape.

## Rollback

Charges are irreversible once taken. Rolling this service back does not undo money that has
already moved — see [`docs/runbooks/charge.md`](docs/runbooks/charge.md).

## Local development

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
