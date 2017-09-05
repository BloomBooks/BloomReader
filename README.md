# Bloom Reader
[Bloom](http://bloomlibrary.org) is [award-winning](http://allchildrenreading.org/sil-international-wins-enabling-writers-prize-for-software-solution-to-childrens-book-shortage/) open source desktop software for Windows and Linux that communities, NGOs, and Education ministries around the world are using to create books in the worlds thousands of minority languages. These books are mostly distributed via paper, though some people are making epubs. We can now also use SIL's Reading Application Builder to package books as Android apps that can be downloaded from the Play Store.

So what's this "Bloom Reader" about? Bloom users tell us that it's difficult to find epub readers that are simple
to use and that can reliably display Bloom books and play audio from Bloom Talking books.
They want the simplicity of apps. But they don't want to have to create apps or have people download
apps for each book that gets published. Instead, they want an app, kind of like a Kindle app, which
people can use to read and share Bloom books.

Bloom Reader is a native java app with a WebView for displaying Bloom books (which are HTML). We want to support the older Android versions and devices that are common in the developing world.

# Status
Bloom Reader is in active development with an official MVP release expected Fall 2017.

# Road map
## Prototype
* [x] Shows a list of books on the device's storage
* [x] user can swipe through pages to read the book

Books would get onto the device by plugging into a computer and transferring files over to some known location.

## MVP
* [x] Works well with 3rd party file sharing apps.
* [x] Works with Android 4.4 (KitKat) and up
* [x] Comes with SIL's Andika literacy font
* [x] Book scales to fit device screen
* [x] Bloom desktop Publish tab offers a "Publish to Android Phone" option (Bloom version 4.0). That may have to reduce image resolution.

At this point, literacy projects could seed books into the community and let the community distribute them virally.

## Needed for SIL PNG literacy research project
* Plays talking books
* Book thumbnails

## Tie into BloomLibrary.org
* BloomLibrary.org publishes an [OPDS](http://opds-spec.org/specs/opds-catalog-1-1-20110627/) catalog of all its books.
* User can see a list of all the books in her language that are on [BloomLibrary.org](http://bloomlibrary.org).
* User can preview books, perhaps in a very low-bandwidth form.
* User can choose to download books to her device.
* User gets notifications when new or updated books are available.

At this point, anyone could publish a book using the existing Bloom mechanism, and have it immediately downloadable by anyone with Bloom Reader. Books would still spread mostly from person to person in expensive internet areas.

## Other things on the radar
* Use [Crosswalk](https://crosswalk-project.org/) to get an up-to-date browser that can handle Bloom's Talking Books.
* Use Graphite-enabled Crosswalk to support languages with the most complex scripts.
* Support Ken Burns animation as we do in [BloomPlayer.js](https://github.com/BloomBooks/BloomPlayer)
* Support background music that works across pages, as we do in BloomPlayer.js.
* In-app sharing/synchronization via bluetooth and wifi-direct.

# Building

    git clone https://github.com/BloomBooks/BloomReader
    cd BloomReader
    gradlew (or, more likely open project in Android Studio)

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

# Development

## Sample book

To update the sample book, *The Moon and the Cap*:

- Open an English collection in the latest version of Bloom. Vernacular and National language should be English. 
- Set xMatter to Super Paper Saver. 
- Create a book using the sample shell.
- Set the layout to Device16x9Portrait (this step is, theoretically, not necessary because Bloom Reader should display the correct layout, anyway, but it is probably a good idea).
- Publish -> Android -> Save Bloom Reader File
- Save to {BloomReader}/app/src/main/assets/sample books/The Moon and the Cap.bloomd

## Contributions
We welcome contributions, particularly if we pre-agree on UX.

