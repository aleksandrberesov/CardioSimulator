#!/usr/bin/env python3
"""Generate Courses.zip — a course bundle for the Course Constructor.

Mirrors `build_pathologies.py`: deterministic, no third-party deps. The
output conforms to `docs/course-format.md` and parses cleanly with
`domain/CourseParser.kt`:

* UTF-8, no BOM; LF line endings everywhere.
* `manifest.txt` -> per-course `course.txt` -> per-lecture
  `<lecture-id>.<lang>.md` (one file per language: en + ru).
* Rich bodies use KaTeX math plus the two custom fenced blocks the
  format defines: ```ecg (embedded trace) and ```table (quiz/reference).

Every ```ecg block references a real `PathologyEntry.id`; the build
validates each id against the live pathology manifest and fails loudly
on a typo so the bundle can never ship a dangling embed.
"""

import os
import re
import sys
import zipfile

# --- paths -----------------------------------------------------------------
HERE = os.path.dirname(os.path.abspath(__file__))
OUT_ZIP = os.path.join(HERE, "Courses.zip")
PATHOLOGY_MANIFEST = r"C:\VLN_Project\Data\Data\Pathologies\manifest.txt"
CREATED = "2026-05-29T12:00:00"
VALID_LEADS = {"I", "II", "III", "aVR", "aVL", "aVF",
               "V1", "V2", "V3", "V4", "V5", "V6"}


def valid_pathology_ids():
    """Set of pathology slugs declared in the live dataset manifest."""
    ids = set()
    with open(PATHOLOGY_MANIFEST, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"pathology:([^;]+);", line)
            if m:
                ids.add(m.group(1))
    return ids


# A minimal, valid PQRST reference diagram dropped into each course's
# assets/ folder and referenced from its first lecture. Opaque to the
# parser (see course-format.md §8) but exercises the assets path.
def pqrst_svg(caption):
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="420" height="180" viewBox="0 0 420 180">
  <rect width="420" height="180" fill="#ffffff"/>
  <g stroke="#f0c0c0" stroke-width="1">
    <line x1="0" y1="36" x2="420" y2="36"/>
    <line x1="0" y1="72" x2="420" y2="72"/>
    <line x1="0" y1="108" x2="420" y2="108"/>
    <line x1="0" y1="144" x2="420" y2="144"/>
  </g>
  <polyline fill="none" stroke="#101010" stroke-width="2"
    points="0,108 40,108 60,96 80,108 110,108 118,112 126,60 134,140 142,108 200,108 230,86 260,108 420,108"/>
  <g font-family="sans-serif" font-size="12" fill="#404040">
    <text x="58" y="90">P</text>
    <text x="123" y="52">R</text>
    <text x="115" y="128">Q</text>
    <text x="137" y="156">S</text>
    <text x="240" y="78">T</text>
  </g>
  <text x="6" y="170" font-family="sans-serif" font-size="11" fill="#808080">{caption}</text>
</svg>
"""


def lecture_file(lid, order, title, body):
    """Assemble a `<lecture-id>.<lang>.md` exactly as serializeLecture would."""
    head = (
        "---\n"
        f"id: {lid}\n"
        f"order: {order}\n"
        f"title: {title}\n"
        "schemaVersion: 1\n"
        "---\n\n"
    )
    return head + body.strip("\n") + "\n"


# ===========================================================================
# Course content. Each lecture body is plain Markdown (no f-strings, so
# KaTeX braces and backtick fences stay literal).
# ===========================================================================

COURSES = []

# --- Course 1: AV conduction blocks ---------------------------------------
c1_l1_en = """
# First-Degree AV Block

First-degree atrioventricular (AV) block is a **conduction delay**, not a
true block: every atrial impulse still reaches the ventricles, but
transit through the AV node is slowed.

## Diagnostic criteria

- **PR interval > 200 ms** (more than one large square at 25 mm/s).
- The PR interval is *constant* from beat to beat.
- Every P wave is followed by a QRS — conduction stays 1:1.

The PR interval is measured from the onset of the P wave to the onset of
the QRS complex:

$$
PR = t_{\\text{QRS onset}} - t_{\\text{P onset}}
$$

A normal value is $120\\text{–}200\\ \\text{ms}$; anything longer defines the block.

![PQRST reference complex](assets/pqrst.svg)

```ecg
pathology: 1abblock
lead: II
caption: First-degree AV block — prolonged but constant PR interval (lead II)
```

## Clinical note

Isolated first-degree block is usually benign and is common in athletes
from high vagal tone. It seldom needs treatment, but a markedly long PR
can be the earliest sign of progressive conduction-system disease.

```table
id: av-block-degrees
editable: false
---
| Degree | Hallmark                         | Risk          |
|--------|----------------------------------|---------------|
| 1°     | PR > 200 ms, all P conduct       | Low           |
| 2°     | Some P waves dropped             | Variable      |
| 3°     | No P–QRS relationship            | High          |
```
"""

c1_l1_ru = """
# АВ-блокада I степени

Атриовентрикулярная (АВ) блокада I степени — это **замедление
проведения**, а не истинная блокада: каждый предсердный импульс
по-прежнему достигает желудочков, но проходит через АВ-узел медленнее.

## Диагностические критерии

- **Интервал PR > 200 мс** (более одной большой клетки при 25 мм/с).
- Интервал PR *постоянен* от комплекса к комплексу.
- За каждым зубцом P следует QRS — проведение остаётся 1:1.

Интервал PR измеряют от начала зубца P до начала комплекса QRS:

$$
PR = t_{\\text{начало QRS}} - t_{\\text{начало P}}
$$

Норма — $120\\text{–}200\\ \\text{мс}$; большие значения определяют блокаду.

![Эталонный комплекс PQRST](assets/pqrst.svg)

```ecg
pathology: 1abblock
lead: II
caption: АВ-блокада I степени — удлинённый, но постоянный интервал PR (отведение II)
```

## Клиническая заметка

Изолированная блокада I степени обычно доброкачественна и часто
встречается у спортсменов из-за высокого тонуса блуждающего нерва.
Лечение требуется редко, но заметно удлинённый PR может быть ранним
признаком прогрессирующего поражения проводящей системы.

```table
id: av-block-degrees
editable: false
---
| Степень | Признак                          | Риск       |
|---------|----------------------------------|------------|
| I       | PR > 200 мс, все P проводятся    | Низкий     |
| II      | Часть зубцов P выпадает          | Переменный |
| III     | Нет связи P–QRS                  | Высокий    |
```
"""

c1_l2_en = """
# Second-Degree AV Block

In second-degree block **some** atrial impulses fail to reach the
ventricles, so there are more P waves than QRS complexes.

## Mobitz type I (Wenckebach)

- **Progressive PR prolongation** until a P wave is dropped.
- The RR interval *shortens* just before the pause.
- Usually nodal and generally benign.

```ecg
pathology: 2abblock1
lead: II
caption: Mobitz I — PR lengthens until a non-conducted P wave (lead II)
```

## Mobitz type II

- The PR interval stays **constant**, then a P wave is suddenly dropped.
- The lesion is infranodal (His–Purkinje) — higher risk of progressing
  to complete block, especially with a wide QRS or bundle branch block.

```ecg
pathology: 2abblock2
lead: aVF
caption: Mobitz II — constant PR with an abrupt dropped beat (lead aVF)
```

