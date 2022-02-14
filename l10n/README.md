# Localization

We use Crowdin for localization. Bloom Reader's `strings.xml` is included in the general [SIL-Bloom](https://crowdin.com/project/sil-bloom) project in Crowdin and is aliased as `Bloom Reader`.

## Updating/Adding translations from Crowdin

1. Download the translations using the Crowdin plugin (see below). (Or manually download them and copy to the correct structure.)
2. Run `processLocalizations.sh`.
   - This remaps the language codes in the way we expect.
3. At this point, existing files should be updated correctly.
4. There will also be a bunch of new files added for languages we haven't included previously. Delete those.
   - (Or decide to add them, thinking through what the language code should be. And update `processLocalizations.sh`.)

Warning: with the current project settings in Crowdin, you will download all translated strings, even unapproved ones. Therefore, this process will add unapproved strings.

## Add/Update/Remove English strings in Crowdin

1. Make sure you on the `master` branch.
2. After modifications have been made to `{BR root}/app/src/main/res/values/strings.xml`, upload it using Crowdin plugin (see below).

Warning: This will also delete strings from Crowdin if the strings are not in the file.

## Crowdin plugin

The easiest way to upload new strings and download translations is through the [JetBrains plugin for Android Studio](https://plugins.jetbrains.com/plugin/9463-crowdin). You will need to add the `crowdin.properties` file at the root of this repo. The file should have the following format:

```
project-identifier=sil-bloom
project-key={secret key}
auto-upload=false
```

`project-key` can be found in the Crowdin project if you have the appropriate permissions.

`auto-upload=false` toggles off the (totally bizzare, in my opinion) default behavior of uploading all changes immediately.

I decided not to add the file, even without the key, to the repo because it would be a lot easier for someone to accidentally commit the key at that point. As it is, we can .gitignore the file entirely.

Warning: with the current project settings in Crowdin, you will download all translated strings, even unapproved ones.
