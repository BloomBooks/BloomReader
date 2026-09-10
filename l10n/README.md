# Localization

We use Crowdin for localization. Bloom Reader's `strings.xml` is included in the general [SIL-Bloom](https://crowdin.com/project/sil-bloom) project in Crowdin and is aliased as `Bloom Reader`.

## Update/Add translations from Crowdin

1. Download the translations. Either:
   - Run `downloadFromCrowdin.sh` (needs `BLOOM_CROWDIN_TOKEN`, `curl`, and `python`). It exports the Bloom Reader
     file for every target language via the Crowdin REST API into `app/src/main/res/values-*/strings.xml`,
     and then runs `processLocalizations.sh` for you (skip step 2). It uses per-file exports rather than a
     project build because Crowdin's project builds can serve a stale cache for a while after edits.
   - Or use the Crowdin plugin (see below). (Or manually download them and copy to the correct structure.)
2. Run `processLocalizations.sh`.
   - This remaps the language codes in the way we expect (e.g. Crowdin's `id` becomes Android's legacy `in` for Indonesian).
3. At this point, existing files should be updated correctly.
   - Crowdin exports untranslated strings as copies of the English, and the files come back with LF line endings,
     so use `git diff` (not `git status`) to see which languages really changed.
4. There will also be a bunch of new files added for languages we haven't included previously. Delete those
   (`git clean -fd app/src/main/res`).
   - (Or decide to add them, thinking through what the language code should be. And update `processLocalizations.sh`.)

Note: the standalone Crowdin CLI does not work with this repo's `crowdin.yml` (it rejects `dest` without
`preserve_hierarchy` and then cannot match the exported paths). Use the script or the plugin instead.

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