```ecg
pathology: 2avblock2RBBB
lead: V1
caption: Mobitz II with right bundle branch block (lead V1)
```

High-grade (advanced) block drops two or more consecutive P waves:

```ecg
pathology: 2abblock2hg
lead: II
caption: High-grade AV block — multiple consecutive non-conducted P waves
```

## Quiz

Fill in the conduction ratio (P : QRS) you would expect for each pattern.

```table
id: conduction-ratios
editable: true
---
| Pattern              | Conduction ratio |
|----------------------|------------------|
| 3:2 Wenckebach       |                  |
| Fixed 2:1 block      |                  |
| High-grade block     |                  |
```
"""

c1_l2_ru = """
# АВ-блокада II степени

При блокаде II степени **часть** предсердных импульсов не достигает
желудочков, поэтому зубцов P больше, чем комплексов QRS.

## Мобитц тип I (Венкебах)

- **Прогрессирующее удлинение PR** до выпадения зубца P.
- Интервал RR *укорачивается* непосредственно перед паузой.
- Обычно узловой уровень и в целом доброкачественный.

```ecg
pathology: 2abblock1
lead: II
caption: Мобитц I — PR удлиняется до непроведённого зубца P (отведение II)
```

## Мобитц тип II

- Интервал PR остаётся **постоянным**, затем зубец P внезапно выпадает.
- Уровень поражения ниже узла (система Гиса–Пуркинье) — выше риск
  перехода в полную блокаду, особенно при широком QRS или блокаде ножки.

```ecg
pathology: 2abblock2
lead: aVF
caption: Мобитц II — постоянный PR с внезапно выпавшим комплексом (отведение aVF)
```

```ecg
pathology: 2avblock2RBBB
lead: V1
caption: Мобитц II с блокадой правой ножки пучка Гиса (отведение V1)
```

При далеко зашедшей блокаде выпадают два и более зубца P подряд:

```ecg
pathology: 2abblock2hg
lead: II
caption: Далеко зашедшая АВ-блокада — несколько непроведённых зубцов P подряд
```

## Задание

Впишите ожидаемое соотношение проведения (P : QRS) для каждого случая.

```table
id: conduction-ratios
editable: true
---
| Паттерн              | Соотношение      |
|----------------------|------------------|
| Венкебах 3:2         |                  |
| Фиксированная 2:1    |                  |
| Далеко зашедшая      |                  |
```
"""

c1_l3_en = """
# Third-Degree (Complete) AV Block

In complete block **no** atrial impulses reach the ventricles. The atria
and ventricles beat independently — AV dissociation driven by a slower
escape rhythm below the level of the block.

## Hallmarks

- P waves and QRS complexes are each **regular but unrelated**: the P–P
  and R–R intervals are constant, yet the PR interval varies randomly.
- Atrial rate is faster than the ventricular escape rate.
- A junctional escape gives a narrow QRS ($\\approx 40\\text{–}60$ bpm); a
  ventricular escape gives a wide QRS ($\\approx 20\\text{–}40$ bpm).

```ecg
pathology: 3abblock
lead: II
caption: Complete heart block — independent atrial and ventricular activity
```

## Contrast: sinoatrial block

Do not confuse AV dissociation with **sinoatrial (SA) block**, where the
fault is impulse *formation or exit* at the SA node. There the whole
P–QRS–T drops out together, rather than P waves marching through an
unrelated ventricular rhythm.

```ecg
pathology: sablock
lead: II
caption: Sinoatrial block — a whole P–QRS–T cycle is missing
```

```table
id: escape-rates
editable: false
---
| Escape focus | QRS width | Typical rate |
|--------------|-----------|--------------|
| Junctional   | Narrow    | 40–60 bpm    |
| Ventricular  | Wide      | 20–40 bpm    |
```
"""

c1_l3_ru = """
# АВ-блокада III степени (полная)

При полной блокаде **ни один** предсердный импульс не достигает
желудочков. Предсердия и желудочки сокращаются независимо — это
АВ-диссоциация с более медленным выскальзывающим ритмом ниже уровня
блокады.

## Ключевые признаки

- Зубцы P и комплексы QRS каждый **регулярны, но не связаны**: интервалы
  P–P и R–R постоянны, тогда как PR меняется случайно.
- Частота предсердий выше частоты выскальзывающего ритма желудочков.
- Узловое выскальзывание даёт узкий QRS ($\\approx 40\\text{–}60$ уд/мин);
  желудочковое — широкий QRS ($\\approx 20\\text{–}40$ уд/мин).

```ecg
pathology: 3abblock
lead: II
caption: Полная АВ-блокада — независимая активность предсердий и желудочков
```

## Сравнение: синоатриальная блокада

Не путайте АВ-диссоциацию с **синоатриальной (СА) блокадой**, при которой
нарушено *образование или выход* импульса в синусовом узле. Там выпадает
весь цикл P–QRS–T целиком, а не зубцы P проходят на фоне несвязанного
желудочкового ритма.

```ecg
pathology: sablock
lead: II
caption: Синоатриальная блокада — выпадает целый цикл P–QRS–T
```

```table
id: escape-rates
editable: false
---
| Очаг замещения | Ширина QRS | Частота      |
|----------------|------------|--------------|
| Узловой        | Узкий      | 40–60 уд/мин |
| Желудочковый   | Широкий    | 20–40 уд/мин |
```
"""

COURSES.append({
    "id": "av-blocks",
    "title_en": "AV Conduction Blocks",
    "name_ru": "АВ-блокады",
    "authors": "CardioSimulator Faculty",
    "languages": ["en", "ru"],
    "asset": ("pqrst.svg", pqrst_svg("PQRST reference complex")),
    "lectures": [
        {"id": "01-first-degree", "order": 1,
         "title_en": "First-Degree AV Block", "name_ru": "АВ-блокада I степени",
         "body_en": c1_l1_en, "body_ru": c1_l1_ru},
        {"id": "02-second-degree", "order": 2,
         "title_en": "Second-Degree AV Block", "name_ru": "АВ-блокада II степени",
         "body_en": c1_l2_en, "body_ru": c1_l2_ru},
        {"id": "03-third-degree", "order": 3,
         "title_en": "Third-Degree AV Block", "name_ru": "АВ-блокада III степени",
         "body_en": c1_l3_en, "body_ru": c1_l3_ru},
    ],
})

# --- Course 2: Localizing myocardial infarction ---------------------------
c2_l1_en = """
# Localizing Anterior MI

ST-segment elevation across the **precordial leads** points to the
territory of the left anterior descending (LAD) artery.

## Lead groups

- **Septal:** V1–V2
- **Anterior:** V3–V4
- **Lateral:** V5–V6, I, aVL

ST elevation is measured at the J point relative to the TP baseline:

$$
\\Delta ST = V_{J} - V_{\\text{baseline}}
$$

![PQRST reference complex](assets/pqrst.svg)

Anteroseptal infarction involves V1–V4:

```ecg
pathology: anteroseptalmi
lead: V2
caption: Anteroseptal MI — ST elevation in V1–V4 (lead V2)
```

A more focal anterior MI centres on V3–V4:

```ecg
pathology: anteriormi
lead: V3
caption: Anterior MI — ST elevation maximal in V3–V4
```

When injury extends laterally, V5–V6 / I / aVL join in:

```ecg
pathology: anterolatmi
lead: V5
caption: Anterolateral MI — anterior plus lateral ST elevation
```

