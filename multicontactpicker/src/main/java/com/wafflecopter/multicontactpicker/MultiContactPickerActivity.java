package com.wafflecopter.multicontactpicker;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.SystemBarStyle;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.l4digital.fastscroll.FastScrollRecyclerView;
import com.miguelcatalan.materialsearchview.MaterialSearchView;
import com.wafflecopter.multicontactpicker.RxContacts.Contact;
import com.wafflecopter.multicontactpicker.RxContacts.RxContacts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;

public class MultiContactPickerActivity extends AppCompatActivity implements MaterialSearchView.OnQueryTextListener {

    public static final String EXTRA_RESULT_SELECTION = "extra_result_selection";
    private FastScrollRecyclerView recyclerView;
    private List<Contact> contactList = new ArrayList<>();
    private TextView tvSelectAll;
    private TextView tvSelectBtn;
    private TextView tvNoContacts;
    private LinearLayout controlPanel;
    private MultiContactPickerAdapter adapter;
    private Toolbar toolbar;
    private MaterialSearchView searchView;
    private ProgressBar progressBar;
    private MenuItem searchMenuItem;
    private MultiContactPicker.Builder builder;
    private boolean allSelected = false;
    private CompositeDisposable disposables;
    private Integer animationCloseEnter, animationCloseExit;

