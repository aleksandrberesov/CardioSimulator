# Plan: Sync Clinical Case Presentation Mode & Dashboard to Android

**Created:** 2026-07-01  
**Status:** COMPLETED
**Direction:** **Windows → Android**

**Target (Android) source root:** `E:\VLN_Project\CardioSimulator\app\src\main\java\com\example\cardiosimulator\`  
**Reference (Windows) source root:** `E:\VLN_Project\CardioSimulatorWin\src\`  

---

## 1. Background & Goals

During this session, we implemented **Clinical Case Presentation Mode** in the Windows application. This feature allows users to associate specific pathologies with clinical presentation cases containing parameters like patient age, gender, heart rate, blood pressure, case title, and patient name.

To maintain feature parity, the Android application must implement:
1. Parsing and serializing of the `clinical_case` parameter from `.dat` file headers and `manifest.txt`.
2. A toggle button in the rhythm choosing panel's header to enter Clinical Case Mode.
3. Pathologies list presentation matching the Case Title when in Clinical Case Mode.
4. A bottom dashboard card displaying parsed clinical parameters in a canonical order (translating the gender value dynamically on display).
5. A toolbar button and content dialog in Constructor Mode for filling, parsing, and validating clinical case parameters.

---

## 2. Part A: Data Models & Parser

### 2.1 Model Changes
- Add a nullable `clinicalCase: String?` property to `PathologyFile` and `PathologyEntry` data classes.
- Ensure all copying constructors or cloning functions preserve this property.

### 2.2 Parser & Serialization
- **Manifest Parser**: Update the manifest reader/writer to read and write the `clinical_case` attribute. Since the manifest uses semicolon `;` as a field separator, ensure that the clinical case parameter uses comma `,` separations instead (e.g. `title=Severe Infarct,name=John Doe,age=45,gender=Male,hr=72,bp=120/80`).
- **Pathology Parser**: Parse the `clinical_case:` header field from `.dat` files on read, and serialize it back on write.
- **Pathology Source Sync**: Ensure that when a pathology is written/updated, its `clinicalCase` metadata is written both to the `.dat` file and correctly synced to the manifest index cache.

---

## 3. Part B: Rhythm Choosing UI (Compose)

### 3.1 Mode Toggle Button
- Add a `ToggleButton` in the choosing panel header containing a stethoscope icon (glyph `&#xECAD;` or Jetpack Compose equivalent icon like `Icons.Default.Healing`).
- Tooltip: `clinical_mode_tooltip` ("Clinical cases mode").
- When checked:
  - Filter list of rhythms to only include entries where `clinicalCase` is defined (non-blank).
  - Enforce category grouping (disable alphabetical sorting, i.e., disable the alphabetical sort toggle button).
  - Automatically select the first clinical pathology in the list if the current selection is filtered out.
  - Present pathology rows using their parsed clinical case `title` parameter instead of standard pathology names (falling back to standard names if `title` is missing).

### 3.2 Clinical Dashboard Layout
- Add a floating/bottom card layout `ClinicalDashboard` below the list scroll-viewer.
- Header: `clinical_dashboard_title` ("Clinical Case").
- Body: An items list rendering key-value properties.
- **Canonical Ordering**: Parse parameters and display them in a canonical sorted order in the layout:
  1. Case Title (`title`)
  2. Patient Name (`name`)
  3. Age (`age`)
  4. Gender (`gender`)
  5. Heart Rate (`hr`)
  6. Blood Pressure (`bp`)
  7. Custom parameters (any other parsed key-value parameters, e.g. `temp=36.6`)
- **Gender Value Translation**: When displaying the `gender` value on the dashboard, check for known aliases (case-insensitive `male`, `female`, and their translations) and display the translated gender option (`gender_male` or `gender_female`) in the current UI language.

---

## 4. Part C: Constructor Editor (Compose)

### 4.1 Toolbar Button
- Add a stethoscope icon button to the constructor toolbar next to the tag/group button.
- Tooltip: `clinical_edit_tooltip` ("Edit clinical case parameters").
- Visibility: Show only when a pathology file is loaded.

### 4.2 Interactive Dialog
On click, open a Dialog containing input fields:
- **Case Title** (TextField)
- **Patient Name** (TextField)
- **Age** (TextField)
- **Gender** (ComboBox/Dropdown Menu with localized options: Male/Female)
- **Heart Rate** (TextField)
- **Blood Pressure** (TextField)
- **Other custom parameters** (TextField, comma-separated e.g. `temp=36.6, weight=70`)
- **Pre-filling**: Parse the active pathology's `clinicalCase` string to pre-populate all textboxes and select the correct index in the Gender ComboBox on open.
- **Input Restriction**: Restrict the Age and Heart Rate TextFields to digits only, preventing any letters, symbols, or spacing characters from being typed or pasted (e.g. by wrapping text state changes or using numeric keyboards).
- **Serialization**: On OK click, serialize all fields back to the comma-separated `clinical_case` parameter string format and write it via the viewmodel.