```table
id: anterior-leads
editable: false
---
| Territory     | Leads        | Culprit          |
|---------------|--------------|------------------|
| Septal        | V1–V2        | LAD (septal)     |
| Anterior      | V3–V4        | LAD              |
| Anterolateral | V3–V6, I, aVL| LAD / diagonal   |
```
"""

c2_l1_ru = """
# Локализация переднего инфаркта

Подъём сегмента ST в **грудных отведениях** указывает на бассейн
передней нисходящей артерии (ПНА / LAD).

## Группы отведений

- **Перегородочные:** V1–V2
- **Передние:** V3–V4
- **Боковые:** V5–V6, I, aVL

Подъём ST измеряют в точке J относительно изолинии TP:

$$
\\Delta ST = V_{J} - V_{\\text{изолиния}}
$$

![Эталонный комплекс PQRST](assets/pqrst.svg)

Переднеперегородочный инфаркт захватывает V1–V4:

```ecg
pathology: anteroseptalmi
lead: V2
caption: Переднеперегородочный ИМ — подъём ST в V1–V4 (отведение V2)
```

Более локальный передний ИМ концентрируется в V3–V4:

```ecg
pathology: anteriormi
lead: V3
caption: Передний ИМ — максимальный подъём ST в V3–V4
```

При распространении на боковую стенку подключаются V5–V6 / I / aVL:

```ecg
pathology: anterolatmi
lead: V5
caption: Переднебоковой ИМ — подъём ST в передних и боковых отведениях
```

```table
id: anterior-leads
editable: false
---
| Область        | Отведения     | Артерия          |
|----------------|---------------|------------------|
| Перегородка    | V1–V2         | ПНА (септальные) |
| Передняя       | V3–V4         | ПНА              |
| Переднебоковая | V3–V6, I, aVL | ПНА / диагональ  |
```
"""

c2_l2_en = """
# Inferior and Posterior MI

## Inferior wall

The inferior leads **II, III and aVF** view the diaphragmatic surface,
usually supplied by the right coronary artery (RCA).

```ecg
pathology: inf
lead: aVF
caption: Inferior MI — ST elevation in II, III, aVF (lead aVF)
```

Reciprocal ST *depression* in I and aVL supports the diagnosis.

## Posterior wall

The standard 12 leads have no electrode directly over the posterior wall,
so it presents as a **mirror image** in V1–V3: tall R waves, horizontal
ST depression and upright T waves.

```ecg
pathology: posteriormi
lead: V2
caption: Posterior MI — tall R and ST depression in V1–V3 (mirror image)
```

Lateral extension shows up in I, aVL, V5–V6:

```ecg
pathology: lateralmi
lead: V6
caption: Lateral MI — ST changes in I, aVL, V5–V6
```

```table
id: inferior-recognition
editable: true
---
| Question                                   | Answer |
|--------------------------------------------|--------|
| Which leads show inferior injury?          |        |
| Which leads show reciprocal depression?    |        |
| Posterior MI mirrors into which leads?     |        |
```
"""

c2_l2_ru = """
# Нижний и задний инфаркт

## Нижняя стенка

Нижние отведения **II, III и aVF** смотрят на диафрагмальную поверхность,
которую обычно кровоснабжает правая коронарная артерия (ПКА / RCA).

```ecg
pathology: inf
lead: aVF
caption: Нижний ИМ — подъём ST в II, III, aVF (отведение aVF)
```

Реципрокная *депрессия* ST в I и aVL подтверждает диагноз.

## Задняя стенка

В стандартных 12 отведениях нет электрода прямо над задней стенкой,
поэтому она проявляется **зеркально** в V1–V3: высокие зубцы R,
горизонтальная депрессия ST и положительные зубцы T.

```ecg
pathology: posteriormi
lead: V2
caption: Задний ИМ — высокий R и депрессия ST в V1–V3 (зеркальное отражение)
```

Распространение на боковую стенку видно в I, aVL, V5–V6:

```ecg
pathology: lateralmi
lead: V6
caption: Боковой ИМ — изменения ST в I, aVL, V5–V6
```

```table
id: inferior-recognition
editable: true
---
| Вопрос                                       | Ответ |
|----------------------------------------------|-------|
| Какие отведения показывают нижнее повреждение?|       |
| Где видна реципрокная депрессия?             |       |
| В каких отведениях зеркалит задний ИМ?       |       |
```
"""

c2_l3_en = """
# Special Patterns

Two patterns deserve separate study because the textbook "ST elevation"
rule does not apply cleanly.

## Wellens' syndrome

Deeply inverted or biphasic T waves in **V2–V3**, recorded when the
patient is pain-free, signal a critical proximal LAD stenosis. The ECG
can look almost normal between episodes, yet the lesion is high-risk.

```ecg
pathology: wellens
lead: V3
caption: Wellens' syndrome — biphasic / deep T-wave inversion in V2–V3
```

## MI in the presence of LBBB

Left bundle branch block distorts the ST–T segment, so primary
repolarisation changes are hard to read. Apply the **Sgarbossa
criteria** — concordant ST elevation, concordant ST depression in
V1–V3, or excessively discordant ST elevation.

```ecg
pathology: lbbbmi
lead: V2
caption: Acute MI complicating LBBB — assess with Sgarbossa criteria
```

```table
id: sgarbossa
editable: false
---
| Criterion                            | Points |
|--------------------------------------|--------|
| Concordant ST elevation ≥ 1 mm       | 5      |
| Concordant ST depression V1–V3       | 3      |
| Discordant ST elevation ≥ 5 mm       | 2      |
```
"""

c2_l3_ru = """
# Особые паттерны

Два паттерна стоит разобрать отдельно, потому что школьное правило
«подъём ST» к ним применимо не напрямую.

## Синдром Велленса

Глубоко инвертированные или двухфазные зубцы T в **V2–V3**,
зарегистрированные вне болевого приступа, говорят о критическом
проксимальном стенозе ПНА. Между эпизодами ЭКГ может выглядеть почти
нормальной, но поражение высокого риска.

```ecg
pathology: wellens
lead: V3
caption: Синдром Велленса — двухфазная / глубокая инверсия T в V2–V3
```

## Инфаркт на фоне блокады ЛНПГ

Блокада левой ножки искажает сегмент ST–T, поэтому первичные изменения
реполяризации читать трудно. Применяют **критерии Сгарбосса** —
конкордантный подъём ST, конкордантная депрессия ST в V1–V3 или
чрезмерно дискордантный подъём ST.

```ecg
pathology: lbbbmi
lead: V2
caption: Острый ИМ на фоне блокады ЛНПГ — оценка по критериям Сгарбосса
```

