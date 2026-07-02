# Sync pathology description field to Android (Windows → Android)

This document provides a detailed instruction set to implement pathology descriptions in the Android repository, establishing 1:1 parity with the Windows implementation.

---

## 1. Goal

Implement support for a multiline **Description** field associated with each pathology:
1. **Model**: Add `description: String?` to domain classes.
2. **Parsing & Serialization**: Parse the `description:` header field from `<pathology>.dat` files (unescaping `\n` to actual newlines), and serialize it back (escaping actual newlines to `\n` representation).
3. **ViewModel**: Expose description state in `ConstructorViewModel` and `RhythmViewModel`.
4. **UI**:
   - Add a graduation-cap or info icon button to the ECG Constructor toolbar to view/edit description.
   - Display a multiline text editing dialog for the description.
   - Render the description inside the `RhythmInfoScreen` in the Teaching monitor overlay.
5. **Localization & Verification**: Add localized labels and unit tests.

---

## 2. Technical Blueprint & Reference Implementation

### 2.1 Model Changes
In `app/src/main/java/com/example/cardiosimulator/domain/Pathology.kt`:
- Add `val description: String? = null` to `PathologyEntry` data class.
- Add `val description: String? = null` to `PathologyFile` data class.

```kotlin
data class PathologyEntry(
    val id: String,
    val titleEn: String,
    val nameRu: String?,
    val leadsCount: Int,
    val fileName: String,
    val group: String? = null,
    val description: String? = null // Add this
)

data class PathologyFile(
    val id: String,
    val titleEn: String,
    val nameRu: String?,
    val leads: Map<Lead, LeadStream>,
    val significantPoints: List<SignificantPoint> = emptyList(),
    val group: String? = null,
    val description: String? = null // Add this
)
```

### 2.2 Parser Updates
In `app/src/main/java/com/example/cardiosimulator/domain/PathologyParser.kt`:
- In `parsePathology(text: String)`, parse the `description` header if present, replacing serialized `\\n` back to `\n`:
  ```kotlin
  val description = header["description"]?.replace("\\n", "\n")
  ```
  Pass this variable into the `PathologyFile` constructor.

- In `serializePathology(file: PathologyFile, leadOrder: List<Lead>)`, serialize the `description` if not blank, escaping actual newlines to `\\n`:
  ```kotlin
  if (!file.description.isNullOrBlank()) {
      val escaped = file.description.replace("\r\n", "\n").replace("\n", "\\n")
      sb.append("description:").append(escaped).append('\n')
  }
  ```

- Ensure `PathologyEntry` instantiated in `parseManifest` also defaults/propagates the description (it will be `null` in manifest, but can be loaded dynamically).

### 2.3 ViewModel Updates

#### A. RhythmViewModel (`app/src/main/java/com/example/cardiosimulator/ui/viewmodels/RhythmViewModel.kt`)
- Expose a description flow for the active rhythm:
  ```kotlin
  private val _description = MutableStateFlow<String?>(null)
  val description: StateFlow<String?> = _description.asStateFlow()
  ```
- Set it in `selectRhythm(id, persist)`:
  ```kotlin
  _description.value = file?.description
  ```

#### B. ConstructorViewModel (`app/src/main/java/com/example/cardiosimulator/ui/viewmodels/ConstructorViewModel.kt`)
- Add property to set description:
  ```kotlin
  fun setDescription(description: String?) {
      val currentFile = _targetFile.value ?: return
      val normalized = if (description.isNullOrBlank()) null else description
      if (currentFile.description != normalized) {
          _targetFile.value = currentFile.copy(description = normalized)
          _isMetadataDirty.value = true
      }
  }
  ```

### 2.4 Localization Strings
Add the following strings to resources:
- `app/src/main/res/values/strings.xml` (English):
  ```xml
  <string name="pathology_description_label">Pathology Information</string>
  <string name="description_edit_tooltip">Edit pathology information</string>
  <string name="description_edit_title">Pathology Information</string>
  ```
- `values-ru/strings.xml` (Russian):
  ```xml
  <string name="pathology_description_label">Информация о патологии</string>
  <string name="description_edit_tooltip">Редактировать информацию о патологии</string>
  <string name="description_edit_title">Информация о патологии</string>
  ```
- `values-zh/strings.xml` (Chinese):
  ```xml
  <string name="pathology_description_label">病理信息</string>
  <string name="description_edit_tooltip">编辑病理信息</string>
  <string name="description_edit_title">病理信息</string>
  ```
- `values-es/strings.xml` (Spanish):
  ```xml
  <string name="pathology_description_label">Información de la patología</string>
  <string name="description_edit_tooltip">Editar información de la patología</string>
  <string name="description_edit_title">Información de la patología</string>
  ```
