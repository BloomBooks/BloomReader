# Bloom Reader
[Bloom](http://bloomlibrary.org) is [award-winning](http://allchildrenreading.org/sil-international-wins-enabling-writers-prize-for-software-solution-to-childrens-book-shortage/) open source desktop software for Windows and Linux that communities, NGOs, and Education ministries around the world are using to create books in the worlds thousands of minority languages. These books are mostly distributed via paper, though some people are making epubs. We can now also use SIL's Reading Application Builder to package books as Android apps that can be downloaded from the Play Store.

So what's this "Bloom Reader" about? Bloom users tell us that it's difficult to find epub readers that are simple
to use and that can reliably display Bloom books and play audio from Bloom Talking books.
They want the simplicity of apps. But they don't want to have to create apps or have people download
apps for each book that gets published. Instead, they want an app, kind of like a Kindle app, which
people can use to read and share Bloom books.

Bloom Reader is a native java app with a WebView for displaying Bloom books (which are HTML). We want to support the older Android versions and devices that are common in the developing world.

# Status
This is currently an unofficial side project.

# Road map
## Prototype
* [x] Shows a list of books on the device's storage
* [x] user can swipe through pages to read the book

Books would get onto the device by plugging into a computer and transferring files over to some known location.

## MVP
* [x] Works well with 3rd part file sharing apps.
* [x] Works with Android 4.4 (KitKat) and up
* [ ] Book scales to fit device screen
* [ ] Comes with SIL's Andika literacy font
* [ ] Bloom desktop Publish tab offers a "Publish to Bloom Reader" option. That may have to reduce image resolution.
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

# Building

    git clone https://github.com/BloomBooks/BloomReader
    cd BloomReader
    gradlew (or, more likely open project in Android Studio)

# Contributions
We welcome contributions, particularly if we pre-agree on UX.