```table
id: sgarbossa
editable: false
---
| Критерий                              | Баллы |
|---------------------------------------|-------|
| Конкордантный подъём ST ≥ 1 мм        | 5     |
| Конкордантная депрессия ST в V1–V3    | 3     |
| Дискордантный подъём ST ≥ 5 мм        | 2     |
```
"""

COURSES.append({
    "id": "mi-localization",
    "title_en": "Localizing Myocardial Infarction",
    "name_ru": "Локализация инфаркта миокарда",
    "authors": "CardioSimulator Faculty",
    "languages": ["en", "ru"],
    "asset": ("pqrst.svg", pqrst_svg("PQRST reference complex")),
    "lectures": [
        {"id": "01-anterior", "order": 1,
         "title_en": "Anterior MI", "name_ru": "Передний инфаркт",
         "body_en": c2_l1_en, "body_ru": c2_l1_ru},
        {"id": "02-inferior-posterior", "order": 2,
         "title_en": "Inferior & Posterior MI", "name_ru": "Нижний и задний инфаркт",
         "body_en": c2_l2_en, "body_ru": c2_l2_ru},
        {"id": "03-special-patterns", "order": 3,
         "title_en": "Special Patterns", "name_ru": "Особые паттерны",
         "body_en": c2_l3_en, "body_ru": c2_l3_ru},
    ],
})

# --- Course 3: Tachyarrhythmias -------------------------------------------
c3_l1_en = """
# Narrow-Complex Tachycardias

A narrow QRS ($< 120\\ \\text{ms}$) means the ventricles are activated through
the normal His–Purkinje system, so the arrhythmia arises **at or above**
the AV node (supraventricular).

## Regular

Supraventricular tachycardia (SVT) is regular with hidden or retrograde
P waves:

```ecg
pathology: tachsv
lead: II
caption: Supraventricular tachycardia — regular narrow complexes
```

AV nodal reentrant tachycardia uses a re-entry circuit within the node:

```ecg
pathology: avreentrtach
lead: II
caption: AV nodal reentrant tachycardia (AVNRT)
```

Atrial flutter shows sawtooth F waves, classically at ~300/min with 2:1
conduction:

```ecg
pathology: atrflu
lead: II
caption: Atrial flutter — sawtooth flutter waves
```

## Irregular

Atrial fibrillation is irregularly irregular with no discrete P waves:

```ecg
pathology: fib
lead: II
caption: Atrial fibrillation — irregularly irregular, no P waves
```

```table
id: narrow-rhythm
editable: true
---
| Rhythm   | Regular? | P-wave morphology |
|----------|----------|-------------------|
| SVT      |          |                   |
| Flutter  |          |                   |
| AF       |          |                   |
```
"""

c3_l1_ru = """
# Тахикардии с узким комплексом

Узкий QRS ($< 120\\ \\text{мс}$) означает, что желудочки активируются через
нормальную систему Гиса–Пуркинье, поэтому аритмия возникает **на уровне
или выше** АВ-узла (наджелудочковая).

## Регулярные

Наджелудочковая тахикардия (НЖТ) регулярна, зубцы P скрыты или
ретроградны:

```ecg
pathology: tachsv
lead: II
caption: Наджелудочковая тахикардия — регулярные узкие комплексы
```

АВ-узловая реципрокная тахикардия использует круг re-entry внутри узла:

```ecg
pathology: avreentrtach
lead: II
caption: АВ-узловая реципрокная тахикардия (АВУРТ)
```

Трепетание предсердий даёт пилообразные волны F, классически ~300/мин с
проведением 2:1:

```ecg
pathology: atrflu
lead: II
caption: Трепетание предсердий — пилообразные волны
```

## Нерегулярные

Фибрилляция предсердий нерегулярно нерегулярна, дискретных зубцов P нет:

```ecg
pathology: fib
lead: II
caption: Фибрилляция предсердий — нерегулярно нерегулярная, без зубцов P
```

```table
id: narrow-rhythm
editable: true
---
| Ритм     | Регулярный? | Морфология P |
|----------|-------------|--------------|
| НЖТ      |             |              |
| Трепетание|            |              |
| ФП       |             |              |
```
"""

c3_l2_en = """
# Wide-Complex Tachycardias

A wide QRS ($\\ge 120\\ \\text{ms}$) with a fast rate is **ventricular
tachycardia until proven otherwise** — treat accordingly when the patient
is unstable.

## Monomorphic VT

Uniform, broad complexes at a regular fast rate:

```ecg
pathology: vt
lead: II
caption: Monomorphic ventricular tachycardia — uniform wide complexes
```

```ecg
pathology: tach
lead: V1
caption: Ventricular tachycardia viewed in a precordial lead
```

## Ventricular flutter

A near-sinusoidal waveform at ~250–300/min, the transitional rhythm
between rapid VT and fibrillation:

```ecg
pathology: ventrflut
lead: II
caption: Ventricular flutter — sinusoidal, ~250–300/min
```

Features that favour VT over SVT with aberrancy include AV dissociation,
fusion/capture beats, and very broad QRS.

```table
id: vt-vs-svt
editable: false
---
| Feature           | Favours VT |
|-------------------|------------|
| AV dissociation   | Yes        |
| Capture/fusion    | Yes        |
| QRS > 140 ms      | Yes        |
```
"""

c3_l2_ru = """
# Тахикардии с широким комплексом

Широкий QRS ($\\ge 120\\ \\text{мс}$) с высокой частотой — это **желудочковая
тахикардия, пока не доказано обратное**; при нестабильности лечить
соответственно.

## Мономорфная ЖТ

Однообразные широкие комплексы с регулярной высокой частотой:

```ecg
pathology: vt
lead: II
caption: Мономорфная желудочковая тахикардия — однообразные широкие комплексы
```

```ecg
pathology: tach
lead: V1
caption: Желудочковая тахикардия в грудном отведении
```

## Трепетание желудочков

Почти синусоидальная кривая ~250–300/мин — переходный ритм между быстрой
ЖТ и фибрилляцией:

```ecg
pathology: ventrflut
lead: II
caption: Трепетание желудочков — синусоидальная форма, ~250–300/мин
```

В пользу ЖТ (против НЖТ с аберрацией) говорят АВ-диссоциация, сливные и
захваченные комплексы, очень широкий QRS.

```table
id: vt-vs-svt
editable: false
---
| Признак           | В пользу ЖТ |
|-------------------|-------------|
| АВ-диссоциация    | Да          |
| Захват/слияние    | Да          |
| QRS > 140 мс      | Да          |
```
"""

c3_l3_en = """
# Malignant Ventricular Rhythms

These rhythms are immediately life-threatening and demand defibrillation
or correction of the trigger.

## Torsades de pointes

A polymorphic VT in which the QRS axis "twists" around the baseline,
arising on a background of a **prolonged QT interval**. Correct
magnesium, potassium and offending drugs.

```ecg
pathology: tacht
lead: II
caption: Torsades de pointes — QRS twisting around the baseline
```

## Ventricular fibrillation

Chaotic, disorganised electrical activity with no effective contraction —
the rhythm of cardiac arrest:

```ecg
pathology: fibv
lead: II
caption: Ventricular fibrillation — chaotic, no organised QRS
```

The QT that predisposes to torsades is rate-corrected with Bazett's
formula:

$$
QTc = \\frac{QT}{\\sqrt{RR}}
$$

```table
id: malignant-response
editable: false
---
| Rhythm    | Pulse? | First action            |
|-----------|--------|-------------------------|
| Torsades  | Maybe  | IV magnesium / defib    |
| VF        | No     | Immediate defibrillation|
```
"""

c3_l3_ru = """
# Злокачественные желудочковые ритмы

Эти ритмы непосредственно угрожают жизни и требуют дефибрилляции или
устранения провоцирующего фактора.

## Пируэтная тахикардия (torsades de pointes)

Полиморфная ЖТ, при которой ось QRS «закручивается» вокруг изолинии,
возникает на фоне **удлинённого интервала QT**. Корректируют магний,
калий и отменяют виновные препараты.

