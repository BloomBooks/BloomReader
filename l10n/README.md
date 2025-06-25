# Localization

We use Crowdin for localization. Bloom Reader's `strings.xml` is included in the general [SIL-Bloom](https://crowdin.com/project/sil-bloom) project in Crowdin and is aliased as `Bloom Reader`.

## Update/Add translations from Crowdin

1. Download the translations using the Crowdin plugin (see below). (Or manually download them and copy to the correct structure.)
2. Run `processLocalizations.sh`.
   - This remaps the language codes in the way we expect.
3. At this point, existing files should be updated correctly.
4. There will also be a bunch of new files added for languages we haven't included previously. Delete those.
   - (Or decide to add them, thinking through what the language code should be. And update `processLocalizations.sh`.)

Warning: with the current project settings in Crowdin, you will download all translated strings, even unapproved ones. Therefore, this process will add unapproved strings.

## Add/Update/Remove English strings in Crowdin

1. Make sure you are on the `master` branch.
2. After modifications have been made to `{BR root}/app/src/main/res/values/strings.xml`, upload it using Crowdin plugin (see below).

Warning: This will also delete strings from Crowdin if the strings are not in the file.

## Crowdin plugin

The easiest way to upload new strings and download translations is through the Crowdin plugin for Android Studio.

### Configuration

The `crowdin.yml` file contains the project configuration.

### Setup

1. Install the Crowdin plugin from the [JetBrains marketplace](https://plugins.jetbrains.com/plugin/9463-crowdin) in Android Studio.
1. Turn off the "Automatically upload sources" option in the Crowdin plugin settings.
  a. `File` -> `Settings` -> `Tools` -> `Crowdin`.
1. Set your API token as an environment variable:
   - BLOOM_CROWDIN_TOKEN = your_api_token

     The API token can be found in your Crowdin account settings if you have the appropriate permissions.

### Usage

To upload source strings to Crowdin:

1. In the Crowdin plugin UI, click the Upload tab.
2. Click "Upload Sources".

To download translations from Crowdin:

1. In the Crowdin plugin UI, click the Download tab.
2. Click "Download Translations".

Warning: with the current project settings in Crowdin, you will download all translated strings, even unapproved ones.
