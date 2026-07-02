# Android parity: Course → Тема → Подтема nesting (topics)

**Status:** ACTIVE · **Date:** 2026-07-01 · **Origin:** Windows (CardioSimulatorWin) feature, port to Android.

## Goal (one line)
Add a **third nesting level** to courses — **Course → Тема (topic) → Подтема (subtopic)** — replacing the flat
Course → Lecture, in **both** the Course Constructor (authoring) and the Teaching viewer (reading), using a
**single nested dropdown** (topics expand to their subtopics, which open on click).

## Why
Customer feedback (2026-07-01): *"мне не хватает глубины вложения в курсах… Курс – Тема – Подтема вместо
Курс–лекция… подтемы — может быть их отображать тут же в выпадающем списке, чтобы открывались."*
Two decisions were taken with the customer: **(1)** apply to both Constructor + Teaching; **(2)** use one
**nested dropdown**, not separate Тема/Подтема selectors.

## Core convention (match Windows)
The **leaf stays a `Lecture` internally** — its on-disk file `lectures/<id>.<lang>.html` is unchanged.
"Подтема" is only the **UI label** for a lecture; "Тема" is a **new grouping** layer. So code keeps
saying `Lecture`/`Topic`; the UI says Подтема/Тема. Legacy courses with no topics must keep working
(lectures shown flat).

---

## Windows source of truth (diff against these)
Repo `E:\VLN_Project\CardioSimulatorWin`:
- `src/CardioSimulator.Core/Domain/Course.cs` — `TopicEntry`, `Course.Topics`, `LectureEntry.Topic`.
- `src/CardioSimulator.Core/Domain/CourseParser.cs` — `topic:` lines + `;topic:` ref (parse + serialize).
- `src/CardioSimulator.App/Controls/CourseTopicFlyout.cs` — the shared nested-menu builder.
- `src/CardioSimulator.App/Controls/CourseConstructorControlPanel.cs`, `TeachingControlPanel.cs` — use it.
- `src/CardioSimulator.App/ViewModels/CourseConstructorViewModel.cs` — `SelectedTopicId`, `CreateTopic`/
  `RenameTopic`/`DeleteTopic`, `CreateLecture(..., topicId)`, `RenameLecture(title, topicId)`.
- `src/CardioSimulator.App/Screens/CourseConstructorScreen.cs` — New/Delete Topic + Тема picker in the
  New/Edit Subtopic dialogs; `UniqueSlug` / `GenerateTopicId`.
- `src/CardioSimulator.App/Localization/AppStrings.cs` — new strings (search `course_ctor_`, `topic_selector_title`).
- `tests/CardioSimulator.Core.Tests/CourseLectureParserTests.cs` — round-trip + legacy parser tests.

## Android target files (confirmed)
Repo `E:\VLN_Project\CardioSimulator` (branch `master`):
- `app/src/main/java/com/example/cardiosimulator/domain/Course.kt` — model.
- `app/src/main/java/com/example/cardiosimulator/domain/CourseParser.kt` — format.
- `app/src/main/java/com/example/cardiosimulator/ui/panels/LectureSelector.kt` — **shared** subtopic list (used by
  both the constructor top panel and Teaching → nesting lives here).
- `app/src/main/java/com/example/cardiosimulator/ui/panels/CourseConstructorTopPanel.kt` — constructor top-bar
  Course + Lecture selectors (Android **already** has top-bar selectors; only nest the lecture one).
- `app/src/main/java/com/example/cardiosimulator/ui/panels/TeachingControlPanel.kt` — Teaching top bar (2nd
  `LectureSelector` call site).
- `app/src/main/java/com/example/cardiosimulator/ui/viewmodels/CourseConstructorViewModel.kt` — authoring VM
  (`selectCourse`, `selectLecture`, `createCourse(courseId,title)`, `createLecture(lectureId,title)`,
  `_lectures`/`_selectedLectureId`/`_selectedCourseId` StateFlows). Add topic state + CRUD here.
- `app/src/main/java/com/example/cardiosimulator/ui/viewmodels/CourseViewerViewModel.kt` — Teaching VM.
- `app/src/main/java/com/example/cardiosimulator/ui/screens/CourseConstructorScreen.kt` — toolbar/dialogs
  (find the New/Rename/Delete lecture dialogs; add topic actions + a Тема picker to the subtopic dialogs).
- `app/src/main/res/values{,-ru,-zh,-es,-hi}/strings.xml` — all **5** locales present.
- `docs/course-format.md` — document the new `topic:` lines.
- `app/src/test/java/.../CourseParserTest.kt` — **create** (none in `app/src/test` today; a worktree has one to model).

---