```ecg
pathology: tacht
lead: II
caption: Пируэтная тахикардия — закручивание QRS вокруг изолинии
```

## Фибрилляция желудочков

Хаотичная неупорядоченная электрическая активность без эффективного
сокращения — ритм остановки сердца:

```ecg
pathology: fibv
lead: II
caption: Фибрилляция желудочков — хаотична, без организованного QRS
```

QT, предрасполагающий к torsades, корригируют по частоте формулой Базетта:

$$
QTc = \\frac{QT}{\\sqrt{RR}}
$$

```table
id: malignant-response
editable: false
---
| Ритм      | Пульс? | Первое действие         |
|-----------|--------|-------------------------|
| Torsades  | Иногда | В/в магний / дефибрилляция|
| ФЖ        | Нет    | Немедленная дефибрилляция|
```
"""

COURSES.append({
    "id": "tachyarrhythmias",
    "title_en": "Tachyarrhythmias",
    "name_ru": "Тахиаритмии",
    "authors": "CardioSimulator Faculty",
    "languages": ["en", "ru"],
    "asset": ("pqrst.svg", pqrst_svg("PQRST reference complex")),
    "lectures": [
        {"id": "01-narrow-complex", "order": 1,
         "title_en": "Narrow-Complex Tachycardias", "name_ru": "Тахикардии с узким комплексом",
         "body_en": c3_l1_en, "body_ru": c3_l1_ru},
        {"id": "02-wide-complex", "order": 2,
         "title_en": "Wide-Complex Tachycardias", "name_ru": "Тахикардии с широким комплексом",
         "body_en": c3_l2_en, "body_ru": c3_l2_ru},
        {"id": "03-malignant", "order": 3,
         "title_en": "Malignant Ventricular Rhythms", "name_ru": "Злокачественные желудочковые ритмы",
         "body_en": c3_l3_en, "body_ru": c3_l3_ru},
    ],
})

# --- Course 4: Bundle branch blocks & pre-excitation ----------------------
c4_l1_en = """
# Right Bundle Branch Block

When the right bundle fails, the right ventricle is depolarised late and
indirectly, widening the QRS to $\\ge 120\\ \\text{ms}$.

## Recognition

- **rSR'** ("rabbit ears") in V1–V2.
- Wide, slurred S wave in I, V5–V6.
- QRS $\\ge 120\\ \\text{ms}$ (incomplete if 110–120 ms).

![PQRST reference complex](assets/pqrst.svg)

```ecg
pathology: synbrg
lead: V1
caption: RBBB — rSR' pattern in V1
```

Incomplete RBBB shows the same morphology with a QRS still under 120 ms:

```ecg
pathology: incomprbbb
lead: V1
caption: Incomplete RBBB — rSR' with QRS 110–120 ms
```

```table
id: rbbb-criteria
editable: true
---
| Feature           | RBBB value |
|-------------------|------------|
| QRS duration      |            |
| V1 morphology     |            |
| Lead I S-wave     |            |
```
"""

c4_l1_ru = """
# Блокада правой ножки пучка Гиса

При отказе правой ножки правый желудочек деполяризуется поздно и
окольно, расширяя QRS до $\\ge 120\\ \\text{мс}$.

## Распознавание

- **rSR'** («кроличьи уши») в V1–V2.
- Широкий зазубренный зубец S в I, V5–V6.
- QRS $\\ge 120\\ \\text{мс}$ (неполная при 110–120 мс).

![Эталонный комплекс PQRST](assets/pqrst.svg)

```ecg
pathology: synbrg
lead: V1
caption: БПНПГ — паттерн rSR' в V1
```

Неполная БПНПГ даёт ту же морфологию при QRS менее 120 мс:

```ecg
pathology: incomprbbb
lead: V1
caption: Неполная БПНПГ — rSR' с QRS 110–120 мс
```

```table
id: rbbb-criteria
editable: true
---
| Признак           | Значение БПНПГ |
|-------------------|----------------|
| Длительность QRS  |                |
| Морфология в V1   |                |
| Зубец S в I       |                |
```
"""

c4_l2_en = """
# Left Bundle Branch Block

Left bundle branch block reverses septal activation, producing a broad,
notched QRS and **secondary** ST–T changes that mimic ischaemia.

## Recognition

- Broad, monophasic R waves in I, V5–V6 (often notched).
- Deep QS or rS in V1.
- QRS $\\ge 120\\ \\text{ms}$, with discordant ST–T (opposite the main QRS
  deflection).

```ecg
pathology: synblg
lead: V6
caption: LBBB — broad notched R wave in V6
```

```ecg
pathology: synblg
lead: V1
caption: LBBB — deep QS complex in V1
```

Because LBBB already distorts repolarisation, a new LBBB with ischaemic
symptoms is treated as an acute coronary equivalent.

```table
id: bbb-compare
editable: false
---
| Feature      | RBBB        | LBBB         |
|--------------|-------------|--------------|
| V1           | rSR'        | QS / rS      |
| V6           | wide S      | broad R      |
| Septal order | preserved   | reversed     |
```
"""

c4_l2_ru = """
# Блокада левой ножки пучка Гиса

Блокада левой ножки меняет направление активации перегородки, давая
широкий зазубренный QRS и **вторичные** изменения ST–T, имитирующие
ишемию.

## Распознавание

- Широкие монофазные зубцы R в I, V5–V6 (часто зазубренные).
- Глубокий QS или rS в V1.
- QRS $\\ge 120\\ \\text{мс}$, дискордантные ST–T (противоположны основному
  отклонению QRS).

```ecg
pathology: synblg
lead: V6
caption: БЛНПГ — широкий зазубренный зубец R в V6
```

```ecg
pathology: synblg
lead: V1
caption: БЛНПГ — глубокий комплекс QS в V1
```

Поскольку БЛНПГ сама искажает реполяризацию, впервые возникшая БЛНПГ с
ишемическими симптомами расценивается как эквивалент острого коронарного
синдрома.

```table
id: bbb-compare
editable: false
---
| Признак         | БПНПГ      | БЛНПГ       |
|-----------------|------------|-------------|
| V1              | rSR'       | QS / rS     |
| V6              | широкий S  | широкий R   |
| Порядок септ.   | сохранён   | обратный    |
```
"""

c4_l3_en = """
# Ventricular Pre-excitation (WPW)

In Wolff–Parkinson–White syndrome an accessory pathway (bundle of Kent)
lets the atrial impulse bypass the AV node, pre-exciting part of the
ventricle.

## The triad

- **Short PR interval** ($< 120\\ \\text{ms}$).
- **Delta wave** — a slurred QRS upstroke.
- **Widened QRS** from fusion of pathway and normal conduction.

```ecg
pathology: synwpw
lead: II
caption: WPW — short PR with a delta wave
```

The delta-wave polarity helps localise the accessory pathway. A right
lateral pathway, for example, shifts the early forces:

```ecg
pathology: wpwrightlat
lead: V1
caption: WPW, right lateral pathway — delta wave in V1
```

Pre-excitation matters most because it can support fast re-entry
(AVRT) — and atrial fibrillation conducting down a fast pathway is
dangerous.

```table
id: wpw-triad
editable: true
---
| Component   | Expected finding |
|-------------|------------------|
| PR interval |                  |
| QRS onset   |                  |
| QRS width   |                  |
```
"""

c4_l3_ru = """
# Преэкзитация желудочков (WPW)

