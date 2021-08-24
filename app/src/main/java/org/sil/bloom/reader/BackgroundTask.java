package org.sil.bloom.reader;

// This class was my attempt to replace the deprecated AsyncTask.
// But it probably has at least all the problems AsyncTask does.
// So using it at this point probably does nothing more than whitewash the deprecation warning.
// Thus it is currently unused.
// Some day when I understand the issues better, we can expand this to be robust and use it.
public abstract class BackgroundTask {

    protected final MainActivity activity;
    public BackgroundTask(MainActivity activity) {
        this.activity = activity;
    }

    private void startBackground() {
        new Thread(() -> {
            doInBackground();
            if (activity != null)
                activity.runOnUiThread(this::onPostExecute);
        }).start();
    }

    public void execute(){
        startBackground();
    }

    public abstract void doInBackground();
    public abstract void onPostExecute();
}