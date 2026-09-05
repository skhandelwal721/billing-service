# billing-service

Owns invoices and card charging for Northwind Retail. Live since 2024 and processing
production traffic continuously — every charge this service takes is money moving.

## Responsibilities

- `POST /v1/invoices/{invoiceId}/charge` — charges an invoice against a card, applying tax.

Supported card networks today: **Visa** and **Mastercard**, both settled through the
Worldpay acquirer.

## Dependencies

| Service | Used for | Criticality |
| --- | --- | --- |
| `tax-service` | tax on the invoice subtotal | hard — we do not charge an untaxed amount |
| Worldpay acquirer (external) | authorisation and settlement | hard |

`payment-service` handles the storefront checkout path and holds its own gateway
relationship. The two services are siblings, not layered.

## Consumers

The charge response is a published contract. Known consumers:

| Consumer | Tier | Reads |
| --- | --- | --- |
| `order-service` | 1 | `cardType`, `total`, `status` — drives order state and the customer receipt |
| `coupon-service` | 1 | `cardType`, `acquirerReference`, `subtotal`, `tax`, `total` — network promotion eligibility, chargeback matching, redemption audit |
| Finance reconciliation export | 1 | `cardType`, `acquirerReference`, `total` — grouped by network for the daily settlement file |

`coupon-service` is the only consumer that generates its client DTO from
[`docs/api/openapi.yaml`](docs/api/openapi.yaml) rather than hand-rolling it, so a response
field we add lands in their build, not just in their runtime. It also subscribes to
[`northwind.billing.charge.completed`](docs/api/events.md).

Because reconciliation groups by `cardType`, changing what that field *means* is a breaking
change even when its name and type stay the same. See
[`docs/api/charge.md`](docs/api/charge.md).

## Rollback

Charges are irreversible once taken. Rolling this service back does not undo money that has
already moved — see [`docs/runbooks/charge.md`](docs/runbooks/charge.md).

## Local development

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
