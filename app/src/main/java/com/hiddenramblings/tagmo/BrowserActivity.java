package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;
import com.robertlevonyan.views.chip.Chip;
import com.robertlevonyan.views.chip.OnCloseClickListener;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.OptionsMenuItem;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.sharedpreferences.Pref;
import org.apmem.tools.layouts.FlowLayout;
import org.json.JSONException;
import org.parceler.Parcel;
import org.parceler.ParcelConstructor;
import org.parceler.Parcels;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

@EActivity(R.layout.browser_layout)
@OptionsMenu({R.menu.browser_menu})
public class BrowserActivity extends AppCompatActivity implements SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {
    public static final int SORT_ID = 0x0;
    public static final int SORT_NAME = 0x1;
    public static final int SORT_AMIIBO_SERIES = 0x2;
    public static final int SORT_AMIIBO_TYPE = 0x3;
    public static final int SORT_GAME_SERIES = 0x4;
    public static final int SORT_CHARACTER = 0x5;
    public static final int SORT_FILE_PATH = 0x6;

    public static final int VIEW_TYPE_SIMPLE = 0;
    public static final int VIEW_TYPE_COMPACT = 1;
    public static final int VIEW_TYPE_LARGE = 2;

    @ViewById(R.id.chip_list)
    FlowLayout chipList;
    @ViewById(R.id.list)
    RecyclerView amiibosView;
    @ViewById(R.id.swiperefresh)
    SwipeRefreshLayout swipeRefreshLayout;
    @ViewById(R.id.internalEmpty)
    TextView emptyText;
    @ViewById(R.id.folders)
    RecyclerView foldersView;
    @ViewById(R.id.bottom_sheet)
    ViewGroup bottomSheet;
    @ViewById(R.id.current_folder)
    TextView currentFolderView;
    @ViewById(R.id.toggle)
    ImageView toggle;

    @OptionsMenuItem(R.id.search)
    MenuItem menuSearch;
    @OptionsMenuItem(R.id.sort_id)
    MenuItem menuSortId;
    @OptionsMenuItem(R.id.sort_name)
    MenuItem menuSortName;
    @OptionsMenuItem(R.id.sort_game_series)
    MenuItem menuSortGameSeries;
    @OptionsMenuItem(R.id.sort_character)
    MenuItem menuSortCharacter;
    @OptionsMenuItem(R.id.sort_amiibo_series)
    MenuItem menuSortAmiiboSeries;
    @OptionsMenuItem(R.id.sort_amiibo_type)
    MenuItem menuSortAmiiboType;
    @OptionsMenuItem(R.id.sort_file_path)
    MenuItem menuSortFilePath;
    @OptionsMenuItem(R.id.filter_game_series)
    MenuItem menuFilterGameSeries;
    @OptionsMenuItem(R.id.filter_character)
    MenuItem menuFilterCharacter;
    @OptionsMenuItem(R.id.filter_amiibo_series)
    MenuItem menuFilterAmiiboSeries;
    @OptionsMenuItem(R.id.filter_amiibo_type)
    MenuItem menuFilterAmiiboType;
    @OptionsMenuItem(R.id.view_simple)
    MenuItem menuViewSimple;
    @OptionsMenuItem(R.id.view_compact)
    MenuItem menuViewCompact;
    @OptionsMenuItem(R.id.view_large)
    MenuItem menuViewLarge;
    @OptionsMenuItem(R.id.refresh)
    MenuItem menuRefresh;

    SearchView searchView;

    BottomSheetBehavior bottomSheetBehavior;
    ArrayList<AmiiboFile> amiiboFiles = null;
    AmiiboManager amiiboManager = null;
    File currentFolder;

    @Pref
    Preferences_ prefs;

    @Parcel
    public static class AmiiboFile {
        String filePath;
        long id;

        @ParcelConstructor
        public AmiiboFile(String filePath, long id) {
            this.filePath = filePath;
            this.id = id;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            amiiboFiles = Parcels.unwrap(savedInstanceState.getParcelable("amiiboFiles"));
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("amiiboFiles", Parcels.wrap(amiiboFiles));
    }

