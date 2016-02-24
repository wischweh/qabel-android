package de.qabel.qabelbox.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.cocosw.bottomsheet.BottomSheet;

import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Map;

import de.qabel.qabelbox.QabelBoxApplication;
import de.qabel.qabelbox.R;
import de.qabel.qabelbox.activities.BoxVolumeActivity;
import de.qabel.qabelbox.activities.MainActivity;
import de.qabel.qabelbox.adapter.FilesAdapter;
import de.qabel.qabelbox.communication.VolumeFileTransferHelper;
import de.qabel.qabelbox.exceptions.QblStorageException;
import de.qabel.qabelbox.helper.ExternalApps;
import de.qabel.qabelbox.services.LocalBroadcastConstants;
import de.qabel.qabelbox.services.LocalQabelService;
import de.qabel.qabelbox.storage.BoxFile;
import de.qabel.qabelbox.storage.BoxFolder;
import de.qabel.qabelbox.storage.BoxNavigation;
import de.qabel.qabelbox.storage.BoxObject;
import de.qabel.qabelbox.storage.BoxUploadingFile;
import de.qabel.qabelbox.storage.BoxVolume;
import de.qabel.qabelbox.storage.StorageSearch;

public class FilesFragment extends BaseFragment {

    private static final String TAG = "FilesFragment";
    protected BoxNavigation boxNavigation;
    public RecyclerView filesListRecyclerView;
    protected FilesAdapter filesAdapter;
    private RecyclerView.LayoutManager recyclerViewLayoutManager;
    private boolean isLoading;
    private FilesListListener mListener;
    protected SwipeRefreshLayout swipeRefreshLayout;
    private FilesFragment self;
    private BrowseToTask browseToTask;

    private MenuItem mSearchAction;
    private boolean isSearchOpened = false;
    private EditText edtSeach;
    protected BoxVolume mBoxVolume;
    private AsyncTask<String, Void, StorageSearch> searchTask;
    private StorageSearch mCachedStorageSearch;
    View mEmptyView;
    View mLoadingView;
    private LocalQabelService mService;

    private LoadDataTask loadDataTask;

    public static FilesFragment newInstance(final BoxVolume boxVolume) {

        final FilesFragment filesFragment = new FilesFragment();
        return filesFragment;
    }


    /**
     * loads data asynchronous if no other tasks are currently going on
     *
     * @param boxVolume
     * @return
     */
    protected void loadFragmentData(@NonNull BoxVolume boxVolume) {
        cancelSearchTask();
        cancelBrowseToTask();
        loadDataTask = new LoadDataTask(boxVolume);
        loadDataTask.executeOnExecutor(serialExecutor);

    }

