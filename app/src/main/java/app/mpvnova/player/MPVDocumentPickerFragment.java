package app.mpvnova.player;

import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;

import is.xyz.filepicker.DocumentPickerFragment;

public class MPVDocumentPickerFragment extends DocumentPickerFragment {

    public MPVDocumentPickerFragment(@NonNull Uri root) {
        super(root);
    }

    @Override
    public void onClickCheckable(@NonNull View view, @NonNull FileViewHolder vh) {
        mListener.onDocumentPicked(vh.file, false);
    }

    @Override
    protected int getIconResource(@NonNull Uri uri) {
        if (isDir(uri))
            return R.drawable.ic_folder_white_48dp;
        if (Utils.isLikelyMediaPath(getName(uri)))
            return R.drawable.round_play_arrow_24;
        return R.drawable.ic_file_open_48dp;
    }

    @Override
    public boolean onLongClickCheckable(@NonNull View view, @NonNull DirViewHolder vh) {
        mListener.onDocumentPicked(vh.file, true);
        return true;
    }

    public boolean isBackTop() {
        return mCurrentPath.equals(getRoot());
    }
}
