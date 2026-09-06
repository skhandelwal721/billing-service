# Charge events

Every completed charge is published to one topic. The payload is a published contract in the
same sense as the HTTP response — see [`docs/api/charge.md`](charge.md) — and it is the only
way asynchronous consumers learn that money moved.

## `northwind.billing.charge.completed`

Published once per charge, after the acquirer confirms. At-least-once delivery, so consumers
must key off `chargeId`.

```json
{
  "eventType": "northwind.billing.charge.completed",
  "chargeId": "chg_9f3b7c21",
  "invoiceId": "inv-1001",
  "subtotal": "249.00",
  "surcharge": "0.00",
  "tax": "49.80",
  "total": "298.80",
  "currency": "GBP",
  "cardType": "CREDIT",
  "cardNetwork": "VISA",
  "acquirerReference": "wp_4f8a21c7",
  "status": "CHARGED",
  "occurredAt": "2026-09-01T10:14:22Z"
}
```

The payload mirrors the charge response field for field, deliberately: consumers that read both
the synchronous response and the event use one deserializer for both. A field that changes
meaning changes it on both paths at once.

## Subscribers

| Subscriber | Reads | Why |
| --- | --- | --- |
| `coupon-service` | `cardNetwork`, `acquirerReference`, `subtotal`, `tax`, `total` | Audits each redemption against the charge that settled it, and matches chargebacks by acquirer prefix |

## Compatibility rules

1. **`cardType` carries the funding type** — `CREDIT` or `CHARGE_CARD`. The network moved to
   `cardNetwork`; subscribers that switch on the network read it there.
2. **`acquirerReference` is prefixed by acquirer** — `wp_` for Worldpay, `amex_` for Amex
   Direct.
3. **`subtotal + surcharge + tax == total`.** `surcharge` is `0.00` on every network except
   Amex.
4. New fields are additive. [`docs/api/openapi.yaml`](openapi.yaml) allows unknown properties,
   so a subscriber that has not picked them up is unaffected.
