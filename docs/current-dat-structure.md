# Current State of .dat File Structure

This document provides a concise overview of the `.dat` file structure currently used by the CardioSimulator app. This format is designed for efficiency and simplicity, bundling all 12 standard ECG leads for a single pathology into a single UTF-8 text file.

## 1. File Specification

*   **Encoding**: UTF-8
*   **Line Endings**: LF (`\n`)
*   **Format**: Plain text with `key:value` pairs.
*   **Blocks**: Separated by one or more blank lines.

## 2. Structure Breakdown

A typical pathology `.dat` file consists of one **Header Block** followed by multiple **Lead Blocks**.

### 2.1 Header Block
Contains metadata about the pathology.

| Key | Description | Example |
| :--- | :--- | :--- |
| `pathology` | Unique identifier (slug) | `pathology:tachpm` |
| `title` | English display name | `title:Atrial tachycardia` |
| `name` | Russian display name (UTF-8) | `name:Предсердная тахикардия` |
| `leads` | Total number of lead blocks in this file | `leads:12` |
| `markers` | (Optional) Common ECG landmark indices | `markers:120:P_START,225:R_PEAK` |

### 2.2 Lead Block
One block per lead (typically 12 blocks total).

| Key | Description | Example |
| :--- | :--- | :--- |
| `lead` | Lead identifier (I, II, III, aVR, aVL, aVF, V1-V6) | `lead:II` |
| `count` | Number of integers in the `points` field | `count:2782` |
| `points` | Comma-separated raw ADC samples | `points:1024,1024,1025,...` |

## 3. Data Semantics

*   **Baseline**: All `points` are centered on an isoelectric baseline of **1024**.
*   **Sample Values**: Signed 32-bit integers (typically within `int16` range).
*   **Missing Leads**: If a lead is missing (e.g., in the `emd` pathology), its entire block is omitted.
*   **Canonical Order**: `I, II, III, aVR, aVL, aVF, V1, V2, V3, V4, V5, V6`.

## 4. Key Point Markers
Markers in the header use the format `index:TYPE`.
*   **Index**: 0-based sample index.
*   **Types**:

| Type | Description |
| :--- | :--- |
| `P_START` | Start of the P wave |
| `P_PEAK` | Peak of the P wave |
| `P_END` | End of the P wave |
| `QRS_START` | Start of the QRS complex |
| `Q_PEAK` | Peak of the Q wave |
| `R_PEAK` | Peak of the R wave |
| `S_PEAK` | Peak of the S wave |
| `QRS_END` | End of the QRS complex |
| `T_START` | Start of the T wave |
| `T_PEAK` | Peak of the T wave |
| `T_END` | End of the T wave |

## 5. Usage Example

```text
pathology:normal
title:Normal Sinus Rhythm
name:Нормальный синусовый ритм
leads:12
markers:100:P_START,125:P_PEAK,140:P_END,180:QRS_START,195:R_PEAK,210:QRS_END

lead:I
count:1000
points:1024,1024,1024,1024,1025,1027,...

lead:II
count:1000
points:1024,1024,1024,1026,1029,1035,...

...
```
