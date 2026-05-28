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

> **Scope note.** This document specifies the message format. The Android
> app's `AppViewModel` opens a TCP socket on demand from the Settings dialog,
> auto-uploads the current `Pathologies.zip` snapshot on every successful
> connect, drains incoming frames to detect disconnects, and reconnects
> on a fixed `tcpReconnectIntervalMs` cadence (default 5 s). See
> [architecture.md](architecture.md) §4 for the connection lifecycle.
> Authentication and keep-alive are not implemented and are out of scope
> for this document.

---

## Common envelope

Every message is a JSON object with at minimum a `type` field.

| Field  | Type   | Required | Description                                                                                    |
|--------|--------|----------|------------------------------------------------------------------------------------------------|
| `type` | string | yes      | Discriminator. One of `start`, `stop`, `points`, `upload`, `ack`. Unknown values are rejected. |
| `id`   | string | no       | Optional correlation identifier chosen by the sender. Echoed back as-is.                       |

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
[`Lead`](../app/src/main/java/com/example/cardiosimulator/domain/Pathology.kt) enum:

```
I, II, III, aVR, aVL, aVF, V1, V2, V3, V4, V5, V6
```

Decoding is case-insensitive and trims surrounding whitespace, so `"ii"`,
`"II"`, and `" II "` are all accepted. Encoders **SHOULD** emit the canonical
forms above (`I`, `II`, `III`, `aVR`, `aVL`, `aVF`, `V1`–`V6`).

Any other token causes the message to be rejected with an `Unknown lead`
error.

---

## Message: `upload`

Transfers a binary file (e.g. a ZIP archive) from the client to the server.
This message uses **mixed framing**: the JSON header is sent as a normal
newline-terminated line, and the raw binary payload follows immediately with
no separator. See [Binary payload framing](#binary-payload-framing).

| Field      | Type    | Required | Description                                                    |
|------------|---------|----------|----------------------------------------------------------------|
| `type`     | string  | yes      | Must be `"upload"`.                                            |
| `id`       | string  | no       | See envelope. Echoed in the `ack` response.                    |
| `filename` | string  | yes      | Suggested filename. Path separators and unsafe characters are sanitized by the receiver. |
| `size`     | integer | yes      | Exact byte length of the binary payload that follows the `\n`. |

```json
{"type":"upload","id":"u1","filename":"data.zip","size":204800}
```

Immediately after the terminating `\n`, the sender writes exactly `size`
bytes of raw binary data. No newline or other delimiter follows the payload.

After the payload is fully received and saved the server emits an `ack`.

---

## Message: `ack`

Sent by the server after a successful `upload`. Not sent for other message
types.

| Field      | Type    | Required | Description                                                                      |
|------------|---------|----------|----------------------------------------------------------------------------------|
| `type`     | string  | yes      | Must be `"ack"`.                                                                 |
| `id`       | string  | no       | Echoed from the corresponding `upload`, if one was provided.                     |
| `filename` | string  | yes      | The filename under which the file was saved (may differ from the requested name if a collision was resolved). |
| `bytes`    | integer | yes      | Number of bytes written to disk. Equals the `upload.size` on success.           |

```json
{"type":"ack","id":"u1","filename":"data.zip","bytes":204800}
```

---

## Framing

### JSON frames

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

### Binary payload framing

The `upload` message uses mixed framing:

```
<JSON header line>\n<size raw bytes>
```

1. The sender writes the `upload` JSON object followed by `\n`.
2. Immediately after (no gap, no extra delimiter) the sender writes exactly
   `size` bytes of raw binary data.
3. After the payload the connection returns to normal JSON-per-line framing —
   the sender may issue further JSON messages on the same connection.

The receiver switches to binary-read mode as soon as it parses the `upload`
header. Any bytes buffered after the `\n` are treated as the start of the
payload.

---

## Error handling

The reference decoder rejects a frame and raises `TcpProtocolException` when:

| Condition                                | Error message contains     |
|------------------------------------------|----------------------------|
| Frame is not valid JSON                  | `Invalid JSON`             |
| Object has no `type` field               | `Missing required field`   |
| `type` value is not a known message type | `Unknown message type`     |
| `points` message is missing `values`     | `Missing required field`   |
| `points.values` contains a non-number    | `Invalid number in values` |
| `lead` token is not a known lead         | `Unknown lead`             |
| `upload` missing `filename`              | `Missing required field`   |
| `upload` missing `size`                  | `Missing required field`   |
| `upload.filename` is empty or unsafe     | connection closed, no ack  |
| `upload.size` exceeds server limit       | connection closed, no ack  |
| Binary payload cut short (peer closed)   | connection closed, no ack  |

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

## End-to-end examples

### ECG streaming session

A controller drives a simulator through one short session:

```
→ {"type":"start","id":"req-1","sampleRate":500,"params":{"source":"ecg"}}
← {"type":"points","id":"req-1","lead":"I","identy":"abc","values":[0.0,0.1,0.2]}
← {"type":"points","id":"req-1","lead":"I","identy":"abc","offset":3,"values":[0.3,0.4,0.5]}
→ {"type":"stop","id":"req-1"}
```

### File upload

A client uploads a ZIP file and receives confirmation:

```
→ {"type":"upload","id":"u1","filename":"patient-data.zip","size":204800}\n
→ <204800 raw bytes>
← {"type":"ack","id":"u1","filename":"patient-data.zip","bytes":204800}
```

If a file with the same name already exists the server saves under a
disambiguated name (`patient-data_1.zip`, etc.) and reflects it in `ack.filename`.

The `id` field is a sender-chosen string that the receiver echoes on related
frames; it has no protocol-level meaning beyond correlation.
