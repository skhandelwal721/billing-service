# Settlement file

## `POST /v1/settlement/file?settlementDate=YYYY-MM-DD`

Builds the daily settlement file. Driven by the finance reconciliation job, which posts back
the charge responses we emitted that day and books the result against the acquirer's own
settlement report.

The request body is a list of charge responses exactly as
[`docs/api/charge.md`](charge.md) describes them. This endpoint is a **consumer of that
contract**, in-process rather than over the wire, so a change to the charge response reaches
finance through here.

### Response

```json
{
  "settlementDate": "2026-09-01",
  "sections": {
    "VISA": [
      {
        "chargeId": "chg_9f3b7c21",
        "acquirerReference": "wp_4f8a21c7",
        "cardType": "VISA",
        "gross": "298.80",
        "interchange": "0.90",
        "net": "297.90",
        "currency": "GBP",
        "status": "CHARGED"
      }
    ]
  },
  "exceptions": []
}
```

## What this reads out of the charge response

| Field | Used for | If it changes |
| --- | --- | --- |
| `cardType` | the section key — one section per card network, each booked against that network's settlement account | money lands under a heading the bank will not pay, or falls out into `exceptions` |
| `subtotal`, `tax`, `total` | integrity check: `subtotal + tax` must equal `total` | every affected row drops into `exceptions` and finance reconciles it by hand |
| `acquirerReference` | matching our row to the acquirer's settlement report | the row cannot be matched at all |
| `currency`, `status` | booking currency and whether the row settles | — |

## Interchange rates

Keyed by the `cardType` **string** from the charge response, not by the `CardNetwork` enum —
the file is built from stored charge responses, long after the enum is gone.

| `cardType` | Rate |
| --- | --- |
| `VISA` | 0.30% |
| `MASTERCARD` | 0.35% |

An unrated card type is **not** given a default rate. Guessing a fee produces a net amount that
will not match the bank, so the row goes into `exceptions` instead.
`SettlementFileBuilderTest#everyNetworkIsBookableInTheSettlementFile` enumerates
`CardNetwork.values()` against the rate table so that charging on a network finance cannot book
fails the build.

## Exceptions

Rows are never silently corrected. Anything that fails the balance check or has no agreed
interchange rate is listed in `exceptions` and worked by hand the next morning — which is a
day's delay on the affected money, not a lost row.
