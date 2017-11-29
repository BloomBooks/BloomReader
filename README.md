# Bloom Reader
[Bloom](http://bloomlibrary.org) is [award-winning](http://allchildrenreading.org/sil-international-wins-enabling-writers-prize-for-software-solution-to-childrens-book-shortage/) open source desktop software for Windows and Linux that communities, NGOs, and Education ministries around the world are using to create books in the world's thousands of minority languages. These books are mostly distributed via paper, though some people are making epubs. We can now also use SIL's Reading Application Builder to package books as Android apps that can be downloaded from the Play Store.

So what's this "Bloom Reader" about? Bloom users tell us that it's difficult to find epub readers that are simple
to use and that can reliably display Bloom books and play audio from Bloom Talking books.
They want the simplicity of apps. But they don't want to have to create apps or have people download
apps for each book that gets published. Instead, they want an app, kind of like a Kindle app, which
people can use to read and share Bloom books.

Bloom Reader is a native java app with a WebView for displaying Bloom books (which are HTML). We want to support the older Android versions and devices that are common in the developing world.

# Status
Bloom Reader is in active development with an official MVP release expected Fall 2017.

Try the beta now on the [Play Store](https://play.google.com/store/search?q=%2B%22sil%20international%22%20%2B%22bloom%20reader%22&amp;c=apps).

# Road map
## Prototype
* [x] Shows a list of books on the device's storage
* [x] User can swipe through pages to read the book

Books get onto the device by plugging into a computer and transferring files over to some known location.

## MVP
* [x] Works well with 3rd party file sharing apps.
* [x] Works with Android 4.4 (KitKat) and up
* [x] Comes with SIL's Andika literacy font
* [x] Book scales to fit device screen
* [x] Bloom desktop Publish tab offers a "Publish to Android Phone" option (Bloom version 4.0). That may have to reduce image resolution.

At this point, literacy projects can seed books into the community and let the community distribute them virally.

## Needed for SIL PNG literacy research project
* [x] Plays talking books
* [x] Book thumbnails (Version 1.1, alpha)

## Tie into BloomLibrary.org
* BloomLibrary.org publishes an [OPDS](http://opds-spec.org/specs/opds-catalog-1-1-20110627/) catalog of all its books.
* User can see a list of all the books in her language that are on [BloomLibrary.org](http://bloomlibrary.org).
* User can preview books, perhaps in a very low-bandwidth form.
* User can choose to download books to her device.
* User gets notifications when new or updated books are available.

At this point, anyone could publish a book using the existing Bloom mechanism, and have it immediately downloadable by anyone with Bloom Reader. Books would still spread mostly from person to person in expensive internet areas.

## Other things on the radar
* [ ] Use [Crosswalk](https://crosswalk-project.org/) to get an up-to-date browser that can handle Bloom's Talking Books.
* [ ] Use Graphite-enabled Crosswalk to support languages with the most complex scripts.
* [x] Support Ken Burns animation as we do in [BloomPlayer.js](https://github.com/BloomBooks/BloomPlayer) (Version 1.1, alpha)
* [x] Support background music that works across pages, as we do in BloomPlayer.js. (Version 1.1, alpha)
* [x] In-app sharing/synchronization via bluetooth and wifi-direct.

# Building

    git clone https://github.com/BloomBooks/BloomReader
    cd BloomReader
    build/getDependencies.sh
    gradlew (or, more likely open project in Android Studio)
	
To use the audio player (and eventually pan and zoom) features requires a JavaScript file,
app\src\main\assets\book support files\bloomPagePlayer.js, which is one of the outputs of
the BloomPlayer project. On TeamCity, this is configured as a dependency.

When building locally, if you need to make changes to BloomPlayer, you will currently need to build BloomPlayer first and copy the file over.

If you don't need to make changes in BloomPlayer, running `build/getDependencies.sh` will get the latest version from TeamCity.

# Signing and Deployment

## Flavors

We build three flavors of the app:

- alpha (`org.sil.bloom.reader.alpha`)
- beta (`org.sil.bloom.reader.beta`)
- production (`org.sil.bloom.reader`)

## Debug builds

Each flavor can also build a debug configuration which appends `.debug` to the package name.
The main reason for this is to prevent debug builds from polluting crash reports. See [https://issuetracker.google.com/issues/64929012](https://issuetracker.google.com/issues/64929012).

## Signing a release build

To sign a release build, you need the following file.

	{UserHome}/keystore/keystore_bloom_reader.properties

The contents of this file are: 

    storeFile=
    storePassword=
    keyAlias=
    keyPassword=

where `storeFile` is an absolute path to `keystore_bloom_reader.keystore`. This file and the other values must be shared with you by a member of the team who has them.

## TeamCity builds (and deploying to the Play Store)

To publish to the Play Store, we use a gradle plugin: `https://github.com/Triple-T/gradle-play-publisher`. To use the plugin, you must add `serviceAccountJsonFile=` to the `.properties` file described above. Set the value as an absolute path to the `Google Play Android Developer-cf6d1afc73be.json` file which you must obtain from a member of the team.

Gradle tasks which can be called with the plugin include:

- publish{Alpha/Beta/Production}Release
	- pushes both the apk and listing metadata to the Play Store
- publishApk{Alpha/Beta/Production}Release
	- pushes only the apk to the Play Store
- publishListing{Alpha/Beta/Production}Release
	- pushes only the listing metadata to the Play Store

TeamCity builds are configured to publish the alpha, beta, and production flavors to three respective apps on the Play Store. 

- The alpha build is a continuous publish to the alpha track of the alpha app.
- The beta build is a manual publish to the beta track of the beta app.
- The production build is a manual publish to the beta track of the production app.

The `ba-win10-64-s1-601` (in the Keyman pool) is currently the only agent configured with the `.properties` file described above.

# Development

## Sample book

To update the sample book, *The Moon and the Cap*:

- Open an English collection in the latest version of Bloom. Vernacular and National language should be English. 
- Set xMatter to Device. 
- Create a book using the sample shell.
- Set the layout to Device16x9Portrait (this step is, theoretically, not necessary because Bloom Reader should display the correct layout, anyway, but it is probably a good idea).
- Publish -> Android -> Save Bloom Reader File
- Save to {BloomReader}/app/src/main/assets/sample books/The Moon and the Cap.bloomd

## Contributions
We welcome contributions, particularly if we pre-agree on UX.

