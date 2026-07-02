# Sync Welcome Overlay Opt-Out to Android (Windows → Android)

This document provides a detailed instruction set to implement the welcome screen opt-out checkbox in the Android repository, establishing 1:1 parity with the Windows implementation.

---

## 1. Goal

Modify the welcome screen overlay behavior:
1. Currently, the welcome screen is shown once on first launch and never again (it is hard-coded to dismiss and disable itself permanently on clicking "Start").
2. Change the behavior so it shows on every app launch by default.
3. Add a "Don't show this again" Checkbox to the welcome overlay so the user can opt-out. If checked, the screen will not be shown on subsequent app launches. If unchecked, it will continue to be shown.

---

## 2. Technical Blueprint & Reference Implementation

### 2.1 Localization Strings
Add the following string resource tag to localization files:
- `app/src/main/res/values/strings.xml` (English):
  ```xml
  <string name="welcome_dont_show_again">Don\'t show this again</string>
  ```
- `values-ru/strings.xml` (Russian):
  ```xml
  <string name="welcome_dont_show_again">Больше не показывать</string>
  ```
- `values-zh/strings.xml` (Chinese):
  ```xml
  <string name="welcome_dont_show_again">不再显示</string>
  ```
- `values-es/strings.xml` (Spanish):
  ```xml
  <string name="welcome_dont_show_again">No volver a mostrar</string>
  ```
- `values-hi/strings.xml` (Hindi):
  ```xml
  <string name="welcome_dont_show_again">दोबारा न दिखाएं</string>
  ```

### 2.2 WelcomeOverlay Composable Changes
In `app/src/main/java/com/example/cardiosimulator/ui/components/WelcomeOverlay.kt`:
- Modify the parameter signature to pass a boolean back:
  ```kotlin
  fun WelcomeOverlay(
      onDismiss: (dontShowAgain: Boolean) -> Unit,
      modifier: Modifier = Modifier
  )
  ```
- Track checked state:
  ```kotlin
  var dontShowAgain by remember { mutableStateOf(false) }
  ```
- Add the `Checkbox` layout below the Tagline and above the `Button`:
  ```kotlin
  // ... After the welcome_tagline Text ...
  Spacer(modifier = Modifier.height(16.dp))

  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.clickable { dontShowAgain = !dontShowAgain }
  ) {
      androidx.compose.material3.Checkbox(
          checked = dontShowAgain,
          onCheckedChange = { dontShowAgain = it }
      )
      Text(
          text = stringResource(R.string.welcome_dont_show_again),
          style = MaterialTheme.typography.bodyMedium
      )
  }

  Spacer(modifier = Modifier.height(16.dp))

  // Button onClick:
  Button(
      onClick = { onDismiss(dontShowAgain) },
      modifier = Modifier.height(56.dp).padding(horizontal = 32.dp)
  )
  ```

### 2.3 TeachingScreen Changes
In `app/src/main/java/com/example/cardiosimulator/ui/screens/TeachingScreen.kt`:
- Collect checkbox state from the overlay and update DataStore via the ViewModel:
  ```kotlin
          if (showWelcome) {
              WelcomeOverlay(
                  onDismiss = { dontShowAgain ->
                      showWelcome = false
                      appViewModel.setWelcomeShown(dontShowAgain)
                  }
              )
          }
  ```

---

## 3. Verification

1. Build and run the Android app.
2. Verify that the welcome overlay displays on start (if DataStore state was cleared or was initially false).
3. Tap **Start** with the checkbox **unchecked**.
4. Close and restart the app. Verify that the welcome overlay displays again.
5. Tap **Start** with the checkbox **checked**.
6. Close and restart the app. Verify that the welcome overlay does **not** display.