- `values-hi/strings.xml` (Hindi):
  ```xml
  <string name="pathology_description_label">पैथोलॉजी जानकारी</string>
  <string name="description_edit_tooltip">पैथोलॉजी जानकारी संपादित करें</string>
  <string name="description_edit_title">पैथोलॉजी जानकारी</string>
  ```

### 2.5 Editor UI
In `app/src/main/java/com/example/cardiosimulator/ui/screens/ConstructorScreen.kt`:
- Add a dialog trigger state:
  ```kotlin
  var showDescriptionDialog by remember { mutableStateOf(false) }
  ```
- Add the `AlertDialog` overlay for description editing:
  ```kotlin
  if (showDescriptionDialog && targetFile != null) {
      var descriptionText by remember { mutableStateOf(targetFile?.description ?: "") }
      AlertDialog(
          onDismissRequest = { showDescriptionDialog = false },
          title = { Text(stringResource(R.string.description_edit_title)) },
          text = {
              OutlinedTextField(
                  value = descriptionText,
                  onValueChange = { descriptionText = it },
                  label = { Text(stringResource(R.string.pathology_description_label)) },
                  modifier = Modifier.fillMaxWidth().height(160.dp),
                  minLines = 4,
                  maxLines = 6
              )
          },
          confirmButton = {
              TextButton(onClick = {
                  constructorViewModel.setDescription(descriptionText)
                  showDescriptionDialog = false
              }) {
                  Text(stringResource(R.string.constructor_rename_ok))
              }
          },
          dismissButton = {
              TextButton(onClick = { showDescriptionDialog = false }) {
                  Text(stringResource(R.string.constructor_rename_cancel))
              }
          }
      )
  }
  ```
- Add an `IconButton` to the Toolbar row inside the `if (targetFile != null)` block, right next to the edit/rename button:
  ```kotlin
  IconButton(onClick = { showDescriptionDialog = true }) {
      Icon(
          imageVector = Icons.Default.Info,
          contentDescription = stringResource(R.string.description_edit_tooltip)
      )
  }
  ```

### 2.6 Viewer UI (Student View)
In `app/src/main/java/com/example/cardiosimulator/ui/screens/TeachingScreen.kt`:
- Collect description state in `MonitorOverlay`:
  ```kotlin
  val description by rhythmViewModel.description.collectAsState()
  ```
- Pass `description` into `RhythmInfoScreen`.
- In `RhythmInfoScreen`, render the description if it is not blank:
  ```kotlin
  @Composable
  private fun RhythmInfoScreen(
      pathology: PathologyEntry?,
      significantPoints: List<SignificantPoint>,
      language: Language,
      description: String?, // Add this parameter
      onClose: () -> Unit,
      modifier: Modifier = Modifier,
  ) {
      // ... Existing content ...
      // Inside Column scrollable details, below the markers:
      if (!description.isNullOrBlank()) {
          Spacer(Modifier.height(16.dp))
          Text(
              text = "${stringResource(R.string.pathology_description_label)}:",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
          )
          Text(
              text = description,
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.padding(top = 4.dp)
          )
      }
  }
  ```

### 2.7 Unit Tests
In `app/src/test/java/com/example/cardiosimulator/data/PathologyParserTest.kt`, add a test verifying round-trip parsing/serialization of descriptions with multiline contents:
```kotlin
@Test
fun `pathology parser handles multiline descriptions correctly`() {
    val datWithDescription = """
        pathology:desc_test
        title:Test Description
        description:This is a test\ndescription spread\nover multiple lines.
        leads:1

        lead:I
        count:2
        points:1024,1024
    """.trimIndent()

    val file = PathologyParser.parsePathology(datWithDescription)
    assertEquals("This is a test\ndescription spread\nover multiple lines.", file.description)

    val serialized = PathologyParser.serializePathology(file, listOf(Lead.I))
    assertTrue(serialized.contains("description:This is a test\\ndescription spread\\nover multiple lines."))
}
```

---

## 3. Verification

1. Run unit tests to verify parser changes:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.example.cardiosimulator.data.PathologyParserTest"
   ```
2. Build and launch the Android application.
3. Open the ECG Constructor:
   - Select a pathology.
   - Click the Info icon button in the toolbar.
   - Input a description containing multiple lines.
   - Save the pathology, select another rhythm, then select it back to ensure description remains.
   - Verify that the saved `.dat` file includes the escaped `description:` header.
4. Open the Teaching Screen:
   - Open rhythm details (graduation-cap / school button overlay).
   - Verify that the multiline description renders correctly under the pathology markers section.