    private void clearFilesAdapter() {
        if (getFilesAdapter() == null) {
            setAdapter(new FilesAdapter(new ArrayList<BoxObject>()));
        }
        getFilesAdapter().clear();
   }



    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);

			actionBar.setTitle(getTitle());
		}

        mService = QabelBoxApplication.getInstance().getService();
        self = this;
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mMessageReceiver,
                new IntentFilter(LocalBroadcastConstants.INTENT_UPLOAD_BROADCAST));
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (filesAdapter == null) {
                return;
            }
            String documentId = intent.getStringExtra(LocalBroadcastConstants.EXTRA_UPLOAD_DOCUMENT_ID);
            int uploadStatus = intent.getIntExtra(LocalBroadcastConstants.EXTRA_UPLOAD_STATUS, -1);

            switch (uploadStatus) {
                case LocalBroadcastConstants.UPLOAD_STATUS_NEW:
                    Log.d(TAG, "Received new uploadAndDeleteLocalfile: " + documentId);
                    fillAdapter(filesAdapter);
                    filesAdapter.notifyDataSetChanged();
                    break;
                case LocalBroadcastConstants.UPLOAD_STATUS_FINISHED:
                    Log.d(TAG, "Received upload finished: " + documentId);
                    fillAdapter(filesAdapter);
                    filesAdapter.notifyDataSetChanged();
                    break;
                case LocalBroadcastConstants.UPLOAD_STATUS_FAILED:
                    Log.d(TAG, "Received uploadAndDeleteLocalfile failed: " + documentId);
                    refresh();
                    break;
            }
        }
    };

    @Override
	public void onDestroy() {

        super.onDestroy();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mMessageReceiver);
    }

    @Override
	public void onStart() {

        super.onStart();
        updateSubtitle();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_files, container, false);
        setupLoadingViews(view);
        swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {

                mListener.onDoRefresh(self, boxNavigation, filesAdapter);
            }
        });

        swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {

                swipeRefreshLayout.setRefreshing(isLoading);
            }
        });
        filesListRecyclerView = (RecyclerView) view.findViewById(R.id.files_list);
        filesListRecyclerView.setHasFixedSize(true);

        recyclerViewLayoutManager = new LinearLayoutManager(view.getContext());
        filesListRecyclerView.setLayoutManager(recyclerViewLayoutManager);

        filesListRecyclerView.setAdapter(filesAdapter);

        filesListRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {

                super.onScrolled(recyclerView, dx, dy);
                int lastCompletelyVisibleItem = ((LinearLayoutManager) recyclerViewLayoutManager).findLastCompletelyVisibleItemPosition();
                int firstCompletelyVisibleItem = ((LinearLayoutManager) recyclerViewLayoutManager).findFirstCompletelyVisibleItemPosition();
                if (lastCompletelyVisibleItem == filesAdapter.getItemCount() - 1
                        && firstCompletelyVisibleItem > 0) {
                    mListener.onScrolledToBottom(true);
                } else {
                    mListener.onScrolledToBottom(false);
                }
            }
        });
        return view;
    }

    protected void setupLoadingViews(View view) {

        mEmptyView = view.findViewById(R.id.empty_view);
        mLoadingView = view.findViewById(R.id.loading_view);
        final ProgressBar pg = (ProgressBar) view.findViewById(R.id.pb_firstloading);
        pg.setIndeterminate(true);
        pg.setEnabled(true);
        if(filesAdapter!=null)
        filesAdapter.setEmptyView(mEmptyView, mLoadingView);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            BoxVolumeActivity boxVolumeInterface = (BoxVolumeActivity) activity;
            loadFragmentData(boxVolumeInterface.getBoxVolume());
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement BoxVolumeActivity");
        }
        try {
            mListener = (FilesListListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement FilesListListener");
        }
        setOnItemClickListener(new FilesAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {

                final BoxObject boxObject = getFilesAdapter().get(position);
                if (boxObject != null) {
                    if (boxObject instanceof BoxFolder) {
                        browseTo(((BoxFolder) boxObject));
                    } else if (boxObject instanceof BoxFile) {
                        // Open
                        // TODO This is a dangeorous cast and should be cleaned up as soon as the MainActivity/FilesFragment have a better seperation
                        ((MainActivity) getActivity()).showFile(boxObject);
                    }
                }
            }

            @Override
            public void onItemLockClick(View view, final int position) {

                final BoxObject boxObject = getFilesAdapter().get(position);
                new BottomSheet.Builder(getActivity()).title(boxObject.name).sheet(R.menu.files_bottom_sheet)
                        .listener(new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                switch (which) {
                                    case R.id.open:
                                        ExternalApps.openExternApp(getActivity(), VolumeFileTransferHelper.getUri(boxObject, mBoxVolume, getBoxNavigation()), getMimeType(boxObject), Intent.ACTION_VIEW);
                                        break;
                                    case R.id.edit:
                                        ExternalApps.openExternApp(getActivity(), VolumeFileTransferHelper.getUri(boxObject, mBoxVolume, getBoxNavigation()), getMimeType(boxObject), Intent.ACTION_EDIT);
                                        break;
                                    case R.id.share:
                                        ExternalApps.share(getActivity(), VolumeFileTransferHelper.getUri(boxObject, mBoxVolume, getBoxNavigation()), getMimeType(boxObject));
                                        break;
                                    case R.id.delete:
                                        MainActivity mainActivity = (MainActivity) getActivity();
                                        mainActivity.delete(boxObject);
                                        break;
                                    case R.id.export:
                                        // Export handled in the MainActivity
                                        if (boxObject instanceof BoxFolder) {
                                            Toast.makeText(getActivity(), R.string.folder_export_not_implemented,
                                                    Toast.LENGTH_SHORT).show();
                                        } else {
                                            ((MainActivity) getActivity()).onExport(getBoxNavigation(), boxObject);
                                        }
                                        break;
                                }
                            }
                        }).show();
            }

        });
    }

    //@todo move outside
    private String getMimeType(BoxObject boxObject) {

        return getMimeType(VolumeFileTransferHelper.getUri(boxObject, mBoxVolume, getBoxNavigation()));
    }

    //@todo move outside
    private String getMimeType(Uri uri) {

        return URLConnection.guessContentTypeFromName(uri.toString());
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        menu.clear();
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.ab_files, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {

        mSearchAction = menu.findItem(R.id.action_search);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // handle item selection
        switch (item.getItemId()) {
            case R.id.action_search:
                if (!isSearchRunning()) {
                    handleMenuSearch();
                } else if (isSearchOpened) {
                    removeSearchInActionbar(actionBar);
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean isSearchRunning() {

        if (isSearchOpened) {

            return true;
        }
        return searchTask != null && ((!searchTask.isCancelled() && searchTask.getStatus() != AsyncTask.Status.FINISHED));
    }

    /**
     * handle click on search icon
     */
    private void handleMenuSearch() {

        if (isSearchOpened) {
            removeSearchInActionbar(actionBar);
        } else {
            openSearchInActionBar(actionBar);
        }
    }

    /**
     * setup the actionbar to show a input dialog for search keyword
     *
     * @param action
     */
    private void openSearchInActionBar(final ActionBar action) {

        action.setDisplayShowCustomEnabled(true);
        action.setCustomView(R.layout.ab_search_field);
        action.setDisplayShowTitleEnabled(false);

        edtSeach = (EditText) action.getCustomView().findViewById(R.id.edtSearch);

        //add editor action listener
        edtSeach.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    String text = edtSeach.getText().toString();
                    removeSearchInActionbar(action);
                    startSearch(text);
                    return true;
                }
                return false;
            }
        });

        edtSeach.requestFocus();

        //open keyboard
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(edtSeach, InputMethodManager.SHOW_IMPLICIT);
        mActivity.fab.hide();
        mSearchAction.setIcon(R.drawable.ic_ab_close);
        isSearchOpened = true;
    }

    /**
     * restore the actionbar
     *
     * @param action
     */
    private void removeSearchInActionbar(ActionBar action) {

        action.setDisplayShowCustomEnabled(false);
        action.setDisplayShowTitleEnabled(true);

        //hides the keyboard
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(edtSeach.getWindowToken(), 0);

        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.RESULT_HIDDEN);
        mSearchAction.setIcon(R.drawable.ic_ab_search);
        action.setTitle(getTitle());
        isSearchOpened = false;
        mActivity.fab.show();
    }

    @Override
    public void onPause() {
        cancelBrowseToTask();
        cancelSearchTask();
        if (isSearchOpened) {
            removeSearchInActionbar(actionBar);
        }
        super.onPause();
    }

    /**
     * start search
     *
     * @param searchText
     */
    private boolean startSearch(final String searchText) {
        if (!areTasksPending()) {
            searchTask = new SearchTask(searchText);
            searchTask.executeOnExecutor(serialExecutor);
            return true;
        } else {
            return false;
        }
    }



    /**
     * Sets visibility of loading spinner. Visibility is stored if method is invoked
     * before onCreateView() has completed.
     *
     * @param isLoading
     */
    public void setIsLoading(final boolean isLoading) {

        this.isLoading = isLoading;
        if (swipeRefreshLayout == null) {
            return;
        }
        if (!isLoading) {
            mLoadingView.setVisibility(View.GONE);
        }
        swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {

                swipeRefreshLayout.setRefreshing(isLoading);
            }
        });
    }

    public void setAdapter(FilesAdapter adapter) {

        filesAdapter = adapter;
        filesAdapter.setEmptyView(mEmptyView, mLoadingView);
    }

    public FilesAdapter getFilesAdapter() {

        return filesAdapter;
    }

    public void setOnItemClickListener(FilesAdapter.OnItemClickListener onItemClickListener) {

        filesAdapter.setOnItemClickListener(onItemClickListener);
    }

    @Override
    public boolean isFabNeeded() {

        return true;
    }

    protected void setBoxNavigation(BoxNavigation boxNavigation) {

        this.boxNavigation = boxNavigation;
    }

    public BoxNavigation getBoxNavigation() {

        return boxNavigation;
    }

    @Override
    public String getTitle() {

        return getString(R.string.headline_files);
    }


    /**
     * handle back pressed
     *
     * @return true if back handled
     */


    public boolean handleBackPressed() {

        if (isSearchOpened) {
            removeSearchInActionbar(actionBar);
            return true;
        }
        if (searchTask != null && ((!searchTask.isCancelled() && searchTask.getStatus() != AsyncTask.Status.FINISHED))) {
            cancelSearchTask();
            return true;
        }

        return false;
    }

    public BoxVolume getBoxVolume() {

        return mBoxVolume;
    }

    public void setCachedSearchResult(StorageSearch searchResult) {

        mCachedStorageSearch = searchResult;
    }

    public void refresh() {

        if (boxNavigation == null) {
            Log.e(TAG, "Refresh failed because the boxNavigation object is null");
            return;
        }
        AsyncTask<Void, Void, Void> asyncTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    boxNavigation.reload();
					mService.getCachedFinishedUploads().clear();
					loadBoxObjectsToAdapter(boxNavigation, filesAdapter);
                } catch (QblStorageException e) {
                    Log.e(TAG, "refresh failed", e);
                }
                return null;
            }

            @Override
            protected void onCancelled() {

                setIsLoading(true);
                showAbortMessage();
            }

            @Override
            protected void onPreExecute() {

                setIsLoading(true);
            }

            @Override
            protected void onPostExecute(Void aVoid) {

                super.onPostExecute(aVoid);

                filesAdapter.sort();
                filesAdapter.notifyDataSetChanged();

                setIsLoading(false);
            }
        };
        asyncTask.execute();
    }

    private void showAbortMessage() {

        Toast.makeText(mActivity, R.string.aborted,
                Toast.LENGTH_SHORT).show();
    }

    public interface FilesListListener {

        void onScrolledToBottom(boolean scrolledToBottom);

        void onExport(BoxNavigation boxNavigation, BoxObject object);

        void onDoRefresh(FilesFragment filesFragment, BoxNavigation boxNavigation, FilesAdapter filesAdapter);
    }



    @Override
    public void updateSubtitle() {

        String path = boxNavigation != null ? boxNavigation.getPath() : "";
        if (path.equals("/")) {
            path = null;
        }
        if (actionBar != null)
            actionBar.setSubtitle(path);
    }

    protected void fillAdapter(FilesAdapter filesAdapter) {
        if (filesAdapter == null || boxNavigation == null) {
            return;
        }
        try {
			loadBoxObjectsToAdapter(boxNavigation, filesAdapter);
			insertCachedFinishedUploads(filesAdapter);
			insertPendingUploads(filesAdapter);
			filesAdapter.sort();
        } catch (QblStorageException e) {
            Log.e(TAG, "fillAdapter failed", e);
        }
    }

	private void insertPendingUploads(FilesAdapter filesAdapter) {
		if (mService != null && mService.getPendingUploads() != null) {
			Map<String, BoxUploadingFile> uploadsInPath = mService.getPendingUploads().get(boxNavigation.getPath());
			if (uploadsInPath != null) {
				for (BoxUploadingFile boxUploadingFile : uploadsInPath.values()) {
					filesAdapter.remove(boxUploadingFile.name);
					filesAdapter.add(boxUploadingFile);
				}
			}
		}
	}

	private void insertCachedFinishedUploads(FilesAdapter filesAdapter) {
		if (mService != null && mService.getCachedFinishedUploads() != null) {
			Map<String, BoxFile> cachedFiles = mService.getCachedFinishedUploads().get(boxNavigation.getPath());
			if (cachedFiles != null) {
				for (BoxFile boxFile : cachedFiles.values()) {
					filesAdapter.remove(boxFile.name);
					filesAdapter.add(boxFile);
				}
			}
		}
	}

	private void loadBoxObjectsToAdapter(BoxNavigation boxNavigation, FilesAdapter filesAdapter) throws QblStorageException {
		filesAdapter.clear();
		for (BoxFolder boxFolder : boxNavigation.listFolders()) {
			Log.d(TAG, "Adding folder: " + boxFolder.name);
			filesAdapter.add(boxFolder);
		}
		for (BoxObject boxExternal : boxNavigation.listExternals()) {
			Log.d(TAG, "Adding external: " + boxExternal.name);
			filesAdapter.add(boxExternal);
		}
		for (BoxFile boxFile : boxNavigation.listFiles()) {
			Log.d(TAG, "Adding file: " + boxFile.name);
			filesAdapter.add(boxFile);
		}
	}

	private void waitForBoxNavigation() {

        while (boxNavigation == null) {
            Log.d(TAG, "waiting for BoxNavigation");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    @Override
    public boolean supportSubtitle() {

        return true;
    }


    public boolean areTasksPending() {
        return searchTask != null || browseToTask != null;
    }

    public void cancelOngoingTasks() {
        cancelBrowseToTask();
        cancelSearchTask();
    }

    private void cancelSearchTask() {

        if (searchTask != null) {
            searchTask.cancel(true);
        }
    }

    private void cancelBrowseToTask() {
        if (browseToTask != null) {
            Log.d(TAG, "Found a running browseToTask");
            browseToTask.cancel(true);
            Log.d(TAG, "Canceled browserToTask");
        }
    }


    /**
     * Triggers asynchronous loading and navigating to the current parent folder.
     *
     * @return true if task has been triggered. False if going up is not possible or another navigating task is already going on
     */
    public boolean browseToParent() {
        if (boxNavigation == null || !boxNavigation.hasParent()) {
            return false;
        }
        if (!areTasksPending()) {
            browseToTask = new BrowseToTask(null);
            browseToTask.executeOnExecutor(serialExecutor);
            return true;
        } else {
            Log.d(TAG, "browsing already ongoing. Will skip");
            return false;
        }
    }

    /**
     * Triggers asynchronous loading and navigating to the current parent folder.
     *
     * @return true if task has been triggered. False if going up is not possible or another navigating task is already going on
     */
    public boolean browseTo(@NonNull BoxFolder target) {
        if (!areTasksPending()) {
            browseToTask = new BrowseToTask(target);
            browseToTask.executeOnExecutor(serialExecutor);
            return true;
        } else {
            Log.d(TAG, "browsing already ongoing. Will skip");
            return false;
        }
    }

    /**
     * A task to browse to (and load the data)
     * if the target folder is null the denotes the current parent folder
     */

    public class BrowseToTask extends AsyncTask<Void, Void, Void> {

        BoxFolder targetFolder;

        /**
         * @param targetFolder
         */
        public BrowseToTask(BoxFolder targetFolder) {
            this.targetFolder = targetFolder;
        }

        @Override
        protected void onPreExecute() {

            super.onPreExecute();
            setIsLoading(true);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            waitForBoxNavigation();
            try {
                if (targetFolder == null) {
                    boxNavigation.navigateToParent();
                } else {
                    boxNavigation.navigate(targetFolder);
                }
                fillAdapter(filesAdapter);
            } catch (QblStorageException e) {
                Log.d(TAG, "browseTo failed", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            setIsLoading(false);
            updateSubtitle();
            filesAdapter.notifyDataSetChanged();
            browseToTask = null;
        }

    }

    public class SearchTask extends AsyncTask<String, Void, StorageSearch> {

        String searchText;

        public SearchTask(String searchText) {
            this.searchText = searchText;
        }

        @Override
        protected void onPreExecute() {

            super.onPreExecute();
            setIsLoading(true);
        }

        @Override
        protected void onCancelled(StorageSearch storageSearch) {

            setIsLoading(false);
            super.onCancelled(storageSearch);
        }

        @Override
        protected void onPostExecute(StorageSearch storageSearch) {

            setIsLoading(false);

            //check if files found
            if (storageSearch == null || storageSearch.filterOnlyFiles().getResults().size() == 0) {
                Toast.makeText(getActivity(), R.string.no_entrys_found, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!mActivity.isFinishing() && !searchTask.isCancelled()) {
                boolean needRefresh = mCachedStorageSearch != null;
                try {
                    mCachedStorageSearch = storageSearch.clone();
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }

                FilesSearchResultFragment fragment = FilesSearchResultFragment.newInstance(mCachedStorageSearch, searchText, needRefresh);
                mActivity.toggle.setDrawerIndicatorEnabled(false);
                getFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment, FilesSearchResultFragment.TAG).addToBackStack(null).commit();
            }
        }

        @Override
        protected StorageSearch doInBackground(String... params) {

            try {
                if (mCachedStorageSearch != null && mCachedStorageSearch.getResults().size() > 0) {
                    return mCachedStorageSearch;
                }

                return new StorageSearch(mBoxVolume.navigate());
            } catch (QblStorageException e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    public class LoadDataTask extends AsyncTask<Void, Void, Void> {
        BoxVolume targetVolume;

        public LoadDataTask(BoxVolume targetVolume) {
            this.targetVolume = targetVolume;
        }

        @Override
        protected void onPreExecute() {

            super.onPreExecute();
            setIsLoading(true);
            clearFilesAdapter();
        }

        @Override
        protected Void doInBackground(Void... params) {

            try {
                setBoxNavigation(targetVolume.navigate());
            } catch (QblStorageException e) {
                Log.w(TAG, "Cannot navigate to root. maybe first initialization", e);
                try {
                    targetVolume.createIndex();
                    setBoxNavigation(targetVolume.navigate());
                } catch (QblStorageException e1) {
                    Log.e(TAG, "Creating a volume failed", e1);
                    cancel(true);
                    return null;
                }
            }
            fillAdapter(filesAdapter);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {

            super.onPostExecute(aVoid);
            setIsLoading(false);
            filesAdapter.notifyDataSetChanged();
        }
    }
}