## Step 1 — Domain model (`Course.kt`)
Add a topic entry and wire topics/topic-ref, using **default values** so existing constructors keep compiling:
```kotlin
/** A "Тема" (topic): a named grouping of lectures. Its [id] is referenced by member
 *  lectures' [LectureEntry.topic]; can exist with no lectures yet. */
data class TopicEntry(val id: String, val titleEn: String, val nameRu: String?)

data class Course(
    ...,
    val lectures: List<LectureEntry>,
    val pathologies: List<String> = emptyList(),
    val topics: List<TopicEntry> = emptyList(),   // NEW (order = display order)
)

data class LectureEntry(
    val id: String,
    val titleEn: String,
    val nameRu: String?,
    val topic: String? = null,                    // NEW: owning topic id (null = ungrouped)
)
```

## Step 2 — Format (`CourseParser.kt` + `docs/course-format.md`)
`course.txt` body gains **`topic:` definition lines** (emitted first, order = appearance) and a **`;topic:`**
field on `lecture:` lines. **Backward compatible** — files with no `topic:` lines parse to empty `topics` +
null-`topic` lectures.

`parseCourse` — in the `body.mapNotNull { ... }`, branch on the line kind:
```kotlin
val topics = mutableListOf<TopicEntry>()
val lectures = mutableListOf<LectureEntry>()
for (line in body) {
    val fields = parseSemicolonFields(line)
    val topicId = fields["topic"]
    val lectureId = fields["lecture"]
    if (lectureId == null && topicId != null) {          // a "topic:" definition line
        topics += TopicEntry(topicId, fields["title"].orEmpty(), fields["name"]); continue
    }
    if (lectureId == null) continue
    lectures += LectureEntry(lectureId, fields["title"].orEmpty(), fields["name"], topic = topicId)
}
// ... Course(..., lectures = lectures, pathologies = pathologies, topics = topics)
```
`serializeCourse` — after the blank line, emit topics, then lectures with their topic ref:
```kotlin
for (t in course.topics) {
    append("topic:").append(t.id).append(";title:").append(t.titleEn)
    if (!t.nameRu.isNullOrBlank()) append(";name:").append(t.nameRu)
    append('\n')
}
for (l in course.lectures) {
    append("lecture:").append(l.id).append(";title:").append(l.titleEn)
    if (!l.nameRu.isNullOrBlank()) append(";name:").append(l.nameRu)
    if (!l.topic.isNullOrBlank()) append(";topic:").append(l.topic)
    append('\n')
}
```
Update `docs/course-format.md` §course.txt to document `topic:` lines + the `;topic:` lecture field, noting
legacy files (no topics) remain valid.

## Step 3 — Nested dropdown (`LectureSelector.kt`, both call sites)
`LectureSelector` currently renders a flat `List<LectureEntry>`. Change it to **group by topic**:
- Add a `topics: List<TopicEntry> = emptyList()` parameter (both call sites must pass `course.topics`).
- Render **ungrouped/orphan** lectures (`topic == null` or no matching `TopicEntry`) first, at the top level.
- Then, for each `TopicEntry` in order: a **topic header row** (bold, non-clickable) followed by its member
  lectures (`lecture.topic == topic.id`) indented beneath. Clicking a subtopic calls `onLectureSelect`.
- Compose has no cascading submenu like WinUI's `MenuFlyoutSubItem`; an **accordion/grouped list is the
  intended shape** (topic header → indented subtopics). Optional: make headers expandable (remember an
  expanded-set) — not required for v1.
- Update both callers: `CourseConstructorTopPanel.kt` and `TeachingControlPanel.kt` (pass topics; the
  selected-course object already carries them). Reference Windows `CourseTopicFlyout.Build(...)` for the
  grouping/ordering logic (ungrouped-first, then topics in order, orphans treated as ungrouped).

## Step 4 — Constructor authoring VM (`CourseConstructorViewModel.kt`)
Mirror Windows `CourseConstructorViewModel`:
- Add `_topics: StateFlow<List<TopicEntry>>` (from the selected `Course`) and
  `_selectedTopicId: MutableStateFlow<String?>` (the focused Тема).
- `selectLecture` also sets `selectedTopicId = lecture.topic`.
- `createLecture(lectureId, title, topicId: String?)` — set `LectureEntry.topic = topicId`, focus that topic.
- `renameLecture(newTitle, topicId: String?)` — rename **and** move the leaf to `topicId`.
- `createTopic(id, title)` — append an empty `TopicEntry`, focus it, mark metadata dirty.
- `renameTopic(topicId, newTitle)`.
- `deleteTopic(topicId, language)` — delete the topic **and its member lectures' files**, clear selection if
  the open subtopic belonged to it.
- Persist via the existing course-write path (topics are part of `Course`, so serialization covers them; make
  sure the "metadata dirty → write course.txt" trigger fires on topic edits).

