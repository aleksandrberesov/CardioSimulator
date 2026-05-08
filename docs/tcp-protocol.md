# CardioSimulator TCP JSON Protocol

Wire format used by CardioSimulator to exchange ECG control commands and
waveform samples over a TCP connection.

- **Encoding**: UTF-8 text
- **Framing**: one JSON object per line, terminated by `\n` (LF). Blank lines
  and whitespace-only lines are ignored by the receiver.
- **Direction**: bidirectional. There is no client/server asymmetry at the
  protocol layer — either side may send any message type. Application-level
  conventions decide who sends what.
- **Reference implementation**: [TcpProtocol.kt](../app/src/main/java/com/example/cardiosimulator/network/TcpProtocol.kt),
  [TcpMessage.kt](../app/src/main/java/com/example/cardiosimulator/network/TcpMessage.kt),
  tests in [TcpProtocolTest.kt](../app/src/test/java/com/example/cardiosimulator/network/TcpProtocolTest.kt).

> **Scope note.** This document specifies only the message format. The Android
> app currently exposes IP/port settings and implements encode/decode, but
> does not yet open sockets. Connection lifecycle, authentication, reconnect
> behavior, and keep-alive are out of scope here and TBD.

---

## Common envelope

Every message is a JSON object with at minimum a `type` field.

| Field  | Type   | Required | Description                                                                   |
|--------|--------|----------|-------------------------------------------------------------------------------|
| `type` | string | yes      | Discriminator. One of `start`, `stop`, `points`. Unknown values are rejected. |
| `id`   | string | no       | Optional correlation identifier chosen by the sender. Echoed back as-is.      |

Senders **MUST** omit optional fields rather than send `null` when they have
no value. The reference decoder treats missing and JSON `null` identically,
but other parsers may differ.

---

## Message: `start`

Begins streaming. Sent from the controller to the simulator.

| Field        | Type            | Required | Default | Description                                                                                  |
|--------------|-----------------|----------|---------|----------------------------------------------------------------------------------------------|
| `type`       | string          | yes      | —       | Must be `"start"`.                                                                           |
| `id`         | string          | no       | —       | See envelope.                                                                                |
| `sampleRate` | integer         | no       | —       | Samples per second the receiver should produce. Application-defined when omitted.            |
| `params`     | object<string,string\> | no | `{}`   | Free-form key/value parameters. All values are strings. Only emitted on the wire if non-empty. |

```json
{"type":"start","id":"m1","sampleRate":250,"params":{"source":"ecg"}}
```

Minimal form:

```json
{"type":"start"}
```

---

## Message: `stop`

Halts streaming.

| Field  | Type   | Required | Description       |
|--------|--------|----------|-------------------|
| `type` | string | yes      | Must be `"stop"`. |
| `id`   | string | no       | See envelope.     |

```json
{"type":"stop","id":"m2"}
```

---

## Message: `points`

A batch of waveform samples for a single lead.

| Field    | Type           | Required | Default | Description                                                                                                |
|----------|----------------|----------|---------|------------------------------------------------------------------------------------------------------------|
| `type`   | string         | yes      | —       | Must be `"points"`.                                                                                        |
| `id`     | string         | no       | —       | See envelope.                                                                                              |
| `lead`   | string         | no       | —       | ECG lead the samples belong to. See [lead tokens](#lead-tokens).                                           |
| `identy` | string         | no       | —       | Opaque series identifier — links this batch to a specific source recording. Free-form, sender-defined.     |
| `offset` | integer        | no       | `0`     | Sample index at which `values[0]` starts within the series. Omitted on the wire when zero.                 |
| `values` | array<number\> | yes      | —       | Sample amplitudes, in order. JSON numbers; the reference implementation parses them as 32-bit floats.      |

```json
{"type":"points","id":"m3","lead":"II","identy":"series-1","offset":10,"values":[0.1,0.2,0.3]}
```

Minimal form (no lead, no identy, offset = 0):

```json
{"type":"points","values":[1.0,2.0,3.5]}
```

### Lead tokens

Valid `lead` values, defined by the
[`Lead`](../app/src/main/java/com/example/cardiosimulator/domain/EcgData.kt) enum:

```
I, II, III, aVR, aVL, aVF, V1, V2, V3, V4, V5, V6
```

Decoding is case-insensitive and trims surrounding whitespace, so `"ii"`,
`"II"`, and `" II "` are all accepted. Encoders **SHOULD** emit the canonical
forms above (`I`, `II`, `III`, `aVR`, `aVL`, `aVF`, `V1`–`V6`).

Any other token causes the message to be rejected with an `Unknown lead`
error.

---

## Framing

- One JSON object per line. The line terminator is `\n`.
- Blank lines and lines containing only whitespace are skipped by the decoder.
- The decoder does **not** support pretty-printed (multi-line) JSON. Each
  object must fit on a single line.

Example stream (three valid frames separated by a blank line):

```
{"type":"start","sampleRate":250}

{"type":"points","lead":"II","values":[0.1,0.2,0.3]}
{"type":"stop"}
```

---

## Error handling

The reference decoder rejects a frame and raises `TcpProtocolException` when:

| Condition                                | Error message contains   |
|------------------------------------------|--------------------------|
| Frame is not valid JSON                  | `Invalid JSON`           |
| Object has no `type` field               | `Missing required field` |
| `type` value is not a known message type | `Unknown message type`   |
| `points` message is missing `values`     | `Missing required field` |
| `points.values` contains a non-number    | `Invalid number in values` |
| `lead` token is not a known lead         | `Unknown lead`           |

The decoder is otherwise lenient: it ignores unknown fields, and treats
missing optional fields and JSON `null` the same way.

A `decodeOrNull` variant is provided for callers that want to silently drop
malformed frames (e.g. when reading from a noisy stream).

---

## Round-trip stability

The reference encoder is normalizing — re-encoding a decoded message produces
canonical output:

- Optional fields with no value are dropped.
- `points.offset` is omitted when it equals `0`.
- `start.params` is omitted when empty.

If you implement an alternate encoder, follow the same rules so receivers can
diff frames meaningfully.

---

## End-to-end example

A controller drives a simulator through one short session:

```
→ {"type":"start","id":"req-1","sampleRate":500,"params":{"source":"ecg"}}
← {"type":"points","id":"req-1","lead":"I","identy":"abc","values":[0.0,0.1,0.2]}
← {"type":"points","id":"req-1","lead":"I","identy":"abc","offset":3,"values":[0.3,0.4,0.5]}
→ {"type":"stop","id":"req-1"}
```

The `id` field is a sender-chosen string that the receiver echoes on related
frames; it has no protocol-level meaning beyond correlation.