При синдроме Вольфа–Паркинсона–Уайта дополнительный путь (пучок Кента)
позволяет предсердному импульсу обойти АВ-узел, преждевременно возбуждая
часть желудочка.

## Триада

- **Короткий интервал PR** ($< 120\\ \\text{мс}$).
- **Дельта-волна** — пологий подъём начала QRS.
- **Расширенный QRS** из-за слияния проведения по пути и нормального.

```ecg
pathology: synwpw
lead: II
caption: WPW — короткий PR с дельта-волной
```

Полярность дельта-волны помогает локализовать дополнительный путь.
Например, правый боковой путь смещает ранние силы:

```ecg
pathology: wpwrightlat
lead: V1
caption: WPW, правый боковой путь — дельта-волна в V1
```

Преэкзитация важна прежде всего потому, что поддерживает быстрый re-entry
(АВРТ), а фибрилляция предсердий с проведением по быстрому пути опасна.

```table
id: wpw-triad
editable: true
---
| Компонент      | Ожидаемая находка |
|----------------|-------------------|
| Интервал PR    |                   |
| Начало QRS     |                   |
| Ширина QRS     |                   |
```
"""

COURSES.append({
    "id": "conduction-preexcitation",
    "title_en": "Bundle Branch Blocks & Pre-excitation",
    "name_ru": "Блокады ножек пучка Гиса и преэкзитация",
    "authors": "CardioSimulator Faculty",
    "languages": ["en", "ru"],
    "asset": ("pqrst.svg", pqrst_svg("PQRST reference complex")),
    "lectures": [
        {"id": "01-rbbb", "order": 1,
         "title_en": "Right Bundle Branch Block", "name_ru": "Блокада правой ножки",
         "body_en": c4_l1_en, "body_ru": c4_l1_ru},
        {"id": "02-lbbb", "order": 2,
         "title_en": "Left Bundle Branch Block", "name_ru": "Блокада левой ножки",
         "body_en": c4_l2_en, "body_ru": c4_l2_ru},
        {"id": "03-wpw", "order": 3,
         "title_en": "Ventricular Pre-excitation (WPW)", "name_ru": "Преэкзитация желудочков (WPW)",
         "body_en": c4_l3_en, "body_ru": c4_l3_ru},
    ],
})

# --- Course 5: Chamber enlargement & hypertrophy --------------------------
c5_l1_en = """
# Atrial Enlargement

Atrial enlargement reshapes the **P wave**, whose first half is the right
atrium and second half the left.

## Right atrial enlargement

Tall, peaked P waves ($> 2.5\\ \\text{mm}$) in II, III, aVF — "P pulmonale".

```ecg
pathology: rigthatrhyper
lead: II
caption: Right atrial enlargement — tall peaked P wave (P pulmonale)
```

## Left atrial enlargement

A broad, notched P wave ($> 120\\ \\text{ms}$) in II and a deep terminal
negativity in V1 — "P mitrale".

```ecg
pathology: leftatrhyper
lead: II
caption: Left atrial enlargement — broad notched P wave (P mitrale)
```

## Biatrial enlargement

Both patterns combine — tall *and* wide P waves:

```ecg
pathology: biatrenlarg
lead: II
caption: Biatrial enlargement — tall and broad P wave
```

```table
id: atrial-p-wave
editable: false
---
| Chamber | Lead | P-wave change       |
|---------|------|---------------------|
| RA      | II   | tall, peaked > 2.5mm|
| LA      | V1   | deep terminal trough|
| Both    | II   | tall + wide         |
```
"""

c5_l1_ru = """
# Увеличение предсердий

Увеличение предсердий меняет форму **зубца P**: его первая половина —
правое предсердие, вторая — левое.

## Увеличение правого предсердия

Высокие заострённые зубцы P ($> 2{,}5\\ \\text{мм}$) в II, III, aVF —
«P-pulmonale».

```ecg
pathology: rigthatrhyper
lead: II
caption: Увеличение правого предсердия — высокий заострённый P (P-pulmonale)
```

## Увеличение левого предсердия

Широкий зазубренный зубец P ($> 120\\ \\text{мс}$) в II и глубокая конечная
негативность в V1 — «P-mitrale».

```ecg
pathology: leftatrhyper
lead: II
caption: Увеличение левого предсердия — широкий зазубренный P (P-mitrale)
```

## Увеличение обоих предсердий

Оба паттерна сочетаются — зубцы P высокие *и* широкие:

```ecg
pathology: biatrenlarg
lead: II
caption: Увеличение обоих предсердий — высокий и широкий зубец P
```

```table
id: atrial-p-wave
editable: false
---
| Камера | Отвед. | Изменение P          |
|--------|--------|----------------------|
| ПП     | II     | высокий, > 2,5 мм    |
| ЛП     | V1     | глубокий конечный спад|
| Оба    | II     | высокий + широкий    |
```
"""

c5_l2_en = """
# Ventricular Hypertrophy

Increased muscle mass shifts the QRS axis toward the hypertrophied side
and deepens or heightens the precordial voltages.

## Left ventricular hypertrophy

Use the Sokolow–Lyon voltage criterion:

$$
S_{V1} + R_{V5\\,\\text{or}\\,V6} > 35\\ \\text{mm}
$$

```ecg
pathology: synlvht
lead: V5
caption: LVH — tall R wave in V5 with strain pattern
```

## Right ventricular hypertrophy

Dominant R in V1, right-axis deviation, and a deep S in V6:

```ecg
pathology: synrvht
lead: V1
caption: RVH — dominant R wave in V1
```

A repolarisation "strain" pattern (down-sloping ST with asymmetric T
inversion) often accompanies advanced hypertrophy.

```table
id: lvh-vs-rvh
editable: true
---
| Feature      | LVH | RVH |
|--------------|-----|-----|
| Axis         |     |     |
| Tall R lead  |     |     |
| Deep S lead  |     |     |
```
"""

c5_l2_ru = """
# Гипертрофия желудочков

Увеличение мышечной массы смещает ось QRS в сторону гипертрофированного
желудочка и углубляет либо увеличивает грудные вольтажи.

## Гипертрофия левого желудочка

Вольтажный критерий Соколова–Лайона:

$$
S_{V1} + R_{V5\\,\\text{или}\\,V6} > 35\\ \\text{мм}
$$

```ecg
pathology: synlvht
lead: V5
caption: ГЛЖ — высокий зубец R в V5 с паттерном перегрузки
```

## Гипертрофия правого желудочка

Доминирующий R в V1, отклонение оси вправо и глубокий S в V6:

```ecg
pathology: synrvht
lead: V1
caption: ГПЖ — доминирующий зубец R в V1
```

Паттерн «перегрузки» при реполяризации (косонисходящий ST с асимметричной
инверсией T) часто сопровождает выраженную гипертрофию.

```table
id: lvh-vs-rvh
editable: true
---
| Признак       | ГЛЖ | ГПЖ |
|---------------|-----|-----|
| Ось           |     |     |
| Высокий R     |     |     |
| Глубокий S    |     |     |
```
"""

c5_l3_en = """
# Biventricular Hypertrophy

When both ventricles hypertrophy, their opposing forces partly cancel, so
the ECG can be deceptively unimpressive — or show a mixture of left- and
right-sided clues at once.