    @AfterViews
    protected void afterViews() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });

        this.foldersView.setLayoutManager(new LinearLayoutManager(this));
        this.foldersView.setAdapter(new FoldersAdapter(this));

        setGameSeriesFilter(getGameSeriesFilter());
        setCharacterFilter(getCharacterFilter());
        setAmiiboSeriesFilter(getAmiiboSeriesFilter());
        setAmiiboTypeFilter(getAmiiboTypeFilter());

        this.swipeRefreshLayout.setOnRefreshListener(this);
        this.amiibosView.setLayoutManager(new LinearLayoutManager(this));
        this.amiibosView.setAdapter(new AmiibosAdapter(this));


        String folderName = prefs.browserFolder().get();
        File folder;
        if (folderName == null) {
            folder = Util.getDataDir();
        } else {
            folder = new File(Util.getSDCardDir(), folderName);
            if (!folder.exists() || !folder.isDirectory()) {
                folder = Util.getDataDir();
            }
        }
        loadFolders(folder);
        loadAmiiboManager();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);

        setSort(getSort());
        setView(getView());

        // setOnQueryTextListener will clear this, so make a copy
        String query = getQuery();

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) menuSearch.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(true);
        searchView.setOnQueryTextListener(this);

        //focus the SearchView
        if (!query.isEmpty()) {
            menuSearch.expandActionView();
            searchView.setQuery(query, true);
            searchView.clearFocus();
        }

        return result;
    }

    protected AmiibosAdapter getAmiiboFilesAdapter() {
        return (AmiibosAdapter) this.amiibosView.getAdapter();
    }

    protected FoldersAdapter getFoldersAdapter() {
        return (FoldersAdapter) this.foldersView.getAdapter();
    }

    @Override
    public void onRefresh() {
        this.loadAmiiboManager();
        this.loadFolders(getCurrentFolder());
    }

    @Background
    void loadAmiiboManager() {
        AmiiboManager amiiboManager = null;
        try {
            amiiboManager = Util.loadAmiiboManager(this);
        } catch (IOException | JSONException | ParseException e) {
            e.printStackTrace();
            showToast("Unable to parse amiibo info");
        }
        this.setAmiiboManager(amiiboManager);
    }

    @UiThread
    void setAmiiboManager(AmiiboManager amiiboManager) {
        this.amiiboManager = amiiboManager;
        this.getAmiiboFilesAdapter().refresh();
    }

    File getCurrentFolder() {
        return currentFolder;
    }

    @UiThread
    void setCurrentFolder(File currentFolder) {
        this.currentFolder = currentFolder;

        String folderPath = Util.friendlyPath(currentFolder.getAbsolutePath());
        prefs.browserFolder().put(folderPath);
        this.currentFolderView.setText(folderPath);

        this.getFoldersAdapter().setCurrentFolder(currentFolder);
        this.getFoldersAdapter().notifyDataSetChanged();

        loadAmiiboFiles();
    }

    @Background
    void loadFolders(File rootFolder) {
        setCurrentFolder(rootFolder);

        ArrayList<File> folders = listFolders(rootFolder);
        Collections.sort(folders, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                return file1.getPath().compareToIgnoreCase(file2.getPath());
            }
        });
        setFolders(folders);
    }

    ArrayList<File> listFolders(File rootFolder) {
        ArrayList<File> folders = new ArrayList<>();

        File[] files = rootFolder.listFiles();
        if (files == null)
            return folders;

        for (File file : files) {
            if (file.isDirectory()) {
                folders.add(file);
            }
        }
        return folders;
    }

    @UiThread
    void setFolders(ArrayList<File> folders) {
        this.getFoldersAdapter().setData(folders);
    }

    @Background
    void loadAmiiboFiles() {
        setLoadingBarVisibility(true);
        ArrayList<AmiiboFile> amiiboFiles = listAmiibos(getCurrentFolder());
        setLoadingBarVisibility(false);
        setAmiiboFiles(amiiboFiles);
    }

    ArrayList<AmiiboFile> listAmiibos(File rootFolder) {
        ArrayList<AmiiboFile> amiiboFiles = new ArrayList<>();

        File[] files = rootFolder.listFiles();
        if (files == null)
            return amiiboFiles;

        for (File file : files) {
            if (file.isDirectory()) {
                amiiboFiles.addAll(listAmiibos(file));
            } else {
                try {
                    byte[] data = TagUtil.readTag(new FileInputStream(file));
                    TagUtil.validateTag(data);
                    amiiboFiles.add(new AmiiboFile(file.getAbsolutePath(), TagUtil.amiiboIdFromTag(data)));
                } catch (Exception e) {
                    //
                }
            }
        }
        return amiiboFiles;
    }

    @UiThread
    void setAmiiboFiles(ArrayList<AmiiboFile> amiiboFiles) {
        this.amiiboFiles = amiiboFiles;
        this.getAmiiboFilesAdapter().setData(amiiboFiles);
        if (amiiboFiles != null && amiiboFiles.size() == 0) {
            emptyText.setText("No Amiibos found");
        } else {
            emptyText.setText("");
        }
    }

    @UiThread
    void setLoadingBarVisibility(boolean visible) {
        this.swipeRefreshLayout.setRefreshing(visible);
    }

    public String getQuery() {
        return this.prefs.query().get();
    }

    public void setQuery(String query) {
        this.prefs.query().put(query);
    }

    public int getSort() {
        return this.prefs.sort().get();
    }

    public void setSort(int sort) {
        this.prefs.sort().put(sort);
        if (sort == SORT_ID) {
            menuSortId.setChecked(true);
        } else if (sort == SORT_NAME) {
            menuSortName.setChecked(true);
        } else if (sort == SORT_GAME_SERIES) {
            menuSortGameSeries.setChecked(true);
        } else if (sort == SORT_CHARACTER) {
            menuSortCharacter.setChecked(true);
        } else if (sort == SORT_AMIIBO_SERIES) {
            menuSortAmiiboSeries.setChecked(true);
        } else if (sort == SORT_AMIIBO_TYPE) {
            menuSortAmiiboType.setChecked(true);
        } else if (sort == SORT_FILE_PATH) {
            menuSortFilePath.setChecked(true);
        }
    }

    public int getView() {
        return this.prefs.browserAmiiboView().get();
    }

    public void setView(int view) {
        this.prefs.browserAmiiboView().put(view);
        if (view == VIEW_TYPE_SIMPLE) {
            menuViewSimple.setChecked(true);
        } else if (view == VIEW_TYPE_COMPACT) {
            menuViewCompact.setChecked(true);
        } else if (view == VIEW_TYPE_LARGE) {
            menuViewLarge.setChecked(true);
        }
    }

    public String getGameSeriesFilter() {
        return this.prefs.filterGameSeries().get();
    }

    public void setGameSeriesFilter(String gameSeriesFilter) {
        this.prefs.filterGameSeries().put(gameSeriesFilter);

        addFilterItemView(gameSeriesFilter, "game_series", onFilterGameSeriesChipClick);
    }

    public String getCharacterFilter() {
        return this.prefs.filterCharacter().get();
    }

    public void setCharacterFilter(String characterFilter) {
        this.prefs.filterCharacter().put(characterFilter);

        addFilterItemView(characterFilter, "character", onFilterCharacterChipClick);
    }

    public String getAmiiboSeriesFilter() {
        return this.prefs.filterAmiiboSeries().get();
    }

    public void setAmiiboSeriesFilter(String amiiboSeriesFilter) {
        this.prefs.filterAmiiboSeries().put(amiiboSeriesFilter);

        addFilterItemView(amiiboSeriesFilter, "amiibo_series", onFilterAmiiboSeriesChipClick);
    }

    public String getAmiiboTypeFilter() {
        return this.prefs.filterAmiiboType().get();
    }

    public void setAmiiboTypeFilter(String amiiboTypeFilter) {
        this.prefs.filterAmiiboType().put(amiiboTypeFilter);

        addFilterItemView(amiiboTypeFilter, "amiibo_type", onAmiiboTypeChipClick);
    }

    public boolean matchesGameSeriesFilter(GameSeries gameSeries) {
        if (gameSeries != null) {
            String filterGameSeries = getGameSeriesFilter();
            if (!filterGameSeries.isEmpty() && !gameSeries.name.equals(filterGameSeries))
                return false;
        }
        return true;
    }

    public boolean matchesCharacterFilter(Character character) {
        if (character != null) {
            String filterCharacter = getCharacterFilter();
            if (!filterCharacter.isEmpty() && !character.name.equals(filterCharacter))
                return false;
        }
        return true;
    }

    public boolean matchesAmiiboSeriesFilter(AmiiboSeries amiiboSeries) {
        if (amiiboSeries != null) {
            String filterAmiiboSeries = getAmiiboSeriesFilter();
            if (!filterAmiiboSeries.isEmpty() && !amiiboSeries.name.equals(filterAmiiboSeries))
                return false;
        }
        return true;
    }

    public boolean matchesAmiiboTypeFilter(AmiiboType amiiboType) {
        if (amiiboType != null) {
            String filterAmiiboType = getAmiiboTypeFilter();
            if (!filterAmiiboType.isEmpty() && !amiiboType.name.equals(filterAmiiboType))
                return false;
        }
        return true;
    }

    public void addFilterItemView(String text, String tag, OnCloseClickListener listener) {
        FrameLayout chipContainer = chipList.findViewWithTag(tag);
        chipList.removeView(chipContainer);
        if (!text.isEmpty()) {
            chipContainer = (FrameLayout) getLayoutInflater().inflate(R.layout.chip_view, null);
            chipContainer.setTag(tag);
            Chip chip = chipContainer.findViewById(R.id.chip);
            chip.setChipText(text);
            chip.setClosable(true);
            chip.setOnCloseClickListener(listener);
            chipList.addView(chipContainer);
            chipList.setVisibility(View.VISIBLE);
        } else if (chipList.getChildCount() == 0) {
            chipList.setVisibility(View.GONE);
        }
    }

    @Click(R.id.toggle)
    void onToggleClick() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    @OptionsItem(R.id.sort_id)
    void onSortIdClick() {
        setSort(SORT_ID);
        this.getAmiiboFilesAdapter().refresh();
    }

    @OptionsItem(R.id.sort_name)
    void onSortNameClick() {
        setSort(SORT_NAME);
        this.getAmiiboFilesAdapter().refresh();
    }

    @OptionsItem(R.id.sort_game_series)
    void onSortGameSeriesClick() {
        setSort(SORT_GAME_SERIES);
        this.getAmiiboFilesAdapter().refresh();
    }

    @OptionsItem(R.id.sort_character)
    void onSortCharacterClick() {
        setSort(SORT_CHARACTER);
        this.getAmiiboFilesAdapter().refresh();
    }

    @OptionsItem(R.id.sort_amiibo_series)
    void onSortAmiiboSeriesClick() {
        setSort(SORT_AMIIBO_SERIES);
        this.getAmiiboFilesAdapter().refresh();
    }

    @OptionsItem(R.id.sort_amiibo_type)
    void onSortAmiiboTypeClick() {
        setSort(SORT_AMIIBO_TYPE);
        this.getAmiiboFilesAdapter().refresh();
    }

    @OptionsItem(R.id.sort_file_path)
    void onSortFilePathClick() {
        setSort(SORT_FILE_PATH);
        this.getAmiiboFilesAdapter().refresh();
    }

    @OptionsItem(R.id.view_simple)
    void onViewSimpleClick() {
        setView(VIEW_TYPE_SIMPLE);
        this.getAmiiboFilesAdapter().refresh();
    }

    @OptionsItem(R.id.view_compact)
    void onViewCompactClick() {
        setView(VIEW_TYPE_COMPACT);
        this.getAmiiboFilesAdapter().refresh();
    }

    @OptionsItem(R.id.view_large)
    void onViewLargeClick() {
        setView(VIEW_TYPE_LARGE);
        this.getAmiiboFilesAdapter().refresh();
    }

    @OptionsItem(R.id.refresh)
    void onRefreshClicked() {
        this.setAmiiboFiles(null);
        this.onRefresh();
    }

    @OptionsItem(R.id.filter_game_series)
    boolean onFilterGameSeriesClick() {
        SubMenu subMenu = menuFilterGameSeries.getSubMenu();
        subMenu.clear();

        AmiibosAdapter adapter = getAmiiboFilesAdapter();
        if (amiiboManager == null)
            return false;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : adapter.data) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.id);
            if (amiibo == null)
                continue;

            GameSeries gameSeries = amiibo.getGameSeries();
            if (gameSeries != null &&
                matchesCharacterFilter(amiibo.getCharacter()) &&
                matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries()) &&
                matchesAmiiboTypeFilter(amiibo.getAmiiboType())
                ) {
                items.add(gameSeries.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_game_series_group, Menu.NONE, 0, item)
                .setChecked(item.equals(getGameSeriesFilter()))
                .setOnMenuItemClickListener(onFilterGameSeriesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_game_series_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterGameSeriesItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            setGameSeriesFilter(menuItem.getTitle().toString());
            getAmiiboFilesAdapter().refresh();
            return false;
        }
    };

    OnCloseClickListener onFilterGameSeriesChipClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(View v) {
            setGameSeriesFilter("");
            getAmiiboFilesAdapter().refresh();
        }
    };

    @OptionsItem(R.id.filter_character)
    boolean onFilterCharacterClick() {
        SubMenu subMenu = menuFilterCharacter.getSubMenu();
        subMenu.clear();

        AmiibosAdapter adapter = getAmiiboFilesAdapter();
        if (amiiboManager == null)
            return true;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : adapter.data) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.id);
            if (amiibo == null)
                continue;

            Character character = amiibo.getCharacter();
            if (character != null &&
                matchesGameSeriesFilter(amiibo.getGameSeries()) &&
                matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries()) &&
                matchesAmiiboTypeFilter(amiibo.getAmiiboType())
                ) {
                items.add(character.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_character_group, Menu.NONE, 0, item)
                .setChecked(item.equals(getCharacterFilter()))
                .setOnMenuItemClickListener(onFilterCharacterItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_character_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterCharacterItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            setCharacterFilter(menuItem.getTitle().toString());
            getAmiiboFilesAdapter().refresh();
            return false;
        }
    };

    OnCloseClickListener onFilterCharacterChipClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(View v) {
            setCharacterFilter("");

            getAmiiboFilesAdapter().refresh();
        }
    };

    @OptionsItem(R.id.filter_amiibo_series)
    boolean onFilterAmiiboSeriesClick() {
        SubMenu subMenu = menuFilterAmiiboSeries.getSubMenu();
        subMenu.clear();

        AmiibosAdapter adapter = getAmiiboFilesAdapter();
        if (amiiboManager == null)
            return true;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : adapter.data) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.id);
            if (amiibo == null)
                continue;

            AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
            if (amiiboSeries != null &&
                matchesGameSeriesFilter(amiibo.getGameSeries()) &&
                matchesCharacterFilter(amiibo.getCharacter()) &&
                matchesAmiiboTypeFilter(amiibo.getAmiiboType())
                ) {
                items.add(amiiboSeries.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_amiibo_series_group, Menu.NONE, 0, item)
                .setChecked(item.equals(getAmiiboSeriesFilter()))
                .setOnMenuItemClickListener(onFilterAmiiboSeriesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_series_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterAmiiboSeriesItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            setAmiiboSeriesFilter(menuItem.getTitle().toString());
            getAmiiboFilesAdapter().refresh();
            return false;
        }
    };

    OnCloseClickListener onFilterAmiiboSeriesChipClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(View v) {
            setAmiiboSeriesFilter("");
            getAmiiboFilesAdapter().refresh();
        }
    };

    @OptionsItem(R.id.filter_amiibo_type)
    boolean onFilterAmiiboTypeClick() {
        SubMenu subMenu = menuFilterAmiiboType.getSubMenu();
        subMenu.clear();

        AmiibosAdapter adapter = getAmiiboFilesAdapter();
        if (amiiboManager == null)
            return true;

        Set<AmiiboType> items = new HashSet<>();
        for (AmiiboFile amiiboFile : adapter.data) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.id);
            if (amiibo == null)
                continue;

            AmiiboType amiiboType = amiibo.getAmiiboType();
            if (amiiboType != null &&
                matchesGameSeriesFilter(amiibo.getGameSeries()) &&
                matchesCharacterFilter(amiibo.getCharacter()) &&
                matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries())
                ) {
                items.add(amiiboType);
            }
        }

        ArrayList<AmiiboType> list = new ArrayList<>(items);
        Collections.sort(list);
        for (AmiiboType item : list) {
            subMenu.add(R.id.filter_amiibo_type_group, Menu.NONE, 0, item.name)
                .setChecked(item.name.equals(getAmiiboTypeFilter()))
                .setOnMenuItemClickListener(onFilterAmiiboTypeItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_type_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterAmiiboTypeItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            setAmiiboTypeFilter(menuItem.getTitle().toString());
            getAmiiboFilesAdapter().refresh();
            return false;
        }
    };

    OnCloseClickListener onAmiiboTypeChipClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(View v) {
            setAmiiboTypeFilter("");
            getAmiiboFilesAdapter().refresh();
        }
    };

    @Override
    public boolean onQueryTextChange(String query) {
        this.getAmiiboFilesAdapter().getFilter().filter(query);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        this.getAmiiboFilesAdapter().getFilter().filter(query);
        searchView.clearFocus();
        return true;
    }

    static abstract class FolderViewHolder extends RecyclerView.ViewHolder {
        public FolderViewHolder(View itemView) {
            super(itemView);
        }

        abstract void bind(BrowserActivity activity, File folder);
    }

    static class ParentFolderViewHolder extends FolderViewHolder {
        BrowserActivity activity;
        File folder;

        public ParentFolderViewHolder(ViewGroup parent) {
            this(LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.parent_folder_view, parent, false));
        }

        public ParentFolderViewHolder(View itemView) {
            super(itemView);

            this.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    activity.loadFolders(folder);
                }
            });
        }

        @Override
        void bind(BrowserActivity activity, File folder) {
            this.activity = activity;
            this.folder = folder;
        }
    }

    static class ChildFolderViewHolder extends FolderViewHolder {
        BrowserActivity activity;
        File folder;
        TextView txtFolderName;

        public ChildFolderViewHolder(ViewGroup parent) {
            this(LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.child_folder_view, parent, false));
        }

        public ChildFolderViewHolder(View itemView) {
            super(itemView);

            this.txtFolderName = itemView.findViewById(R.id.text);
            this.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    activity.loadFolders(folder);
                }
            });
        }

        @Override
        void bind(BrowserActivity activity, File folder) {
            this.activity = activity;
            this.folder = folder;
            this.txtFolderName.setText(folder.getName());
        }
    }

    static class FoldersAdapter extends RecyclerView.Adapter<FolderViewHolder> {
        public static final int PARENT_FOLDER_VIEW_TYPE = 0;
        public static final int CHILD_FOLDER_VIEW_TYPE = 1;

        BrowserActivity activity;
        ArrayList<File> data;
        File currentFolder;
        boolean showParent = false;

        public FoldersAdapter(BrowserActivity activity) {
            this.activity = activity;
            this.data = new ArrayList<>();
            this.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    showParent = showParentFolder();
                }
            });
        }

        void setCurrentFolder(File folder) {
            this.currentFolder = folder;
        }

        void setData(ArrayList<File> data) {
            this.data.clear();
            if (data != null)
                this.data.addAll(data);

            this.notifyDataSetChanged();
        }

        @Override
        public FolderViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            switch (viewType) {
                case PARENT_FOLDER_VIEW_TYPE:
                    return new ParentFolderViewHolder(parent);
                case CHILD_FOLDER_VIEW_TYPE:
                    return new ChildFolderViewHolder(parent);
                default:
                    throw new RuntimeException();
            }
        }

        @Override
        public void onBindViewHolder(FolderViewHolder holder, int position) {
            File folder;
            if (holder instanceof ParentFolderViewHolder) {
                folder = this.currentFolder.getParentFile();
            } else {
                if (showParent) {
                    position -= 1;
                }
                folder = data.get(position);
            }
            holder.bind(activity, folder);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0 && showParent) {
                return PARENT_FOLDER_VIEW_TYPE;
            } else {
                return CHILD_FOLDER_VIEW_TYPE;
            }
        }

        public boolean showParentFolder() {
            return (currentFolder != null && !currentFolder.equals(Util.getSDCardDir())) && currentFolder.getAbsolutePath().startsWith(Util.getSDCardDir().getAbsolutePath());
        }

        @Override
        public int getItemCount() {
            int count = data.size();
            if (showParent) {
                count += 1;
            }
            return count;
        }
    }

    static abstract class AmiiboVewHolder extends RecyclerView.ViewHolder {
        TextView txtTagInfo;
        TextView txtName;
        TextView txtTagId;
        TextView txtAmiiboSeries;
        TextView txtAmiiboType;
        TextView txtGameSeries;
        TextView txtCharacter;
        TextView txtPath;
        ImageView imageAmiibo;
        AmiiboFile amiiboFile = null;
        BrowserActivity activity;

        SimpleTarget<Bitmap> target = new SimpleTarget<Bitmap>() {
            @Override
            public void onLoadStarted(@Nullable Drawable placeholder) {
                imageAmiibo.setVisibility(View.GONE);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                imageAmiibo.setVisibility(View.GONE);
            }

            @Override
            public void onResourceReady(Bitmap resource, Transition transition) {
                imageAmiibo.setImageBitmap(resource);
                imageAmiibo.setVisibility(View.VISIBLE);
            }
        };

        public AmiiboVewHolder(View itemView) {
            super(itemView);

            this.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent returnIntent = new Intent();
                    returnIntent.setData(Uri.fromFile(new File(amiiboFile.filePath)));

                    activity.setResult(Activity.RESULT_OK, returnIntent);
                    activity.finish();
                }
            });

            this.txtTagInfo = itemView.findViewById(R.id.txtTagInfo);
            this.txtName = itemView.findViewById(R.id.txtName);
            this.txtTagId = itemView.findViewById(R.id.txtTagId);
            this.txtAmiiboSeries = itemView.findViewById(R.id.txtAmiiboSeries);
            this.txtAmiiboType = itemView.findViewById(R.id.txtAmiiboType);
            this.txtGameSeries = itemView.findViewById(R.id.txtGameSeries);
            this.txtCharacter = itemView.findViewById(R.id.txtCharacter);
            this.txtPath = itemView.findViewById(R.id.txtPath);
            this.imageAmiibo = itemView.findViewById(R.id.imageAmiibo);
            if (this.imageAmiibo != null) {
                this.imageAmiibo.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Bundle bundle = new Bundle();
                        bundle.putLong(ImageActivity.INTENT_EXTRA_AMIIBO_ID, amiiboFile.id);

                        Intent intent = new Intent(activity, ImageActivity_.class);
                        intent.putExtras(bundle);

                        activity.startActivity(intent);
                    }
                });
            }
        }

        void bind(BrowserActivity activity, final AmiiboFile item) {
            this.activity = activity;
            this.amiiboFile = item;

            String tagInfo = "";
            String amiiboHexId = "";
            String amiiboName = "";
            String amiiboSeries = "";
            String amiiboType = "";
            String gameSeries = "";
            String character = "";
            final String amiiboImageUrl;

            long amiiboId = item.id;
            Amiibo amiibo = null;
            AmiiboManager amiiboManager = activity.amiiboManager;
            if (amiiboManager != null) {
                amiibo = amiiboManager.amiibos.get(amiiboId);
                if (amiibo == null)
                    amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
            }
            if (amiibo != null) {
                amiiboHexId = TagUtil.amiiboIdToHex(amiibo.id);
                amiiboImageUrl = amiibo.getImageUrl();
                if (amiibo.name != null) {
                    amiiboName = amiibo.name;
                }
                if (amiibo.getAmiiboSeries() != null) {
                    amiiboSeries = amiibo.getAmiiboSeries().name;
                }
                if (amiibo.getAmiiboType() != null) {
                    amiiboType = amiibo.getAmiiboType().name;
                }
                if (amiibo.getGameSeries() != null) {
                    gameSeries = amiibo.getGameSeries().name;
                }
                if (amiibo.getCharacter() != null) {
                    character = amiibo.getCharacter().name;
                }
            } else {
                amiiboImageUrl = null;
                tagInfo = "<Unknown amiibo id: " + TagUtil.amiiboIdToHex(amiiboId) + ">";
            }

            String query = activity.getQuery().toLowerCase();
            this.txtTagInfo.setText(tagInfo);
            setAmiiboInfoText(this.txtName, boldMatchingText(amiiboName, query), !tagInfo.isEmpty());
            setAmiiboInfoText(this.txtTagId, boldStartText(amiiboHexId, query), !tagInfo.isEmpty());
            setAmiiboInfoText(this.txtAmiiboSeries, boldMatchingText(amiiboSeries, query), !tagInfo.isEmpty());
            setAmiiboInfoText(this.txtAmiiboType, boldMatchingText(amiiboType, query), !tagInfo.isEmpty());
            setAmiiboInfoText(this.txtGameSeries, boldMatchingText(gameSeries, query), !tagInfo.isEmpty());
            setAmiiboInfoText(this.txtCharacter, boldMatchingText(character, query), !tagInfo.isEmpty());
            this.txtPath.setText(boldMatchingText(Util.friendlyPath(item.filePath), query));
            this.txtPath.setVisibility(View.VISIBLE);

            if (this.imageAmiibo != null) {
                this.imageAmiibo.setVisibility(View.GONE);
                Glide.with(activity).clear(target);
                if (amiiboImageUrl != null) {
                    Glide.with(activity)
                        .setDefaultRequestOptions(new RequestOptions().onlyRetrieveFromCache(onlyRetrieveFromCache(activity)))
                        .asBitmap()
                        .load(amiiboImageUrl)
                        .into(target);
                }
            }
        }

        boolean onlyRetrieveFromCache(BrowserActivity activity) {
            String imageNetworkSetting = activity.prefs.imageNetworkSetting().get();
            if (SettingsFragment.IMAGE_NETWORK_NEVER.equals(imageNetworkSetting)) {
                return true;
            } else if (SettingsFragment.IMAGE_NETWORK_WIFI.equals(imageNetworkSetting)) {
                ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);

                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork == null || activeNetwork.getType() != ConnectivityManager.TYPE_WIFI;
            } else {
                return false;
            }
        }

        SpannableStringBuilder boldMatchingText(String text, String query) {
            SpannableStringBuilder str = new SpannableStringBuilder(text);
            if (query.isEmpty())
                return str;

            text = text.toLowerCase();
            int j = 0;
            while (j < text.length()) {
                int i = text.indexOf(query, j);
                if (i == -1)
                    break;

                j = i + query.length();
                str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), i, j, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return str;
        }

        SpannableStringBuilder boldStartText(String text, String query) {
            SpannableStringBuilder str = new SpannableStringBuilder(text);
            if (!query.isEmpty() && text.toLowerCase().startsWith(query)) {
                str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return str;
        }

        void setAmiiboInfoText(TextView textView, CharSequence text, boolean hasTagInfo) {
            if (hasTagInfo) {
                textView.setText("");
            } else if (text.length() == 0) {
                textView.setText("Unknown");
                textView.setEnabled(false);
            } else {
                textView.setText(text);
                textView.setEnabled(true);
            }
        }
    }

    static class SimpleViewHolder extends AmiiboVewHolder {
        public SimpleViewHolder(ViewGroup parent) {
            super(LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.amiibo_simple_card, parent, false));
        }
    }

    static class CompactViewHolder extends AmiiboVewHolder {
        public CompactViewHolder(ViewGroup parent) {
            super(LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.amiibo_compact_card, parent, false));
        }
    }

    static class LargeViewHolder extends AmiiboVewHolder {
        public LargeViewHolder(ViewGroup parent) {
            super(LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.amiibo_large_card, parent, false));
        }
    }

    static class AmiibosAdapter extends RecyclerView.Adapter<AmiiboVewHolder> implements Filterable {
        private final BrowserActivity activity;
        private ArrayList<AmiiboFile> data;
        private ArrayList<AmiiboFile> filteredData;
        private AmiiboFilter filter;

        AmiibosAdapter(BrowserActivity activity) {
            this.activity = activity;
            this.data = new ArrayList<>();
            this.filteredData = this.data;
        }

        void setData(ArrayList<AmiiboFile> data) {
            this.data.clear();
            if (data != null)
                this.data.addAll(data);
            this.refresh();
        }

        @Override
        public int getItemCount() {
            return filteredData.size();
        }

        @Override
        public long getItemId(int i) {
            return filteredData.get(i).id;
        }

        public AmiiboFile getItem(int i) {
            return filteredData.get(i);
        }

        @Override
        public int getItemViewType(int position) {
            return activity.getView();
        }

        @Override
        public AmiiboVewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            switch (viewType) {
                case VIEW_TYPE_COMPACT:
                    return new CompactViewHolder(parent);
                case VIEW_TYPE_LARGE:
                    return new LargeViewHolder(parent);
                case VIEW_TYPE_SIMPLE:
                default:
                    return new SimpleViewHolder(parent);
            }
        }

        @Override
        public void onBindViewHolder(final AmiiboVewHolder holder, int position) {
            holder.bind(activity, getItem(position));
        }

        class CustomComparator implements Comparator<AmiiboFile> {
            @Override
            public int compare(AmiiboFile amiiboFile1, AmiiboFile amiiboFile2) {
                String filePath1 = amiiboFile1.filePath;
                String filePath2 = amiiboFile2.filePath;

                int sort = activity.getSort();
                if (sort == SORT_FILE_PATH)
                    return filePath1.compareTo(filePath2);

                int value = 0;
                long amiiboId1 = amiiboFile1.id;
                long amiiboId2 = amiiboFile2.id;
                if (sort == SORT_ID) {
                    value = compareAmiiboId(amiiboId1, amiiboId2);
                } else {
                    AmiiboManager amiiboManager = activity.amiiboManager;
                    if (amiiboManager != null) {
                        Amiibo amiibo1 = amiiboManager.amiibos.get(amiiboId1);
                        Amiibo amiibo2 = amiiboManager.amiibos.get(amiiboId2);
                        if (amiibo1 == null)
                            value = 1;
                        else if (amiibo2 == null)
                            value = -1;
                        else if (sort == SORT_NAME) {
                            value = compareAmiiboName(amiibo1, amiibo2);
                        } else if (sort == SORT_AMIIBO_SERIES) {
                            value = compareAmiiboSeries(amiibo1, amiibo2);
                        } else if (sort == SORT_AMIIBO_TYPE) {
                            value = compareAmiiboType(amiibo1, amiibo2);
                        } else if (sort == SORT_GAME_SERIES) {
                            value = compareGameSeries(amiibo1, amiibo2);
                        } else if (sort == SORT_CHARACTER) {
                            value = compareCharacter(amiibo1, amiibo2);
                        }
                        if (value == 0)
                            value = amiibo1.compareTo(amiibo2);
                    }
                    if (value == 0)
                        value = compareAmiiboId(amiiboId1, amiiboId2);
                }

                if (value == 0)
                    value = filePath1.compareTo(filePath2);

                return value;
            }

            int compareAmiiboId(long amiiboId1, long amiiboId2) {
                if (amiiboId1 == amiiboId2)
                    return 0;
                return amiiboId1 < amiiboId2 ? -1 : 1;
            }

            int compareAmiiboName(Amiibo amiibo1, Amiibo amiibo2) {
                String name1 = amiibo1.name;
                String name2 = amiibo2.name;
                if (name1 == null) {
                    return 1;
                } else if (name2 == null) {
                    return -1;
                }
                return name1.compareTo(name2);
            }

            int compareAmiiboSeries(Amiibo amiibo1, Amiibo amiibo2) {
                AmiiboSeries amiiboSeries1 = amiibo1.getAmiiboSeries();
                AmiiboSeries amiiboSeries2 = amiibo2.getAmiiboSeries();
                if (amiiboSeries1 == null) {
                    return 1;
                } else if (amiiboSeries2 == null) {
                    return -1;
                }
                return amiiboSeries1.compareTo(amiiboSeries2);
            }

            int compareAmiiboType(Amiibo amiibo1, Amiibo amiibo2) {
                AmiiboType amiiboType1 = amiibo1.getAmiiboType();
                AmiiboType amiiboType2 = amiibo2.getAmiiboType();
                if (amiiboType1 == null) {
                    return 1;
                } else if (amiiboType2 == null) {
                    return -1;
                }
                return amiiboType1.compareTo(amiiboType2);
            }

            int compareGameSeries(Amiibo amiibo1, Amiibo amiibo2) {
                GameSeries gameSeries1 = amiibo1.getGameSeries();
                GameSeries gameSeries2 = amiibo2.getGameSeries();
                if (gameSeries1 == null) {
                    return 1;
                } else if (gameSeries2 == null) {
                    return -1;
                }
                return gameSeries1.compareTo(gameSeries2);
            }

            int compareCharacter(Amiibo amiibo1, Amiibo amiibo2) {
                Character character1 = amiibo1.getCharacter();
                Character character2 = amiibo2.getCharacter();
                if (character1 == null) {
                    return 1;
                } else if (character2 == null) {
                    return -1;
                }
                return character1.compareTo(character2);
            }
        }

        public void refresh() {
            this.getFilter().filter(activity.getQuery());
        }

        @Override
        public AmiiboFilter getFilter() {
            if (this.filter == null) {
                this.filter = new AmiiboFilter();
            }

            return this.filter;
        }

        public class AmiiboFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String query = constraint != null ? constraint.toString() : "";
                activity.setQuery(query);

                FilterResults filterResults = new FilterResults();
                ArrayList<AmiiboFile> tempList = new ArrayList<>();
                String queryText = query.trim().toLowerCase();
                for (AmiiboFile amiiboFile : data) {
                    boolean add;

                    AmiiboManager amiiboManager = activity.amiiboManager;
                    if (amiiboManager != null) {
                        Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.id);
                        if (amiibo == null)
                            amiibo = new Amiibo(amiiboManager, amiiboFile.id, null, null);
                        add = amiiboContainsQuery(amiibo, queryText);
                    } else {
                        add = queryText.isEmpty();
                    }
                    if (!add)
                        add = pathContainsQuery(amiiboFile.filePath, queryText);
                    if (add)
                        tempList.add(amiiboFile);
                }
                filterResults.count = tempList.size();
                filterResults.values = tempList;

                return filterResults;
            }

            public boolean pathContainsQuery(String path, String query) {
                return !query.isEmpty() &&
                    activity.getGameSeriesFilter().isEmpty() &&
                    activity.getCharacterFilter().isEmpty() &&
                    activity.getAmiiboSeriesFilter().isEmpty() &&
                    activity.getAmiiboTypeFilter().isEmpty() &&
                    path.toLowerCase().contains(query);
            }

            public boolean amiiboContainsQuery(Amiibo amiibo, String query) {
                GameSeries gameSeries = amiibo.getGameSeries();
                if (!activity.matchesGameSeriesFilter(gameSeries))
                    return false;

                Character character = amiibo.getCharacter();
                if (!activity.matchesCharacterFilter(character))
                    return false;

                AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
                if (!activity.matchesAmiiboSeriesFilter(amiiboSeries))
                    return false;

                AmiiboType amiiboType = amiibo.getAmiiboType();
                if (!activity.matchesAmiiboTypeFilter(amiiboType))
                    return false;

                if (!query.isEmpty()) {
                    if (TagUtil.amiiboIdToHex(amiibo.id).toLowerCase().startsWith(query))
                        return true;
                    else if (amiibo.name != null && amiibo.name.toLowerCase().contains(query))
                        return true;
                    else if (gameSeries != null && gameSeries.name.toLowerCase().contains(query))
                        return true;
                    else if (character != null && character.name.toLowerCase().contains(query))
                        return true;
                    else if (amiiboSeries != null && amiiboSeries.name.toLowerCase().contains(query))
                        return true;
                    else if (amiiboType != null && amiiboType.name.toLowerCase().contains(query))
                        return true;

                    return false;
                }
                return true;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                Collections.sort((ArrayList<AmiiboFile>) filterResults.values, new CustomComparator());
                filteredData = (ArrayList<AmiiboFile>) filterResults.values;
                notifyDataSetChanged();
            }
        }

    }

    @UiThread
    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
