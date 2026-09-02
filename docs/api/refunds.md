# Refund API

## `POST /v1/refunds`

Refunds a charge this service took. Raised by support tooling and by finance when a settlement
line has to be reversed.

```json
{
  "acquirerReference": "wp_4f8a21c7",
  "cardType": "VISA",
  "amount": "298.80",
  "daysSinceCharge": 10
}
```

### Response

```json
{
  "acquirerReference": "wp_4f8a21c7",
  "cardType": "VISA",
  "amount": "298.80",
  "status": "REFUNDED"
}
```

## Refund routing

Both request fields are copied straight off the charge response — see
[`docs/api/charge.md`](charge.md). Nothing is re-derived, because a refund is often raised days
after the charge, out of the settlement file, by someone who never saw the original request.

**The acquirer is not carried on the request; it is inferred from the reference prefix.**
`wp_…` is Worldpay, and Worldpay is the only acquirer this service can refund. A reference in
any other format returns `422` and the money stays taken until someone reverses it by hand in
the acquirer's own portal.

**`cardType` is read as a `CardNetwork` name** to pick the scheme refund window. A value that
is not a network name fails the refund outright, so a change to what `cardType` carries breaks
this endpoint even though its own contract has not moved.

## Refund windows

Set by scheme rules, not by us.

| Network | Window |
| --- | --- |
| `VISA` | 180 days |
| `MASTERCARD` | 120 days |

There is deliberately **no default**. A network we can charge but have no window for is a
network whose refunds we cannot route, and inventing a window would be us asserting a scheme
rule that was never agreed. `RefundPolicyTest#everyNetworkHasARefundWindow` enumerates
`CardNetwork.values()` so that adding a network without a window fails the build rather than
stranding money in production.

## Errors

| Status | When |
| --- | --- |
| `400` | request fields missing or the amount is not positive |
| `409` | outside the scheme refund window for the network |
| `422` | the acquirer reference was not issued by Worldpay — this service cannot refund it |
