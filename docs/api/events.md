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
  "tax": "49.80",
  "total": "298.80",
  "currency": "GBP",
  "cardType": "VISA",
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
| `coupon-service` | `cardType`, `acquirerReference`, `subtotal`, `tax`, `total` | Audits each redemption against the charge that settled it, and matches chargebacks by acquirer prefix |
| `order-service` | `cardType`, `total`, `status` | Releases the order and picks the receipt template |

## Compatibility rules

1. **`cardType` carries the card network** — `VISA` or `MASTERCARD`. Subscribers switch on it.
2. **`acquirerReference` is prefixed by acquirer** — `wp_` for Worldpay. Subscribers derive the
   acquirer from the prefix; a new prefix is an unrecognised acquirer to them.
3. **`subtotal + tax == total`.** Subscribers recompute against this to detect mispriced
   charges. Adding anything to `total` without a matching field breaks the check.
4. Adding a field to this payload requires notifying every subscriber first. Subscribers
   generate their deserializers from
   [`docs/api/openapi.yaml`](openapi.yaml), which is strict — an unknown field is a
   deserialization failure, not a no-op.