## Clues to look for

- Tall precordial voltages (LVH) **plus** right-axis deviation (RVH).
- Large equiphasic QRS complexes in the mid-precordial leads
  (Katz–Wachtel phenomenon).
- Signs of biatrial enlargement frequently coexist.

```ecg
pathology: biventrhyper
lead: V3
caption: Biventricular hypertrophy — large equiphasic complexes (Katz–Wachtel)
```

```ecg
pathology: biventrhyper
lead: V1
caption: Biventricular hypertrophy — right-sided voltage with left-sided clues
```

```table
id: biventricular
editable: false
---
| Sign                       | Points toward |
|----------------------------|---------------|
| Tall R in V5–V6            | LV            |
| Dominant R in V1 / RAD     | RV            |
| Equiphasic mid-precordium  | Both          |
```
"""

c5_l3_ru = """
# Гипертрофия обоих желудочков

Когда гипертрофируются оба желудочка, их противоположные силы частично
гасятся, поэтому ЭКГ может быть обманчиво невыразительной — или сразу
показывать смесь левых и правых признаков.

## На что смотреть

- Высокие грудные вольтажи (ГЛЖ) **плюс** отклонение оси вправо (ГПЖ).
- Большие равнофазные комплексы QRS в средних грудных отведениях
  (феномен Каца–Вахтеля).
- Часто сосуществуют признаки увеличения обоих предсердий.

```ecg
pathology: biventrhyper
lead: V3
caption: Гипертрофия обоих желудочков — большие равнофазные комплексы (Кац–Вахтель)
```

```ecg
pathology: biventrhyper
lead: V1
caption: Гипертрофия обоих желудочков — правые вольтажи с левыми признаками
```

```table
id: biventricular
editable: false
---
| Признак                       | Указывает на |
|-------------------------------|--------------|
| Высокий R в V5–V6             | ЛЖ           |
| Доминирующий R в V1 / ось→    | ПЖ           |
| Равнофазные в средних груд.   | Оба          |
```
"""

COURSES.append({
    "id": "chamber-enlargement",
    "title_en": "Chamber Enlargement & Hypertrophy",
    "name_ru": "Увеличение камер сердца и гипертрофия",
    "authors": "CardioSimulator Faculty",
    "languages": ["en", "ru"],
    "asset": ("pqrst.svg", pqrst_svg("PQRST reference complex")),
    "lectures": [
        {"id": "01-atrial", "order": 1,
         "title_en": "Atrial Enlargement", "name_ru": "Увеличение предсердий",
         "body_en": c5_l1_en, "body_ru": c5_l1_ru},
        {"id": "02-ventricular", "order": 2,
         "title_en": "Ventricular Hypertrophy", "name_ru": "Гипертрофия желудочков",
         "body_en": c5_l2_en, "body_ru": c5_l2_ru},
        {"id": "03-biventricular", "order": 3,
         "title_en": "Biventricular Hypertrophy", "name_ru": "Гипертрофия обоих желудочков",
         "body_en": c5_l3_en, "body_ru": c5_l3_ru},
    ],
})

# --- Course 6: Electrolyte & metabolic changes ----------------------------
c6_l1_en = """
# Potassium Disturbances

Potassium sets the resting membrane potential, so its derangements show
up first in repolarisation (the **T wave**) and conduction.

## Hyperkalemia

A progression with rising $[K^+]$:

1. Tall, peaked, narrow-based **T waves**.
2. Flattening P waves and PR prolongation.
3. QRS widening, ultimately a sine-wave pattern.

```ecg
pathology: synhy
lead: II
caption: Hyperkalemia — tall peaked T waves with broadening QRS
```

## Hypokalemia

Low $[K^+]$ does the opposite — repolarisation drags out:

- Flattened T waves and prominent **U waves**.
- ST depression and a long apparent QT.

```ecg
pathology: sinhypokal
lead: II
caption: Hypokalemia — flattened T with prominent U wave
```

```table
id: potassium
editable: true
---
| Disturbance  | T wave | Other clue   |
|--------------|--------|--------------|
| Hyperkalemia |        |              |
| Hypokalemia  |        |              |
```
"""

c6_l1_ru = """
# Нарушения калия

Калий задаёт потенциал покоя мембраны, поэтому его сдвиги проявляются
прежде всего в реполяризации (**зубец T**) и проведении.

## Гиперкалиемия

Динамика при росте $[K^+]$:

1. Высокие заострённые **зубцы T** с узким основанием.
2. Уплощение зубцов P и удлинение PR.
3. Расширение QRS вплоть до синусоидального паттерна.

```ecg
pathology: synhy
lead: II
caption: Гиперкалиемия — высокие заострённые T с расширением QRS
```

## Гипокалиемия

Низкий $[K^+]$ даёт обратное — реполяризация затягивается:

- Уплощённые зубцы T и выраженные **зубцы U**.
- Депрессия ST и удлинённый видимый QT.

```ecg
pathology: sinhypokal
lead: II
caption: Гипокалиемия — уплощённый T с выраженным зубцом U
```

```table
id: potassium
editable: true
---
| Нарушение     | Зубец T | Другой признак |
|---------------|---------|----------------|
| Гиперкалиемия |         |                |
| Гипокалиемия  |         |                |
```
"""

c6_l2_en = """
# Calcium Disturbances

Calcium chiefly governs the plateau phase of the action potential, so it
moves the **ST segment** and therefore the QT interval — with little
effect on T-wave shape.

## Hypercalcemia

A short ST segment shortens the QT:

```ecg
pathology: sinhypcal
lead: II
caption: Hypercalcemia — short ST segment, short QT
```

## Hypocalcemia

A long ST segment stretches the QT, with the T wave left largely
untouched:

```ecg
pathology: sinhypocal
lead: II
caption: Hypocalcemia — long ST segment, prolonged QT
```

Contrast this with potassium, which deforms the T wave itself. Calcium
moves the segment *before* the T wave.

```table
id: calcium
editable: false
---
| Disturbance   | ST segment | QT interval |
|---------------|------------|-------------|
| Hypercalcemia | short      | short       |
| Hypocalcemia  | long       | long        |
```
"""

c6_l2_ru = """
# Нарушения кальция

Кальций в основном управляет фазой плато потенциала действия, поэтому он
двигает **сегмент ST** и, следовательно, интервал QT, мало влияя на форму
зубца T.

## Гиперкальциемия

Короткий сегмент ST укорачивает QT:

```ecg
pathology: sinhypcal
lead: II
caption: Гиперкальциемия — короткий сегмент ST, короткий QT
```

## Гипокальциемия

Длинный сегмент ST растягивает QT, тогда как зубец T остаётся почти без
изменений:

```ecg
pathology: sinhypocal
lead: II
caption: Гипокальциемия — длинный сегмент ST, удлинённый QT
```

Сравните с калием, который деформирует сам зубец T. Кальций сдвигает
сегмент *перед* зубцом T.

```table
id: calcium
editable: false
---
| Нарушение      | Сегмент ST | Интервал QT |
|----------------|------------|-------------|
| Гиперкальциемия| короткий   | короткий    |
| Гипокальциемия | длинный    | длинный     |
```
"""

c6_l3_en = """
# Hypothermia and the Long-QT State

## Hypothermia

The hallmark is the **Osborn (J) wave** — a positive deflection at the
J point — together with bradycardia, tremor artifact and prolonged
intervals.

