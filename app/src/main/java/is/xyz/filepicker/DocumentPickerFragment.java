package is.xyz.filepicker;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Predicate;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * An implementation of the picker that operates on a document tree.
 * <br>
 * See also:
 * - https://developer.android.com/training/data-storage/shared/documents-files
 * - https://developer.android.com/reference/android/provider/DocumentsContract.Document#MIME_TYPE_DIR
 * - https://github.com/android/storage-samples/blob/main/ActionOpenDocumentTree/app/src/main/java/com/example/android/ktfiles/DirectoryFragmentViewModel.kt#L42
 * - https://github.com/googlearchive/android-DirectorySelection/blob/master/Application/src/main/java/com/example/android/directoryselection/DirectorySelectionFragment.java
 */
public class DocumentPickerFragment extends AbstractFilePickerFragment<Uri> {
    private final @NonNull Uri mRoot;
    // The structure of the file picker assumes that only the file URIs matter and you can
    // grab additional info for free afterwards. This is not the case with the documents API so we
    // have to work around it.
    private final HashMap<Uri, Document> mLastRead;
    // maps document ID of directories to parent path
    private final HashMap<String, Uri> mParents;
    protected Predicate<Document> mFilterPredicate;

    public DocumentPickerFragment(@NonNull Uri root) {
        mRoot = root;
        mLastRead = new HashMap<>();
        mParents = new HashMap<>();
    }