### 4.3 Live Refresh
- In the constructor screen view controller, monitor modifications to the clinical case parameters.
- When modified in memory, dynamically update the rhythm list's active entry and reload the drawer items immediately so the clinical case title and dashboard update in real-time before saving.

---

## 5. Part D: Localization Strings

Add translations in `strings.xml` or custom dictionaries:

- **English (EN)**:
  - `clinical_mode_tooltip` = "Clinical cases mode"
  - `clinical_dashboard_title` = "Clinical Case"
  - `clinical_label_title` = "Case Title"
  - `clinical_label_patient_name` = "Patient Name"
  - `clinical_label_age` = "Age"
  - `clinical_label_gender` = "Gender"
  - `clinical_label_hr` = "Heart Rate"
  - `clinical_label_bp` = "Blood Pressure"
  - `clinical_edit_tooltip` = "Edit clinical case parameters"
  - `clinical_edit_title` = "Clinical Case Parameters"
  - `clinical_label_others` = "Other parameters (e.g. temp=36.6, weight=70)"
  - `gender_male` = "Male"
  - `gender_female` = "Female"

- **Russian (RU)**:
  - `clinical_mode_tooltip` = "Режим клинических случаев"
  - `clinical_dashboard_title` = "Клинический случай"
  - `clinical_label_title` = "Название случая"
  - `clinical_label_patient_name` = "Имя пациента"
  - `clinical_label_age` = "Возраст"
  - `clinical_label_gender` = "Пол"
  - `clinical_label_hr` = "ЧСС"
  - `clinical_label_bp` = "АД"
  - `clinical_edit_tooltip` = "Редактировать параметры клинического случая"
  - `clinical_edit_title` = "Параметры клинического случая"
  - `clinical_label_others` = "Другие параметры (напр. temp=36.6, weight=70)"
  - `gender_male` = "Мужской"
  - `gender_female` = "Женский"

- **Spanish (ES)**:
  - `clinical_mode_tooltip` = "Modo de casos clínicos"
  - `clinical_dashboard_title` = "Caso Clínico"
  - `clinical_label_title` = "Título del caso"
  - `clinical_label_patient_name` = "Nombre del paciente"
  - `clinical_label_age` = "Edad"
  - `clinical_label_gender` = "Género"
  - `clinical_label_hr` = "Frecuencia Cardíaca"
  - `clinical_label_bp` = "Presión Arterial"
  - `clinical_edit_tooltip` = "Editar parámetros del caso clínico"
  - `clinical_edit_title` = "Parámetros del caso clínico"
  - `clinical_label_others` = "Otros parámetros (p. ej. temp=36.6, weight=70)"
  - `gender_male` = "Masculino"
  - `gender_female` = "Femenino"

- **Chinese (ZH)**:
  - `clinical_mode_tooltip` = "临床案例模式"
  - `clinical_dashboard_title` = "临床案例"
  - `clinical_label_title` = "病例标题"
  - `clinical_label_patient_name` = "患者姓名"
  - `clinical_label_age` = "年龄"
  - `clinical_label_gender` = "性别"
  - `clinical_label_hr` = "心率"
  - `clinical_label_bp` = "血压"
  - `clinical_edit_tooltip` = "编辑临床案例参数"
  - `clinical_edit_title` = "临床案例参数"
  - `clinical_label_others` = "其他参数 (例如 temp=36.6, weight=70)"
  - `gender_male` = "男"
  - `gender_female` = "女"

- **Hindi (HI)**:
  - `clinical_mode_tooltip` = "क्लिनिकल केस मोड"
  - `clinical_dashboard_title` = "क्लिनिकल केस"
  - `clinical_label_title` = "मामले का शीर्षक"
  - `clinical_label_patient_name` = "रोगी का नाम"
  - `clinical_label_age` = "आयु"
  - `clinical_label_gender` = "लिंग"
  - `clinical_label_hr` = "हृदय दर"
  - `clinical_label_bp` = "रक्तचाप"
  - `clinical_edit_tooltip` = "क्लिनिकल केस पैरामीटर संपादित करें"
  - `clinical_edit_title` = "क्लिनिकल केस पैरामीटर"
  - `clinical_label_others` = "अन्य पैरामीटर (जैसे temp=36.6, weight=70)"
  - `gender_male` = "पुरुष"
  - `gender_female` = "महिला"

---

## 6. Part E: Verification

### 6.1 Parser Unit Tests
- Add a unit test to verify that the parser reads `clinical_case` parameters from headers correctly.
- Add a unit test to verify serialization roundtrips the `clinical_case` property.
- Add a unit test to verify manifest reading correctly maps `clinicalCase`.