```ecg
pathology: sinhypotherm
lead: II
caption: Hypothermia — Osborn (J) wave at the J point
```

## Long-QT

A prolonged QT, whether congenital, drug-induced or electrolyte-driven,
is the substrate for torsades de pointes. Always rate-correct:

$$
QTc = \\frac{QT}{\\sqrt{RR}}
$$

```ecg
pathology: sinlongqt
lead: II
caption: Prolonged QT interval in sinus rhythm
```

A $QTc > 500\\ \\text{ms}$ markedly raises the risk of polymorphic VT.

```table
id: qtc-risk
editable: false
---
| QTc (ms) | Interpretation     |
|----------|--------------------|
| < 440    | Normal             |
| 440–500  | Borderline / long  |
| > 500    | High torsades risk |
```
"""

c6_l3_ru = """
# Гипотермия и состояние удлинённого QT

## Гипотермия

Отличительный признак — **волна Осборна (J-волна)**, положительное
отклонение в точке J, вместе с брадикардией, тремор-артефактом и
удлинёнными интервалами.

```ecg
pathology: sinhypotherm
lead: II
caption: Гипотермия — волна Осборна (J) в точке J
```

## Удлинённый QT

Удлинённый QT — врождённый, лекарственный или электролитный — является
субстратом для пируэтной тахикардии. Всегда корригируйте по частоте:

$$
QTc = \\frac{QT}{\\sqrt{RR}}
$$

```ecg
pathology: sinlongqt
lead: II
caption: Удлинённый интервал QT при синусовом ритме
```

$QTc > 500\\ \\text{мс}$ заметно повышает риск полиморфной ЖТ.

```table
id: qtc-risk
editable: false
---
| QTc (мс) | Трактовка           |
|----------|---------------------|
| < 440    | Норма               |
| 440–500  | Погранично / длинно |
| > 500    | Высокий риск torsades|
```
"""

COURSES.append({
    "id": "electrolyte-metabolic",
    "title_en": "Electrolyte & Metabolic ECG Changes",
    "name_ru": "Электролитные и метаболические изменения ЭКГ",
    "authors": "CardioSimulator Faculty",
    "languages": ["en", "ru"],
    "asset": ("pqrst.svg", pqrst_svg("PQRST reference complex")),
    "lectures": [
        {"id": "01-potassium", "order": 1,
         "title_en": "Potassium Disturbances", "name_ru": "Нарушения калия",
         "body_en": c6_l1_en, "body_ru": c6_l1_ru},
        {"id": "02-calcium", "order": 2,
         "title_en": "Calcium Disturbances", "name_ru": "Нарушения кальция",
         "body_en": c6_l2_en, "body_ru": c6_l2_ru},
        {"id": "03-hypothermia-longqt", "order": 3,
         "title_en": "Hypothermia & Long-QT", "name_ru": "Гипотермия и удлинённый QT",
         "body_en": c6_l3_en, "body_ru": c6_l3_ru},
    ],
})


# ===========================================================================
# Serialization + validation
# ===========================================================================

def build_manifest(courses):
    lines = [
        "version:1.0",
        f"created:{CREATED}",
        "encoding:utf-8",
        "line_endings:lf",
        "",
    ]
    for c in courses:
        lines.append(
            f"course:{c['id']};lectures:{len(c['lectures'])}"
            f";title:{c['title_en']};name:{c['name_ru']}"
        )
    return "\n".join(lines) + "\n"


def build_course_txt(c):
    lines = [
        f"course:{c['id']}",
        f"title:{c['title_en']}",
        f"name:{c['name_ru']}",
        f"authors:{c['authors']}",
        f"language:{','.join(c['languages'])}",
        "",
    ]
    for lec in c["lectures"]:
        lines.append(
            f"lecture:{lec['id']};title:{lec['title_en']};name:{lec['name_ru']}"
        )
    return "\n".join(lines) + "\n"


def collect_files(courses):
    """Return {archive_path: text} for the whole bundle."""
    files = {"manifest.txt": build_manifest(courses)}
    for c in courses:
        cid = c["id"]
        files[f"{cid}/course.txt"] = build_course_txt(c)
        asset_name, asset_text = c["asset"]
        files[f"{cid}/assets/{asset_name}"] = asset_text
        for lec in c["lectures"]:
            for lang, body_key, title_key in (
                ("en", "body_en", "title_en"),
                ("ru", "body_ru", "name_ru"),
            ):
                path = f"{cid}/lectures/{lec['id']}.{lang}.md"
                files[path] = lecture_file(
                    lec["id"], lec["order"], lec[title_key], lec[body_key]
                )
    return files


ECG_FENCE_RE = re.compile(r"^```ecg\s*$")
PATHOLOGY_RE = re.compile(r"^pathology:\s*(.+?)\s*$")
LEAD_RE = re.compile(r"^lead:\s*(.+?)\s*$")


def validate(files, valid_ids):
    """Fail loudly on dangling ecg embeds, bad leads, or missing files."""
    errors = []
    ecg_refs = 0
    for path, text in files.items():
        if not path.endswith(".md"):
            continue
        lines = text.split("\n")
        i = 0
        while i < len(lines):
            if ECG_FENCE_RE.match(lines[i]):
                block = []
                j = i + 1
                while j < len(lines) and lines[j].strip() != "```":
                    block.append(lines[j])
                    j += 1
                pid = lead = None
                for bl in block:
                    m = PATHOLOGY_RE.match(bl)
                    if m:
                        pid = m.group(1)
                    m = LEAD_RE.match(bl)
                    if m:
                        lead = m.group(1)
                ecg_refs += 1
                if pid not in valid_ids:
                    errors.append(f"{path}: unknown pathology '{pid}'")
                if lead is not None and lead not in VALID_LEADS:
                    errors.append(f"{path}: invalid lead '{lead}'")
                i = j
            i += 1

    # Cross-check the manifest counts and lecture index against real files.
    present = set(files.keys())
    for c in COURSES:
        cid = c["id"]
        for lec in c["lectures"]:
            for lang in c["languages"]:
                p = f"{cid}/lectures/{lec['id']}.{lang}.md"
                if p not in present:
                    errors.append(f"missing lecture file {p}")
    return errors, ecg_refs


def main():
    valid_ids = valid_pathology_ids()
    files = collect_files(COURSES)
    errors, ecg_refs = validate(files, valid_ids)
    if errors:
        print("VALIDATION FAILED:")
        for e in errors:
            print("  -", e)
        sys.exit(1)

    # Write the zip: manifest first, then each course, UTF-8 / LF, DEFLATE.
    ordered = ["manifest.txt"] + sorted(p for p in files if p != "manifest.txt")
    with zipfile.ZipFile(OUT_ZIP, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in ordered:
            data = files[path].encode("utf-8")  # no BOM
            assert b"\r" not in data, f"CR found in {path}"
            zf.writestr(path, data)

    total_lectures = sum(len(c["lectures"]) for c in COURSES)
    print(f"Wrote {OUT_ZIP}")
    print(f"  courses        : {len(COURSES)}")
    print(f"  lecture files  : {total_lectures * 2} ({total_lectures} x en+ru)")
    print(f"  ecg embeds     : {ecg_refs} (all valid against {len(valid_ids)} pathologies)")
    print(f"  zip entries    : {len(files)}")


if __name__ == "__main__":
    main()