    /**
     * Check whether the given tree can be used with this file picker class.
     * @param context Application context
     * @param treeUri Tree URI from e.g. ACTION_OPEN_DOCUMENT_TREE
     * @return true if the directory exists
     */
    public static boolean isTreeUsable(@NonNull Context context, @NonNull Uri treeUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!DocumentsContract.isTreeUri(treeUri))
                return false;
        }
        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                DocumentsContract.getTreeDocumentId(treeUri));
        try {
            return isDir(context, docUri);
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * This method is used to set the filter that determines the documents to be shown
     * The predicate runs on a worker thread and must not retain views or activity contexts.
     *
     * @param predicate filter implementation or null
     */
    public void setFilterPredicate(@Nullable Predicate<Document> predicate) {
        this.mFilterPredicate = predicate;
        refresh(mCurrentPath);
    }

    /**
     * Returns the filter that determines the documents to be shown
     *
     * @return filter implementation or null
     */
    public @Nullable Predicate<Document> getFilterPredicate() {
        return this.mFilterPredicate;
    }

    @Override
    public boolean isDir(@NonNull Uri path) {
        Document doc = mLastRead.get(path);
        if (doc != null) {
            return doc.isDir;
        }

        // retrieve the data uncached (not supposed to happen)
        Log.w(TAG, "isDir(): uncached read");
        return isDir(requireContext(), path);
    }

    private static boolean isDir(@NonNull Context context, @NonNull Uri path) {
        final ContentResolver contentResolver = context.getContentResolver();
        final String[] cols = new String[] { DocumentsContract.Document.COLUMN_MIME_TYPE };
        try (Cursor c = contentResolver.query(path, cols, null, null, null, null)) {
            return c != null && c.moveToFirst() && DocumentsContract.Document.MIME_TYPE_DIR.equals(
                    readString(c, c.getColumnIndex(cols[0])));
        }
    }

    @NonNull
    @Override
    public String getName(@NonNull Uri path) {
        Document doc = mLastRead.get(path);
        if (doc != null) {
            return doc.displayName;
        }

        // retrieve the data uncached (not supposed to happen)
        final ContentResolver contentResolver = requireContext().getContentResolver();
        final String[] cols = new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME };
        try (Cursor c = contentResolver.query(path, cols, null, null, null, null)) {
            if (c == null || !c.moveToFirst())
                return "";
            final String name = readString(c, c.getColumnIndex(cols[0]));
            return name != null ? name : "";
        }
    }

    private static @Nullable String readString(Cursor cursor, int column) {
        if (column < 0)
            return null;
        final int type = cursor.getType(column);
        return type == Cursor.FIELD_TYPE_NULL || type == Cursor.FIELD_TYPE_BLOB
                ? null : cursor.getString(column);
    }

    @NonNull
    @Override
    public Uri getParent(@NonNull Uri from) {
        // root path is a tree and would error if given to getDocumentId(), catch that early
        if (from.equals(getRoot()))
            return getRoot();

        String docId = DocumentsContract.getDocumentId(from);
        Uri parent = mParents.get(docId);
        if (parent != null)
            return parent;
        Log.e(TAG, "getParent() has not seen this document before");
        return getRoot();
    }

    @NonNull
    @Override
    public String pathToString(@NonNull Uri path) {
        return path.toString();
    }

    @NonNull
    @Override
    public Uri pathFromString(@NonNull String path) {
        return Uri.parse(path);
    }

    @NonNull
    @Override
    public Uri getRoot() {
        return mRoot;
    }

    @NonNull
    @Override
    public Loader<List<Uri>> getLoader() {
        return new DocumentLoader(requireContext(), mRoot, mCurrentPath, mFilterPredicate);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<Uri>> loader, List<Uri> data) {
        // Only delivered results may update the UI's caches. Canceled workers never touch them.
        if (data instanceof DocumentResult) {
            DocumentResult result = (DocumentResult) data;
            mLastRead.clear();
            mLastRead.putAll(result.documents);
            mParents.putAll(result.parents);
        }
        super.onLoadFinished(loader, data);
    }

    private static final class DocumentResult extends ArrayList<Uri> {
        final HashMap<Uri, Document> documents = new HashMap<>();
        final HashMap<String, Uri> parents = new HashMap<>();
    }

    private static final class DocumentLoader extends AsyncTaskLoader<List<Uri>> {
        private final Uri root;
        private final Uri currentPath;
        private final Uri childUri;
        private final Predicate<Document> predicate;
        private final Object queryLock = new Object();
        private CancellationSignal querySignal;
        private static final String[] COLUMNS = new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        };

        DocumentLoader(Context context, Uri root, Uri currentPath, Predicate<Document> predicate) {
            super(context.getApplicationContext());
            this.root = root;
            this.currentPath = currentPath;
            this.predicate = predicate;
            final String docId = currentPath.equals(root)
                    ? DocumentsContract.getTreeDocumentId(currentPath)
                    : DocumentsContract.getDocumentId(currentPath);
            childUri = DocumentsContract.buildChildDocumentsUriUsingTree(root, docId);
        }

        @Override
        public List<Uri> loadInBackground() {
            final ContentResolver contentResolver = getContext().getContentResolver();
            final DocumentResult result = new DocumentResult();
            final ArrayList<Document> files = new ArrayList<>();
            final CancellationSignal signal = new CancellationSignal();
            synchronized (queryLock) {
                // A cancellation that beat the worker to this lock may have found no signal.
                if (isLoadInBackgroundCanceled())
                    return result;
                querySignal = signal;
            }

            try (Cursor c = contentResolver.query(childUri, COLUMNS, null, null, null, signal)) {
                if (c == null) {
                    return result;
                }

                final int idColumn = c.getColumnIndex(COLUMNS[0]);
                final int mimeColumn = c.getColumnIndex(COLUMNS[1]);
                final int nameColumn = c.getColumnIndex(COLUMNS[2]);
                if (idColumn < 0 || mimeColumn < 0)
                    return result;

                while (!isLoadInBackgroundCanceled() && c.moveToNext()) {
                    final String docId = readString(c, idColumn);
                    final String mimeType = readString(c, mimeColumn);
                    // A row without an ID or a usable type cannot be navigated safely.
                    if (docId == null || docId.isEmpty() || mimeType == null || mimeType.isEmpty())
                        continue;
                    final boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
                    final String name = readString(c, nameColumn);
                    final Document doc = new Document(
                            DocumentsContract.buildDocumentUriUsingTree(root, docId),
                            isDir,
                            name != null ? name : docId
                    );
                    if (predicate != null && !predicate.test(doc))
                        continue;
                    files.add(doc);

                    // Keep parents across directory changes so multiple levels of "up" still work.
                    if (isDir)
                        result.parents.put(docId, currentPath);
                }
            } catch (OperationCanceledException e) {
                // AsyncTaskLoader accepts a normal return for a canceled task and drops its result.
                if (!isLoadInBackgroundCanceled())
                    Log.w(TAG, "Document provider canceled the query", e);
                return new DocumentResult();
            } finally {
                synchronized (queryLock) {
                    querySignal = null;
                }
            }

            if (isLoadInBackgroundCanceled())
                return result;
            Collections.sort(files);
            for (Document doc : files) {
                result.add(doc.uri);
                result.documents.put(doc.uri, doc);
            }
            return result;
        }

        @Override
        public void cancelLoadInBackground() {
            super.cancelLoadInBackground();
            final CancellationSignal signal;
            synchronized (queryLock) {
                signal = querySignal;
            }
            if (signal != null)
                signal.cancel();
        }

        @Override
        protected void onStartLoading() {
            super.onStartLoading();
            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            cancelLoad();
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
    }

    /**
     * Class that represents a document.
     * Wrapper around a content:// URI but with extra information provided at no extra cost (cached).
     */
    public static class Document implements Comparable<Document> {
        private final @NonNull Uri uri;
        private final boolean isDir;
        private final @NonNull String displayName;

        private Document(@NonNull Uri uri, boolean dir, @NonNull String name) {
            this.uri = uri;
            isDir = dir;
            displayName = name;
        }

        @NonNull
        public Uri getUri() {
            return uri;
        }

        public boolean isDirectory() {
            return isDir;
        }

        @NonNull
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            // Cached info is irrelevant, same URI = same document
            return uri.equals(((Document) o).uri);
        }

        // Sort directories before files, in natural order otherwise
        @Override
        public int compareTo(Document other) {
            if (isDir != other.isDir)
                return other.isDir ? 1 : -1;
            return AbstractFilePickerFragment.naturalCompare(displayName, other.displayName);
        }
    }

    private static final String TAG = "mpv";
}
