/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package is.xyz.filepicker;

import app.mpvnova.player.R;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.loader.content.AsyncTaskLoader;
import androidx.core.content.ContextCompat;
import androidx.loader.content.Loader;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * An implementation of the picker which allows you to select a file from the internal/external
 * storage (SD-card) on a device.
 */
public class FilePickerFragment extends AbstractFilePickerFragment<File> {

    protected static final String PERMISSION_PRE33 = Manifest.permission.READ_EXTERNAL_STORAGE;
    private static final long REFRESH_WINDOW_MS = 100;
    protected boolean showHiddenItems = false;
    protected FileFilter filterPredicate = null;
    private File mRequestedPath = null;
    private IdentityHashMap<File, Boolean> mDirectoryCache = null;

    protected static final class FileListResult extends ArrayList<File> {
        final IdentityHashMap<File, Boolean> directories;

        FileListResult(int initialCapacity) {
            super(initialCapacity);
            this.directories = new IdentityHashMap<>(initialCapacity);
        }
    }
    private final ActivityResultLauncher<Intent> allFilesAccessLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    this::onAllFilesAccessResult);
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionResult);

    public FilePickerFragment() {
    }

    /**
     * This method is used to dictate whether hidden files and folders should be shown or not
     *
     * @param showHiddenItems whether hidden items should be shown or not
     */
    public void showHiddenItems(boolean showHiddenItems){
        this.showHiddenItems = showHiddenItems;
    }

    /**
     * Returns if hidden items are shown or not
     *
     * @return true if hidden items are shown, otherwise false
     */

    public boolean areHiddenItemsShown(){
        return showHiddenItems;
    }

    /**
     * This method is used to set the filter that determines the files to be shown
     * The predicate runs on a worker thread and must not retain views or activity contexts.
     *
     * @param predicate filter implementation or null
     */
    public void setFilterPredicate(@Nullable FileFilter predicate) {
        this.filterPredicate = predicate;
        refresh(mCurrentPath);
    }

    /**
     * Returns the filter that determines the files to be shown
     *
     * @return filter implementation or null
     */
    public @Nullable FileFilter getFilterPredicate() {
        return this.filterPredicate;
    }

    /**
     * @return true if app has been granted permission to read shared storage.
     */
    public static boolean hasPermission(@NonNull Context context, @NonNull File path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
            return true;
        // Android 11/12 also allow media-file paths with the runtime storage permission.
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 && PackageManager.PERMISSION_GRANTED ==
                ContextCompat.checkSelfPermission(context, PERMISSION_PRE33);
    }

    @Override
    protected boolean hasPermission(@NonNull File path) {
        return hasPermission(requireContext(), path);
    }

    /**
     * Request permission to read shared storage.
     */
    @Override
    protected void handlePermission(@NonNull File path) {
        mRequestedPath = path;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            launchAllFilesAccessSettings();
        } else {
            permissionLauncher.launch(new String[]{PERMISSION_PRE33});
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void launchAllFilesAccessSettings() {
        Uri packageUri = Uri.parse("package:" + requireContext().getPackageName());
        Intent[] settingsIntents = {
                new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri),
                new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        };
        // Some TV firmware omits both all-files settings activities.
        for (Intent intent : settingsIntents) {
            try {
                allFilesAccessLauncher.launch(intent);
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w("FilePickerFragment", "Storage permission settings unavailable: " + intent.getAction(), e);
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            // TVs without all-files settings can still grant read access to shared media.
            try {
                permissionLauncher.launch(new String[]{PERMISSION_PRE33});
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w("FilePickerFragment", "Storage permission dialog unavailable", e);
            }
        }
        try {
            allFilesAccessLauncher.launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri));
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w("FilePickerFragment", "App permission settings unavailable", e);
        }
        mRequestedPath = null;
        Toast.makeText(requireContext(), R.string.storage_permission_settings_unavailable,
                Toast.LENGTH_LONG).show();
        if (mListener != null)
            mListener.onCancelled();
    }

    private void onAllFilesAccessResult(@SuppressWarnings("unused") ActivityResult result) {
        if (mRequestedPath == null)
            return;
        if (hasPermission(mRequestedPath)) {
            onPermissionGranted(mRequestedPath);
        } else {
            Toast.makeText(getContext(), R.string.nnf_permission_external_write_denied,
                    Toast.LENGTH_SHORT).show();
            if (mListener != null)
                mListener.onCancelled();
        }
    }

    private void onPermissionResult(@NonNull Map<String, Boolean> grants) {
        // If results are empty, then process was cancelled
        if (grants.isEmpty()) {
            // Treat this as a cancel press
            if (mListener != null)
                mListener.onCancelled();
            return;
        }
        boolean ok = false;
        for (Boolean granted : grants.values()) {
            if (Boolean.TRUE.equals(granted)) {
                ok = true;
                break;
            }
        }
        if (ok) {
            // Do refresh
            if (mRequestedPath != null)
                onPermissionGranted(mRequestedPath);
        } else {
            Toast.makeText(getContext(), R.string.nnf_permission_external_write_denied,
                    Toast.LENGTH_SHORT).show();
            // Treat this as a cancel press
            if (mListener != null)
                mListener.onCancelled();
        }
    }

    protected void onPermissionGranted(@NonNull File requestedPath) {
        refresh(requestedPath);
    }

    /**
     * Return true if the path is a directory and not a file.
     *
     * @param path either a file or directory
     * @return true if path is a directory, false if file
     */
    @Override
    public boolean isDir(@NonNull final File path) {
        if (mDirectoryCache != null) {
            Boolean cached = mDirectoryCache.get(path);
            if (cached != null) {
                return cached;
            }
        }
        return path.isDirectory();
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<File>> loader, List<File> data) {
        if (data instanceof FileListResult) {
            mDirectoryCache = ((FileListResult) data).directories;
        } else {
            mDirectoryCache = null;
        }
        super.onLoadFinished(loader, data);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<File>> loader) {
        mDirectoryCache = null;
        super.onLoaderReset(loader);
    }

    /**
     * @param path either a file or directory
     * @return filename of path
     */
    @NonNull
    @Override
    public String getName(@NonNull File path) {
        return path.getName();
    }

    /**
     * Return the path to the parent directory. Should return the root if
     * from is root.
     *
     * @param from either a file or directory
     * @return the parent directory
     */
    @NonNull
    @Override
    public File getParent(@NonNull final File from) {
        if (from.getPath().equals(getRoot().getPath())) {
            // Already at root, we can't go higher
            return from;
        } else if (from.getParentFile() != null) {
            return from.getParentFile();
        } else {
            return from;
        }
    }

    /**
     * Convert the path to the type used.
     *
     * @param path either a file or directory
     * @return File representation of the string path
     */
    @NonNull
    @Override
    public File pathFromString(@NonNull final String path) {
        return new File(path);
    }

    /**
     * @param path either a file or directory
     * @return the full path to the file
     */
    @NonNull
    @Override
    public String pathToString(@NonNull final File path) {
        return path.getPath();
    }

    /**
     * Get the root path.
     *
     * @return the highest allowed path, which is "/" by default
     */
    @NonNull
    @Override
    public File getRoot() {
        return new File("/");
    }

    /**
     * Get a loader that lists the Files in the current path,
     * and monitors changes.
     */
    @NonNull
    @Override
    // Retain the fragment's overridable compareFiles hook. The base fragment destroys this
    // loader and releases views in onDestroyView; stop/reset/abandon release the observer.
    // Worker inputs are snapshots. Cancellation cannot interrupt filesystem I/O, so a worker
    // may retain the fragment until I/O returns, but not its released views or listener.
    @SuppressLint("StaticFieldLeak")
    public Loader<List<File>> getLoader() {
        if (mCurrentPath == null || !isDir(mCurrentPath)) {
            mCurrentPath = getRoot();
        }
        final File currentPath = mCurrentPath;
        final boolean includeHidden = areHiddenItemsShown();
        final FileFilter predicate = filterPredicate;
        return new AsyncTaskLoader<>(requireContext()) {
            private final Object observerLock = new Object();
            private FileObserver fileObserver;
            private boolean refreshPosted;
            // Only accessed on the main thread.
            private boolean loadInFlight;
            private boolean reloadAfterLoad;
            private final Handler observerHandler = new Handler(Looper.getMainLooper());
            private final Runnable refreshFiles = () -> {
                synchronized (observerLock) {
                    refreshPosted = false;
                }
                if (!isStarted() || isAbandoned() || isReset())
                    return;
                // Don't let repeated notifications keep canceling a slow directory listing.
                if (loadInFlight)
                    reloadAfterLoad = true;
                else
                    onContentChanged();
            };

            @Override
            public List<File> loadInBackground() {
                File[] listFiles = currentPath.listFiles();
                if (listFiles == null) {
                    Log.e(TAG, "FilePickerFragment: IO error while listing files");
                    return new ArrayList<>(0);
                }

                FileListResult files = new FileListResult(listFiles.length);
                for (File f : listFiles) {
                    if (isLoadInBackgroundCanceled())
                        return new ArrayList<>(0);
                    if (!includeHidden && f.isHidden())
                        continue;
                    if (predicate != null && !predicate.accept(f))
                        continue;
                    files.directories.put(f, f.isDirectory());
                    files.add(f);
                }

                sortFiles(files);
                return files;
            }

            /**
             * Handles a request to start the Loader.
             */
            @Override
            protected void onStartLoading() {
                super.onStartLoading();

                // Start watching for changes
                stopWatching();
                final FileObserver observer = createFileObserver(currentPath);
                synchronized (observerLock) {
                    fileObserver = observer;
                }
                observer.startWatching();

                forceLoad();
            }

            @Override
            protected void onForceLoad() {
                loadInFlight = true;
                super.onForceLoad();
            }

            @Override
            public void deliverResult(@Nullable List<File> data) {
                loadInFlight = false;
                if (reloadAfterLoad) {
                    reloadAfterLoad = false;
                    scheduleRefresh(fileObserver);
                }
                super.deliverResult(data);
            }

            private FileObserver createFileObserver(@NonNull File path) {
                int mask = FileObserver.CREATE |
                        FileObserver.DELETE |
                        FileObserver.MOVED_FROM |
                        FileObserver.MOVED_TO;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return new FileObserver(path, mask) {
                        @Override
                        public void onEvent(int event, String path) {
                            scheduleRefresh(this);
                        }
                    };
                }
                return createLegacyFileObserver(path.getPath(), mask);
            }

            @SuppressWarnings("deprecation")
            private FileObserver createLegacyFileObserver(@NonNull String path, int mask) {
                return new FileObserver(path, mask) {
                    @Override
                    public void onEvent(int event, String path) {
                        scheduleRefresh(this);
                    }
                };
            }

            private void scheduleRefresh(FileObserver source) {
                // Keep the first event's deadline; late events from a stopped watch are ignored.
                synchronized (observerLock) {
                    if (source != null && source == fileObserver && !refreshPosted)
                        refreshPosted = observerHandler.postDelayed(refreshFiles, REFRESH_WINDOW_MS);
                }
            }

            @Override
            protected void onStopLoading() {
                cancelLoad();
                loadInFlight = false;
                stopWatching();
            }

            @Override
            protected void onReset() {
                super.onReset();
                onStopLoading();
            }

            @Override
            protected void onAbandon() {
                super.onAbandon();
                onStopLoading();
            }

            private void stopWatching() {
                final FileObserver observer;
                synchronized (observerLock) {
                    observer = fileObserver;
                    fileObserver = null;
                    observerHandler.removeCallbacks(refreshFiles);
                    refreshPosted = false;
                }
                reloadAfterLoad = false;
                if (observer != null)
                    observer.stopWatching();
            }
        };
    }

    /** Sort a filtered listing on the loader's worker thread. */
    protected void sortFiles(@NonNull List<File> files) {
        Collections.sort(files, this::compareFiles);
    }

    /** Returns precomputed worker-thread directory flags when available. */
    @Nullable
    protected IdentityHashMap<File, Boolean> cachedDirectoryFlags(@NonNull List<File> files) {
        return (files instanceof FileListResult) ? ((FileListResult) files).directories : null;
    }

    /** Override to customize the default worker-thread order of directories first, then natural names. */
    protected int compareFiles(@NonNull File lhs, @NonNull File rhs) {
        final boolean ldir = lhs.isDirectory(), rdir = rhs.isDirectory();
        if (ldir != rdir)
            return rdir ? 1 : -1;
        return naturalCompare(lhs.getName(), rhs.getName());
    }

    private static final String TAG = "mpv";
}
