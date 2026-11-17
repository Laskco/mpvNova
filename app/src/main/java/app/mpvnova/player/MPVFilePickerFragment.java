package app.mpvnova.player;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;

import is.xyz.filepicker.FilePickerFragment;

import java.io.File;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

public class MPVFilePickerFragment extends FilePickerFragment {

    private File rootPath = new File("/");
    private String rootLabel = "";

    @Override
    protected void sortFiles(@NonNull List<File> files) {
        if (getClass() != MPVFilePickerFragment.class) {
            // Let subclasses keep their compareFiles override.
            super.sortFiles(files);
            return;
        }
        IdentityHashMap<File, Boolean> directories = new IdentityHashMap<>(files.size());
        for (File file : files)
            directories.put(file, file.isDirectory());
        Collections.sort(files, (lhs, rhs) -> {
            final boolean ldir = directories.get(lhs), rdir = directories.get(rhs);
            if (ldir != rdir)
                return rdir ? 1 : -1;
            return naturalCompare(lhs.getName(), rhs.getName());
        });
    }

    @Override
    public void onClickCheckable(@NonNull View v, @NonNull FileViewHolder vh) {
        mListener.onFilePicked(vh.file);
    }

    @Override
    protected int getIconResource(@NonNull File file) {
        if (file.isDirectory())
            return R.drawable.ic_folder_white_48dp;
        if (Utils.isLikelyMediaPath(file.getName()))
            return R.drawable.round_play_arrow_24;
        return R.drawable.ic_file_open_48dp;
    }

    @Override
    public boolean onLongClickCheckable(@NonNull View v, @NonNull DirViewHolder vh) {
        mListener.onDirPicked(vh.file);
        return true;
    }

    public File currentDirectory() {
        return mCurrentPath;
    }

    @Override
    protected void onPermissionGranted(@NonNull File requestedPath) {
        if (getActivity() instanceof FilePickerActivity) {
            ((FilePickerActivity) getActivity()).onFilePermissionGranted();
        } else {
            super.onPermissionGranted(requestedPath);
        }
    }

    @NonNull
    @Override
    public File getRoot() {
        return rootPath;
    }

    public void setRoot(@NonNull File path) {
        rootPath = path;
    }

    public void setRootLabel(@NonNull String label) {
        rootLabel = label;
    }

    public boolean isBackTop() {
        return mCurrentPath.equals(getRoot());
    }

    private @NonNull String makeRelative(@NonNull String path) {
        String head = getRoot().toString();
        if (path.equals(head))
            return rootLabel.isEmpty() ? head : rootLabel;
        if (!head.endsWith("/"))
            head += "/";
        String relative = path.startsWith(head) ? path.substring(head.length()) : path;
        return rootLabel.isEmpty() ? relative : rootLabel + " / " + relative;
    }

    @Override
    public void onChangePath(File file) {
        ActionBar bar = ((AppCompatActivity)getActivity()).getSupportActionBar();
        if (file != null) {
            String location = makeRelative(file.getPath());
            if (bar != null)
                bar.setSubtitle(location);
            ((FilePickerActivity)getActivity()).setPickerLocation(location);
        }
    }
}
