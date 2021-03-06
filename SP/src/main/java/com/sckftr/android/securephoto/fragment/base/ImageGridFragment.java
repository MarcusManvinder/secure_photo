package com.sckftr.android.securephoto.fragment.base;

import android.app.LoaderManager;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.sckftr.android.app.adapter.BaseCursorAdapter;
import com.sckftr.android.app.fragment.SickAdapterViewFragment;
import com.sckftr.android.securephoto.R;

import by.mcreader.imageloader.listener.PauseScrollListener;

public abstract class ImageGridFragment extends SickAdapterViewFragment<GridView, BaseCursorAdapter> implements LoaderManager.LoaderCallbacks<Cursor>, AbsListView.MultiChoiceModeListener {

    private PauseScrollListener mPauseScrollListener;

    private boolean mLoaderCreated = false;

    @Override
    protected int layoutId() {
        return R.layout.fragment_image_grid;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPauseScrollListener = new PauseScrollListener(API.images(), false, true);

        getLoaderManager().initLoader(1, null, this);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getAdapterView().setChoiceMode(isPhotosSecured() ? AbsListView.CHOICE_MODE_MULTIPLE_MODAL : AbsListView.CHOICE_MODE_MULTIPLE);
        getAdapterView().setMultiChoiceModeListener(this);

        setHasOptionsMenu(true);

        setSwipeRefreshEnabled(false);
    }

    @Override
    public void onPause() {
        super.onPause();

        API.images().setPauseWork(false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        inflater.inflate(R.menu.main_menu, menu);

        menu.findItem(R.id.add).setVisible(isPhotosSecured());
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        setRefreshing(true);

        Log.d("Loader", "onCreateLoader");

        mLoaderCreated = true;

        return getCursorLoader();
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Log.d("Loader", "onLoadFinished");

        setRefreshing(false);

        getAdapter().swapCursor(data);

        setListShown(true, !mLoaderCreated);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        setRefreshing(false);
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        super.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);

        mPauseScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        super.onScrollStateChanged(view, scrollState);

        mPauseScrollListener.onScrollStateChanged(view, scrollState);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {

        mode.setTitle(API.string(R.string.cab_title_select_items));

        return true;
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {

        mode.setSubtitle(API.qstring(R.plurals.selected_items, getAdapterView().getCheckedItemCount()));

    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return false;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
    }

    @Override
    public void populateInsets(Rect insets) {
        super.populateInsets(insets);

        final int spacing = getResources().getDimensionPixelSize(R.dimen.dim_small);
        final int margin = getResources().getDimensionPixelSize(R.dimen.unit3);

        getAdapterView().setPadding(insets.left,// + spacing,
                insets.top + spacing,
                insets.right,// + spacing,
                insets.bottom + spacing);

        final SwipeRefreshLayout srl = (SwipeRefreshLayout) aq.id(R.id.refreshContainer).getView();
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) srl.getLayoutParams();

        layoutParams.topMargin = insets.top;

        srl.setLayoutParams(layoutParams);

        final ImageButton button = (ImageButton) aq.id(R.id.hiding).getView();

        layoutParams = (RelativeLayout.LayoutParams) button.getLayoutParams();

        layoutParams.bottomMargin = insets.bottom + margin;
        layoutParams.rightMargin = insets.right + margin;

        button.setLayoutParams(layoutParams);
    }

    public abstract Loader<Cursor> getCursorLoader();

    public abstract boolean isPhotosSecured();
}