    private int toolbarPaddingRight = -1;
    private int toolbarPaddingTop = -1;
    private int lastInsetTop = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null) return;

        builder = (MultiContactPicker.Builder) intent.getSerializableExtra("builder");

        disposables = new CompositeDisposable();

        setTheme(builder.theme);

        setContentView(R.layout.activity_multi_contact_picker);

        setupActionBar();

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        searchView = (MaterialSearchView) findViewById(R.id.search_view);
        controlPanel = (LinearLayout) findViewById(R.id.controlPanel);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        tvSelectAll = (TextView) findViewById(R.id.tvSelectAll);
        tvSelectBtn = (TextView) findViewById(R.id.tvSelect);
        tvNoContacts = (TextView) findViewById(R.id.tvNoContacts);
        recyclerView = (FastScrollRecyclerView) findViewById(R.id.recyclerView);

        initialiseUI(builder);

        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            int nightModeFlags =
                    getResources().getConfiguration().uiMode &
                            Configuration.UI_MODE_NIGHT_MASK;
            switch (nightModeFlags) {
                case Configuration.UI_MODE_NIGHT_YES:
                    break;
                case Configuration.UI_MODE_NIGHT_NO:
                case Configuration.UI_MODE_NIGHT_UNDEFINED:
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                    break;
            }
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
            if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                // windowBackground is a color
                getWindow().setNavigationBarColor(typedValue.data);
            }
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        tvSelectAll.setText(getString(R.string.tv_select_all_btn_text));


        adapter = new MultiContactPickerAdapter(contactList, new MultiContactPickerAdapter.ContactSelectListener() {
            @Override
            public void onContactSelected(Contact contact, int totalSelectedContacts) {
                updateSelectBarContents(totalSelectedContacts);
                if(builder.selectionMode == MultiContactPicker.CHOICE_MODE_SINGLE){
                    finishPicking();
                }
            }
        });

        loadContacts();

        recyclerView.setAdapter(adapter);

        tvSelectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishPicking();
            }
        });

        tvSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                allSelected = !allSelected;
                if(adapter != null)
                    adapter.setAllSelected(allSelected);
                if(allSelected)
                    tvSelectAll.setText(getString(R.string.tv_unselect_all_btn_text));
                else
                    tvSelectAll.setText(getString(R.string.tv_select_all_btn_text));
            }
        });

    }

    private void finishPicking(){
        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT_SELECTION, MultiContactPicker.buildResult(adapter.getSelectedContacts()));
        setResult(RESULT_OK, result);
        finish();
        overrideAnimation();
    }

    private void overrideAnimation(){
        if(animationCloseEnter != null && animationCloseExit != null){
            overridePendingTransition(animationCloseEnter, animationCloseExit);
        }
    }

    private void updateSelectBarContents(int totalSelectedContacts){
        tvSelectBtn.setEnabled(totalSelectedContacts > 0);
        if(totalSelectedContacts > 0) {
            tvSelectBtn.setText(getString(R.string.tv_select_btn_text_enabled, String.valueOf(totalSelectedContacts)));
        } else {
            tvSelectBtn.setText(getString(R.string.tv_select_btn_text_disabled));
        }
    }

    private void initialiseUI(MultiContactPicker.Builder builder){
        //setSupportActionBar(toolbar);
        searchView.setOnQueryTextListener(this);

        this.animationCloseEnter = builder.animationCloseEnter;
        this.animationCloseExit = builder.animationCloseExit;

        if(builder.bubbleColor != 0)
            recyclerView.setBubbleColor(builder.bubbleColor);
        if(builder.handleColor != 0)
            recyclerView.setHandleColor(builder.handleColor);
        if(builder.bubbleTextColor != 0)
            recyclerView.setBubbleTextColor(builder.bubbleTextColor);
        if(builder.trackColor != 0)
            recyclerView.setTrackColor(builder.trackColor);
        recyclerView.setHideScrollbar(builder.hideScrollbar);
        recyclerView.setTrackVisible(builder.showTrack);
        if(builder.selectionMode == MultiContactPicker.CHOICE_MODE_SINGLE){
            controlPanel.setVisibility(View.GONE);
        }else{
            controlPanel.setVisibility(View.VISIBLE);
        }

        if(builder.selectionMode == MultiContactPicker.CHOICE_MODE_SINGLE && builder.selectedItems.size() > 0){
            throw new RuntimeException("You must be using MultiContactPicker.CHOICE_MODE_MULTIPLE in order to use setSelectedContacts()");
        }
        
        if (builder.titleText != null) {
            setTitle(builder.titleText);
        }

    }



    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Handle window insets for the status bar
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (view, insets) -> {
            // Extract only the system bar insets
            int insetTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int insetRight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right;
            int insetLeft = insets.getInsets(WindowInsetsCompat.Type.systemBars()).left;
            //int insetBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            if(toolbarPaddingRight == -1) {
                toolbarPaddingRight = toolbar.getPaddingRight();
            }
            if(toolbarPaddingTop == -1) {
                toolbarPaddingTop = toolbar.getPaddingTop();
            }

            // Apply the top padding to the Toolbar
            toolbar.setPadding(
                    toolbarPaddingRight + insetLeft,
                    toolbarPaddingTop + insetTop,
                    toolbarPaddingRight + insetRight,
                    toolbar.getPaddingBottom()
            );

            ViewGroup.LayoutParams layoutParams = toolbar.getLayoutParams();
            layoutParams.height = layoutParams.height - lastInsetTop + insetTop; // Increase the height
            toolbar.setLayoutParams(layoutParams);
            lastInsetTop = insetTop;

            MaterialSearchView search_view = findViewById(R.id.search_view);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) search_view.getLayoutParams();
            params.setMargins(
                    insetLeft,  // Left margin
                    insetTop, // Preserve existing top margin
                    insetRight, // Right margin
                    params.bottomMargin // Bottom margin
            );

            // Return the original WindowInsetsCompat object
            return insets;
        });
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                setResult(RESULT_CANCELED);
                finish();
                overrideAnimation();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadContacts(){
        tvSelectAll.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        RxContacts.fetch(builder.columnLimit, this)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        disposables.add(disposable);
                    }
                })
                .filter(new Predicate<Contact>() {
                    @Override
                    public boolean test(Contact contact) throws Exception {
                        return contact.getDisplayName() != null;
                    }
                })
                .subscribe(new Observer<Contact>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }
                    @Override
                    public void onNext(Contact value) {
                        contactList.add(value);
                        if(builder.selectedItems.contains(value.getId())){
                            adapter.setContactSelected(value.getId());
                        }
                        Collections.sort(contactList, new Comparator<Contact>() {
                            @Override
                            public int compare(Contact contact, Contact t1) {
                                return contact.getDisplayName().compareToIgnoreCase(t1.getDisplayName());
                            }
                        });
                        if(builder.loadingMode == MultiContactPicker.LOAD_ASYNC) {
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                            progressBar.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        progressBar.setVisibility(View.GONE);
                        e.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        if (contactList.size() == 0) { tvNoContacts.setVisibility(View.VISIBLE); }
                        if (adapter != null && builder.loadingMode == MultiContactPicker.LOAD_SYNC) {
                            adapter.notifyDataSetChanged();
                        }
                        if(adapter != null) {
                            updateSelectBarContents(adapter.getSelectedContactsCount());
                        }
                        progressBar.setVisibility(View.GONE);
                        tvSelectAll.setEnabled(true);
                    }
                });



    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mcp_menu_main, menu);
        searchMenuItem = menu.findItem(R.id.mcp_action_search);
        setSearchIconColor(searchMenuItem, builder.searchIconColor);
        searchView.setMenuItem(searchMenuItem);
        return true;
    }

    private void setSearchIconColor(MenuItem menuItem, final Integer color) {
        if(color != null) {
            Drawable drawable = menuItem.getIcon();
            if (drawable != null) {
                drawable = DrawableCompat.wrap(drawable);
                DrawableCompat.setTint(drawable.mutate(), color);
                menuItem.setIcon(drawable);
            }
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if(adapter != null){
            adapter.filterOnText(query);
        }
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if(adapter != null){
            adapter.filterOnText(newText);
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (searchView.isSearchOpen()) {
            searchView.closeSearch();
        } else {
            super.onBackPressed();
            overrideAnimation();
        }
    }

    @Override
    public void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }
}
