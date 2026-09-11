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

---

# Coupon redemption events (inbound)

## `northwind.coupon.redemption.completed`

Published by **coupon-service**, consumed by us. This is the only inbound contract we take from
coupon-service; every other interaction is them calling our charge API.

We pin it at `coupon.contract.version` in `pom.xml` — currently **2.4.0** — and deserialize it
strictly in `RedemptionCompletedEvent`.

```json
{
  "eventType": "northwind.coupon.redemption.completed",
  "redemptionId": "rdm_4f8a21c7",
  "couponCode": "NW-VISA-10",
  "chargeId": "chg_9f3b7c21",
  "fundingNetwork": "VISA",
  "discount": "24.90",
  "status": "REDEEMED",
  "occurredAt": "2026-09-11T10:14:22Z"
}
```

### What we read, and what breaks if it moves

| Field | Used for | If it is renamed, removed or changes meaning |
| --- | --- | --- |
| `redemptionId` | **Deduplication key** — the topic is at-least-once | We cannot tell a redelivery from a new redemption. `RedemptionCompletedListener` refuses the event rather than double-booking a rebate. |
| `couponCode` | Attributing a refund back to the promotion that funded it | Refunds come out of merchant margin instead of the interchange rebate. Silent — the refund still succeeds. |
| `fundingNetwork` | The interchange rebate account, resolved with `CardNetwork.valueOf` | Closed set. A renamed or absent value throws and the rebate is never booked, so the settlement file will not reconcile against the acquirer report. |
| `discount` | The rebate base — **an absolute currency amount** | A percentage, a rate or a minor-unit figure here produces a well-formed rebate at the wrong magnitude. Nothing throws. |
| `status` | Only `REDEEMED` books a rebate | A new or renamed terminal status means no rebate is booked and no error is raised. |

### Compatibility expectations on coupon-service

1. `redemptionId` is always present. Without it we cannot be idempotent.
2. `fundingNetwork` stays a card network name from the closed set `VISA`, `MASTERCARD`, `AMEX`.
3. `discount` stays an absolute amount in the charge currency, on the same basis as
   `promotionalAdjustment` on our charge request.
4. Any change to a field's name, type or meaning is a major bump, announced before it ships, per
   the Northwind Engineering Change Standards (ECS-3).
