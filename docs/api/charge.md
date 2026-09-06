# Charge API

## `POST /v1/invoices/{invoiceId}/charge`

Charges an invoice against a card. Applies tax from `tax-service`, settles through the
Worldpay acquirer.

```json
{
  "cardNumber": "4111111111111111",
  "currency": "GBP",
  "billingPostcode": "EC2A 4BX"
}
```

### Response

```json
{
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
  "status": "CHARGED"
}
```

## `POST /v1/charges`

The new consolidated endpoint. Same response, but the invoice goes in the body so callers no
longer need a separate lookup before charging.

```json
{
  "invoiceId": "inv-1001",
  "cardNumber": "378282246310005",
  "currency": "GBP",
  "billingPostcode": "EC2A 4BX",
  "promotionalAdjustment": "24.90"
}
```

Amex charges carry a 1.5% surcharge, applied to the subtotal before tax, and settle through
Amex Direct rather than Worldpay — their `acquirerReference` is prefixed `amex_`.

### `promotionalAdjustment`

Optional. Taken off the invoice subtotal before surcharge and tax, so a caller applying a
discount no longer has to charge the full amount and then raise a refund.

> **An absolute amount, in the charge currency. Not a percentage.** `"24.90"` means twenty-four
> pounds ninety off a sterling invoice. `"10"` means ten pounds off, **not** ten percent off.

We do not accept a rate, and this is deliberate. The caller knows the basis it applied and we do
not, so a rate here would mean two services computing the same money figure from different
inputs and disagreeing quietly. Send us the money.

**There is nothing we can validate this against.** A percentage and an amount are both positive
decimals smaller than the invoice, so a rate sent by mistake is indistinguishable from an
amount and will be charged. The two observable consequences:

| Sent | Invoice | What happens |
| --- | --- | --- |
| `24.90` (correct amount) | `249.00` | charged on `224.10` + tax |
| `10` (a rate, by mistake) | `249.00` | charged on `239.00` + tax — **no error, customer overcharged** |
| `25` (a rate, by mistake) | `18.50` | **`422`** — the adjustment exceeds the subtotal, charge refused |

That last row is the only one that surfaces. A rate mistaken for an amount is silent on every
invoice larger than the rate, and fails only on invoices smaller than it — so it presents as an
intermittent checkout failure on small baskets, with correct-looking charges everywhere else.

An adjustment larger than the subtotal is refused rather than clamped to zero or charged as a
negative: a negative charge is a credit to the cardholder, and taking one by accident is worse
than failing the request. `POST /v1/invoices/{invoiceId}/charge` does not accept an adjustment
at all.

## Contract stability

This response is consumed outside this service. Treat it as versioned even though there is no
version in the path.

The machine-readable form is [`openapi.yaml`](openapi.yaml); the asynchronous form is
[`events.md`](events.md). Both carry the same constraints and both are generated against by
consumers.

| Field | Consumer | Used for |
| --- | --- | --- |
| `cardNetwork` | finance reconciliation | groups the daily settlement file by network |
| `cardNetwork` | `coupon-service` | network promotion eligibility |
| `acquirerReference` | finance reconciliation, refunds | matching our charges to the acquirer's settlement report |
| `acquirerReference` | `coupon-service` | derives the acquirer from the `wp_` prefix to match inbound chargebacks and reverse the coupon liability |
| `subtotal`, `tax`, `total` | `coupon-service` | recomputes the discount it applied against `subtotal + tax == total`; a total it cannot account for is treated as a mispriced charge and held |

### `cardType` values

`CREDIT` or `CHARGE_CARD`. Amex is a charge card; Visa and Mastercard are credit.

Previously this field carried the card network, which was always a slightly loose use of the
name. With Amex in the mix the funding distinction genuinely matters, so the network moved to
`cardNetwork` and `cardType` now says what it sounds like it says. Nothing is lost — the
network is still on the response, one field along.

### Response invariants

`subtotal + surcharge + tax == total`. `surcharge` is `0.00` on every network except Amex, so
the identity is unchanged in practice for existing traffic.

`subtotal` stays the pre-surcharge invoice amount, which is what it always meant.

## Errors

| Status | When |
| --- | --- |
| `400` | card number missing or the network is not one we accept |
| `404` | invoice not found |
| `502` | `tax-service` could not price the tax — we do not charge an untaxed amount |