## Step 5 — Constructor screen dialogs (`CourseConstructorScreen.kt`)
- Toolbar/menu: add **New Topic**, **Delete Topic** (Delete Topic visible only when a Тема is focused), and
  reword the lecture actions to **New Subtopic / Edit Subtopic / Delete Subtopic**.
- **New Subtopic** & **Edit Subtopic** dialogs: add a **Тема picker** (dropdown) listing the course's topics
  plus a leading **"(no topic)"** entry, defaulting to the focused/current topic. This is how a subtopic is
  placed under a Тема, moved between Темы, and how legacy flat lectures get organized. Then call
  `createLecture(genId, title, chosenTopicId)` / `renameLecture(title, chosenTopicId)`.
- **New Topic** dialog: title only → `createTopic(genTopicId, title)`.
- Auto-generate ids by slug (unique within the course for lectures, among topics for topics), matching
  Windows `UniqueSlug` (lowercase ASCII slug; numeric `-2/-3` on collision; random fallback for non-Latin
  titles). If Android's create-lecture dialog still asks for an explicit id, drop that field (Windows removed it).

## Step 6 — Strings (all 5 `strings.xml`)
Android uses the **`course_constructor_*`** prefix (not Windows' `course_ctor_`) and **`%1$s`** positional
args (**NOT** .NET `{0}` — this is the recurring port gotcha). Add/replace:
- New: `topic_selector_title` (Тема), `subtopic_selector_title` (Подтема), `course_constructor_new_topic`,
  `course_constructor_delete_topic`, `course_constructor_topic_title_hint`, `course_constructor_no_topic`
  ("(no topic)"), `course_constructor_delete_topic_title`, `course_constructor_delete_topic_body`
  (use `%1$s` for the name).
- Reword lecture→subtopic labels: `course_constructor_new_lecture` → "New Subtopic", the delete-lecture
  title/label → "…subtopic…", the rename dialog title → "Edit Subtopic". Update the shared
  `lecture_selector_title` placeholder usage to Подтема where it labels the leaf selector.
- Translations (en/ru/zh/es/hi) — copy the Windows values from `AppStrings.cs`
  (`Тема/Подтема`, `主题/子主题`, `Tema/Subtema`, `विषय/उपविषय`, etc.). Keep `%1$s` in the confirm bodies.

## Step 7 — Tests (`app/src/test/.../CourseParserTest.kt`)
Port the two Windows tests (`CourseLectureParserTests.cs`):
1. **Round-trip**: a `Course` with 2 topics (one empty) + lectures (one ungrouped, two under a topic) →
   `serializeCourse` → `parseCourse` preserves topic order, names, the empty topic, and each lecture's `topic`.
2. **Legacy**: a `course.txt` with only `lecture:` lines (no `topic:`) → `topics` empty, all lectures `topic == null`.

---

## Gotchas / notes
- **`{0}` → `%1$s`**: the delete-confirmation bodies use a name placeholder — Android needs `%1$s`, not `{0}`.
- **Top-bar selectors already exist on Android** (`CourseConstructorTopPanel.kt`) — do **not** re-add them; just
  nest the lecture selector. (On Windows this session those selectors were *added*; Android was already ahead.)
- **Backward compatibility is mandatory**: shipped/customer course bundles have no `topic:` lines — verify they
  still open and render flat.
- **Shared `LectureSelector`**: one edit covers both Constructor and Teaching, but both call sites must pass the
  new `topics` argument, or Teaching regresses to flat.
- **Empty topics** must persist (serialize/parse) so an author can create a Тема before adding Подтемы.
- **Delete Topic** deletes member-lecture files for the current language only (matching how Android's
  delete-lecture already scopes by language); the manifest/course.txt topic row is removed regardless.
- Verify build: `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` (course parser tests).

## Companion changes from the same Windows session (secondary — port if desired)
These landed in the same Windows Course-Constructor pass; port only if Android lacks them:
- **Delete Course** button + `deleteCourse` in repo/source/VM (Windows: `FileCourseSource.DeleteCourse`,
  `CourseRepository.DeleteCourse`, `CourseConstructorViewModel.DeleteCourse`). Check Android has no equivalent.
- **Auto-generate lecture id** (drop the "Lecture id" dialog field) — see Step 5's slug note.
- Dialog **localization** — Android dialogs already use `R.string.*`, so likely already covered; just add the
  new topic/subtopic keys above.

## Related memory
Windows-side: `[[course-topics-nesting-2026-07]]`, `[[constructor-course-divergence-2026-06]]`,
`[[windows-build-notes]]` (the `FrameworkElement.Language` gotcha is Windows-only; Android has no analogue).
