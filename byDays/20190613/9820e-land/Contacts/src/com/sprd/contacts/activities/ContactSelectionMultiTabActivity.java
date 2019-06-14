
package com.sprd.contacts.activities;

import java.util.HashMap;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Intents.UI;
import android.provider.ContactsContract.Contacts;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.contacts.ContactsActivity;
import com.sprd.contacts.group.GroupBrowseListFragmentSprd;
import com.android.contacts.group.GroupBrowseListFragment.OnGroupBrowserActionListener;
import com.sprd.contacts.list.AllInOneBrowserPickerFragment;
import com.sprd.contacts.list.AllInOneCallLogPickerFragment;
import com.sprd.contacts.list.AllInOneDataPickerFragment;
import com.sprd.contacts.list.AllInOneFavoritesPickerFragment;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.ContactListFilterController;
import com.android.contacts.list.ContactTileListFragment;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.sprd.contacts.list.OnAllInOneDataMultiPickerActionListener;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.contacts.R;
import com.android.contacts.common.util.AccountFilterUtil;
import com.sprd.contacts.common.util.UniverseThemeUtils;
import com.sprd.contacts.common.util.UniverseUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.sprd.android.support.featurebar.FeatureBarHelper;
import android.app.ActionBar.OnMenuVisibilityListener;
import android.widget.TextView;
import android.view.KeyEvent;
import com.android.contacts.common.list.ContactListItemView;
import android.widget.ListView;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.EditText;
import android.view.View;
import android.text.TextUtils;
import android.widget.CheckBox;

public class ContactSelectionMultiTabActivity extends ContactsActivity {
    private static final String TAG = "MultiTabContactSelectionActivity";

    private static final int TAB_INDEX_GROUP_NEWUI = 2;
    private static final int TAB_INDEX_FAVORITES_NEWUI = 1;
    private static final int TAB_INDEX_ALL_NEWUI = 0;
    private static final int TAB_INDEX_CALLLOG_NEWUI = 3;

    private static final int TAB_INDEX_COUNT_NEWUI = 1;

    private static final int REQUEST_CODE_PICK = 1;

    private static final String KEY_TAB = "tab_position";

    private ViewPager mViewPager;
    private PageChangeListener mPageChangeListener = new PageChangeListener();

    private GroupBrowseListFragmentSprd mGroupBrowseListFragment;
    private AllInOneFavoritesPickerFragment mFavoriteFragment;
    private AllInOneBrowserPickerFragment mAllInOneDataPickerFragment;
    private AllInOneCallLogPickerFragment mCallLogFragment;

    private ContactsIntentResolver mIntentResolver;
    private ContactsRequest mRequest;
    private ContactListFilterController mContactListFilterController;
    private ContactListFilter mFilter = null;

    private boolean mMultiSupport = false;
    private boolean mIsFirstEnter = true;
    private boolean mIsDoneMenuEnable = false;

    private Button mDoneMenuItem;
    private boolean mDoneEnable = false;
    private int mDoneMenuDisableColor = Color.WHITE;
    private int mCurrentTabPosition = -1;
    private BroadcastReceiver mSelecStatusReceiver;
    protected ContactEntryListFragment<?> mListFragment;
    private FeatureBarHelper mFeatureBarHelper;
    public TextView  mCenterSkView;
    private TextView mLeftSkView;
    private TextView mRightSkView;
    private EditText searchView;

    public ContactSelectionMultiTabActivity() {
        mIntentResolver = new ContactsIntentResolver(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mCurrentTabPosition = savedInstanceState.getInt(KEY_TAB);
            mIsFirstEnter = false;
        }

        // Extract relevant information from the intent
        mRequest = mIntentResolver.resolveIntent(getIntent());

        mContactListFilterController = ContactListFilterController.getInstance(this);
        mContactListFilterController.checkFilterValidity(false);
        mFilter = mContactListFilterController.getFilter();

        configureActivityTitle();

        setContentView(R.layout.contact_select_multi_new_ui);
        mFeatureBarHelper = new FeatureBarHelper(this);
        mLeftSkView = (TextView)mFeatureBarHelper.getOptionsKeyView() ;
        mCenterSkView =(TextView)mFeatureBarHelper.getCenterKeyView();
        if (mCenterSkView != null) {
            mCenterSkView.setText(R.string.default_feature_bar_center);
        }
        mRightSkView = (TextView)mFeatureBarHelper.getBackKeyView();

        findViewById(R.id.contact_select_multi_tab).addOnLayoutChangeListener(
                mOnLayoutChangeListener);

        prepareActionBar();

        mViewPager = (ViewPager) findViewById(R.id.pager);
        if (mViewPager != null) {
            mViewPager.setAdapter(new MultiTabViewPagerAdapter(getFragmentManager()));
           // mViewPager.setOnPageChangeListener(mPageChangeListener);
        }

        //setupGroup();
        //setupFavorites();
        setupAllContacts();
        //setupCallLog();
        if (mViewPager != null && mCurrentTabPosition != -1) {
            mViewPager.setCurrentItem(mCurrentTabPosition);
        }
        getActionBar().addOnMenuVisibilityListener(new OnMenuVisibilityListener() {
            @Override
            public void onMenuVisibilityChanged(boolean isVisible) {
                if (isVisible) {
                    mLeftSkView.setText("");
                    mCenterSkView.setText(R.string.default_feature_bar_center);
                    mRightSkView.setText(R.string.softkey_cancel);
                } else {
                    /**SPRD Bug643037 monky test, contacts occur the crash
                     * SPRD Bug652189 Sofkey display incorrect
                     * {@
                     * */
                    try {
                        View view = getCurrentFocus();
                        if (view instanceof ListView) {
                            ContactListItemView mListed = (ContactListItemView)((ListView)view).getSelectedView();
                            if (mListed != null && mListed.getNameTextView()!= null){
                                mCenterSkView.setText(R.string.default_feature_bar_center);
                            } else {
                                mCenterSkView.setText("");
                            }
                            mRightSkView.setText(R.string.softkey_back);
                        } else if (view instanceof CheckBox) {
                            mCenterSkView.setText(R.string.default_feature_bar_center);
                            mRightSkView.setText(R.string.softkey_back);
                        } else if (view instanceof EditText) {
                            if (searchView == null) {
                                searchView = (EditText) view;
                                searchView.addTextChangedListener(mDigitsTextListener);
                            }
                            mCenterSkView.setText("");
                            if (searchView != null && !TextUtils.isEmpty(searchView.getText())){
                                int postion = searchView.getSelectionStart();
                                if (postion > 0) {
                                    mRightSkView.setText(R.string.softkey_clear);
                                } else {
                                    mRightSkView.setText(R.string.softkey_back);
                                }
                            }else{
                                mRightSkView.setText(R.string.softkey_back);
                            }
                        } else {
                            mRightSkView.setText(R.string.softkey_back);
                            mCenterSkView.setText("");
                        }
                    } catch (Exception e) {
                        Log.d(TAG, e.toString());
                        mCenterSkView.setText("");
                        mRightSkView.setText(R.string.softkey_back);
                    }
                    /**
                     * @}
                     */
                    mLeftSkView.setText(R.string.softkey_option);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.selection_multi, menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAB, mViewPager != null ? mViewPager.getCurrentItem() : -1);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mViewPager != null && mIsFirstEnter) {
            mViewPager.setCurrentItem(TAB_INDEX_ALL_NEWUI);
            mIsFirstEnter = false;
        }
    }

    public class SSUReceiver extends BroadcastReceiver{

        public void onReceive(final Context context, final Intent intent) {
            int currentTab = mViewPager != null ? mViewPager.getCurrentItem() : -1;
            if (currentTab != -1) {
                Fragment currentFragment = getFragmentAt(currentTab);
                boolean doneEnable = false;
                if (currentFragment != null) {
                    if (currentFragment instanceof GroupBrowseListFragmentSprd) {
                    } else if (currentFragment instanceof AllInOneFavoritesPickerFragment) {
                        ContactEntryListAdapter contactEntryListAdapter = (ContactEntryListAdapter) (((AllInOneFavoritesPickerFragment) currentFragment)
                                .getAdapter());
                        doneEnable = contactEntryListAdapter.hasCheckedItems();
                    } else if (currentFragment instanceof AllInOneCallLogPickerFragment) {
                        ContactEntryListAdapter contactEntryListAdapter = (ContactEntryListAdapter) (((AllInOneCallLogPickerFragment) currentFragment)
                                .getAdapter());
                        doneEnable = contactEntryListAdapter.hasCheckedItems();
                    } else if (currentFragment instanceof AllInOneBrowserPickerFragment) {
                        ContactEntryListAdapter contactEntryListAdapter = (ContactEntryListAdapter) (((AllInOneBrowserPickerFragment) currentFragment)
                                .getAdapter());
                        doneEnable = contactEntryListAdapter.hasCheckedItems();
                    }
                    /**SPRD Bug637119 The doneMenu should be gray if no contacts were selected
                     * {@
                     * */
                    mIsDoneMenuEnable = doneEnable;
                    setDoneMenu(doneEnable);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("com.android.contacts.common.action.SSU");
        mSelecStatusReceiver = new SSUReceiver();
        registerReceiver(mSelecStatusReceiver, filter);
        View view = getCurrentFocus();
        /**SPRD Bug652189 Sofkey display incorrect {@*/
        if (view instanceof EditText ) {
            if(mCenterSkView != null){
                mCenterSkView.setText("");
            }
            if (searchView != null && !TextUtils.isEmpty(searchView.getText())) {
                int postion = searchView.getSelectionStart();
                if (postion > 0) {
                    mRightSkView.setText(R.string.softkey_clear);
                } else {
                    mRightSkView.setText(R.string.softkey_back);
                }
            } else {
                mRightSkView.setText(R.string.softkey_back);
            }
        }
        /**@}*/
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mSelecStatusReceiver);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof GroupBrowseListFragmentSprd) {
            if (mGroupBrowseListFragment == null) {
                mGroupBrowseListFragment = (GroupBrowseListFragmentSprd) fragment;
                setupActionListener(TAB_INDEX_GROUP_NEWUI);
            }
        } else if (fragment instanceof AllInOneFavoritesPickerFragment) {
            if (mFavoriteFragment == null) {
                mFavoriteFragment = (AllInOneFavoritesPickerFragment) fragment;
                setupActionListener(TAB_INDEX_FAVORITES_NEWUI);
            }
        } else if (fragment instanceof AllInOneCallLogPickerFragment) {
            if (mCallLogFragment == null) {
                mCallLogFragment = (AllInOneCallLogPickerFragment) fragment;
                setupActionListener(TAB_INDEX_CALLLOG_NEWUI);
            }
        } else if (fragment instanceof AllInOneBrowserPickerFragment) {
            if (mAllInOneDataPickerFragment == null) {
                mAllInOneDataPickerFragment = (AllInOneBrowserPickerFragment) fragment;
                setupActionListener(TAB_INDEX_ALL_NEWUI);
            }
        }
    }
    /**SPRD Bug637119 The doneMenu should be gray if no contacts were selected
     * {@
     * */
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final MenuItem doneMenu = menu.findItem(R.id.save_menu_item);
        if(!mIsDoneMenuEnable){
                 doneMenu.setEnabled(false);
             }else{
                 doneMenu.setEnabled(true);
                 doneMenu.setVisible(true);
             }
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Go back to previous screen, intending "cancel"
                setResult(RESULT_CANCELED);
                finish();
                return true;
            case R.id.cancel_menu_item:
                onBackPressed();
                break;
            case R.id.save_menu_item:
                configCompleteListener(mViewPager != null ? mViewPager.getCurrentItem() : -1);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public class MultiTabViewPagerAdapter extends FragmentPagerAdapter {
        public MultiTabViewPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case TAB_INDEX_GROUP_NEWUI:
                    mGroupBrowseListFragment = new GroupBrowseListFragmentSprd();
                    configTabAdapter(TAB_INDEX_GROUP_NEWUI);
                    return mGroupBrowseListFragment;
                case TAB_INDEX_FAVORITES_NEWUI:
                    mFavoriteFragment = new AllInOneFavoritesPickerFragment();
                    configTabAdapter(TAB_INDEX_FAVORITES_NEWUI);
                    return mFavoriteFragment;
                case TAB_INDEX_ALL_NEWUI:
                    mAllInOneDataPickerFragment = new AllInOneBrowserPickerFragment();
                    configTabAdapter(TAB_INDEX_ALL_NEWUI);
                    return mAllInOneDataPickerFragment;
                case TAB_INDEX_CALLLOG_NEWUI:
                    mCallLogFragment = new AllInOneCallLogPickerFragment();
                    configTabAdapter(TAB_INDEX_CALLLOG_NEWUI);
                    return mCallLogFragment;
            }
            throw new IllegalStateException("No fragment at position "
                    + position);
        }

        @Override
        public int getCount() {
            return TAB_INDEX_COUNT_NEWUI;
        }
    }

    private class PageChangeListener implements OnPageChangeListener {
        private int mCurrentPosition = -1;
        private int mNextPosition = -1;

        @Override
        public void onPageScrolled(int positon, float positionOffset,
                int positionoffsetPixels) {

        }

        @Override
        public void onPageSelected(int position) {
            final ActionBar actionBar = getActionBar();
            if (mCurrentPosition == position) {
                Log.w(TAG, "Previous position and next position became same ("
                        + position + ")");
            }

            actionBar.selectTab(actionBar.getTabAt(position));
            clearData(mNextPosition);
            mNextPosition = position;
        }

        public void setCurrentPosition(int position) {
            mCurrentPosition = position;
        }

        @Override
        public void onPageScrollStateChanged(int status) {
            switch (status) {
                case ViewPager.SCROLL_STATE_IDLE:
                    if (mCurrentPosition >= 0) {
                        sendFragmentVisibilityChange(mCurrentPosition, false);
                    }
                    if (mNextPosition >= 0) {
                        sendFragmentVisibilityChange(mNextPosition, true);
                    }
                    invalidateOptionsMenu();

                    mCurrentPosition = mNextPosition;
                    break;
                case ViewPager.SCROLL_STATE_DRAGGING:
                case ViewPager.SCROLL_STATE_SETTLING:
                default:
                    break;
            }

        }
    }

    private void sendFragmentVisibilityChange(int position, boolean visibility) {
        if (position >= 0) {
            final Fragment fragment = getFragmentAt(position);
            if (fragment != null) {
                fragment.setMenuVisibility(visibility);
                fragment.setUserVisibleHint(visibility);
            }
        }
    }

    private Fragment getFragmentAt(int position) {
        switch (position) {
            case TAB_INDEX_GROUP_NEWUI:
                return mGroupBrowseListFragment;
            case TAB_INDEX_FAVORITES_NEWUI:
                return mFavoriteFragment;
            case TAB_INDEX_ALL_NEWUI:
                return mAllInOneDataPickerFragment;
            case TAB_INDEX_CALLLOG_NEWUI:
                return mCallLogFragment;
            default:
                throw new IllegalStateException("Unknown fragment index: " + position);
        }
    }

    private void configureActivityTitle() {
        setTitle(R.string.contactPickerActivityTitle);
    }

    private final TextWatcher mDigitsTextListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (searchView != null && !TextUtils.isEmpty(searchView.getText())){
                mRightSkView.setText(R.string.softkey_clear);
            } else {
                mRightSkView.setText(R.string.softkey_back);
            }
        }
        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode=event.getKeyCode();
        try {
            View view = getCurrentFocus();
            if (view instanceof ListView) {
                ContactListItemView mListed = (ContactListItemView)((ListView)view).getSelectedView();
                if (mListed != null && mListed.getNameTextView()!= null){
                    mCenterSkView.setText(R.string.default_feature_bar_center);
                } else {
                    mCenterSkView.setText("");
                }
                mRightSkView.setText(R.string.softkey_back);
            } else if (view instanceof EditText) {
                if (searchView == null) {
                    searchView = (EditText) view;
                    searchView.addTextChangedListener(mDigitsTextListener);
                }
                if (searchView != null && !TextUtils.isEmpty(searchView.getText())) {
                    int postion = searchView.getSelectionStart();
                    if (postion > 0) {
                        mRightSkView.setText(R.string.softkey_clear);
                    } else {
                        mRightSkView.setText(R.string.softkey_back);
                    }
                } else {
                    mRightSkView.setText(R.string.softkey_back);
                }
                mCenterSkView.setText("");
            } else if (view instanceof CheckBox) {
                mCenterSkView.setText(R.string.default_feature_bar_center);
                mRightSkView.setText(R.string.softkey_back);
            } else {
                mCenterSkView.setText("");
                mRightSkView.setText(R.string.softkey_back);
            }
        } catch (Exception e) {
            Log.d(TAG, "type is wrong");
            mCenterSkView.setText("");
        }
        return super.dispatchKeyEvent(event);
    }

    private void prepareActionBar() {
        final ActionBar actionBar = getActionBar();
        if (actionBar != null && UniverseUtils.UNIVERSEUI_SUPPORT) {
            int tabHeight = (int) getResources().getDimensionPixelSize(
                    R.dimen.universe_ui_tab_height);
            actionBar.setAlternativeTabStyle(true);
            actionBar.setTabHeight(tabHeight);

            View customActionBarView = null;

            actionBar.setDisplayOptions( ActionBar.DISPLAY_SHOW_TITLE );
        }
    }

    private final View.OnLayoutChangeListener mOnLayoutChangeListener = new
            View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right,
                        int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    v.removeOnLayoutChangeListener(this);
                }
            };

    private TabListener mTabListener = new TabListener() {

        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }

        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            if (mViewPager.getCurrentItem() != tab.getPosition()) {
                mViewPager.setCurrentItem(tab.getPosition(), true);
            }
        }

        @Override
        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        }
    };

    private void setupGroup() {
        final Tab tab = getActionBar().newTab();
        if (UniverseUtils.UNIVERSEUI_SUPPORT) {
            if (UniverseThemeUtils.isUsingAlternativeTabStyle(this)) {
                LayoutInflater inflater = getLayoutInflater();
                View view = inflater.inflate(R.layout.contact_select_multi_tab_new_ui, null);
                ImageView imageView = (ImageView) view.findViewById(R.id.tab_icon);
                if (imageView != null) {
                    imageView.setImageResource(R.drawable.ic_tab_groups_newui);
                }
                TextView textView = (TextView) view.findViewById(R.id.tab_text);
                if (textView != null) {
                    textView.setText(R.string.contactsGroupsLabel);
                }
                tab.setCustomView(view);
            }
        }
        tab.setTabListener(mTabListener);
        getActionBar().addTab(tab);
    }

    private void setupFavorites() {
        final Tab tab = getActionBar().newTab();
        if (UniverseUtils.UNIVERSEUI_SUPPORT) {
            if (UniverseThemeUtils.isUsingAlternativeTabStyle(this)) {
                LayoutInflater inflater = getLayoutInflater();
                View view = inflater.inflate(R.layout.contact_select_multi_tab_new_ui, null);
                ImageView imageView = (ImageView) view.findViewById(R.id.tab_icon);
                if (imageView != null) {
                    imageView.setImageResource(R.drawable.ic_tab_starred_newui);
                }
                TextView textView = (TextView) view.findViewById(R.id.tab_text);
                if (textView != null) {
                    textView.setText(R.string.contactsFavoritesLabel);
                }
                tab.setCustomView(view);
            }
        }
        tab.setTabListener(mTabListener);
        getActionBar().addTab(tab);
    }

    private void setupAllContacts() {
        final Tab tab = getActionBar().newTab();
        if (UniverseThemeUtils.isUsingAlternativeTabStyle(this)) {
            LayoutInflater inflater = getLayoutInflater();
            View view = inflater.inflate(R.layout.contact_select_multi_tab_new_ui, null);
            ImageView imageView = (ImageView) view.findViewById(R.id.tab_icon);
            if (imageView != null) {
                imageView.setImageResource(R.drawable.ic_tab_all_newui);
            }
            TextView textView = (TextView) view.findViewById(R.id.tab_text);
            if (textView != null) {
                textView.setText(R.string.people);
            }
            tab.setCustomView(view);
        }
        tab.setTabListener(mTabListener);
        getActionBar().addTab(tab);
    }

    private void setupCallLog() {
        final Tab tab = getActionBar().newTab();
        if (UniverseUtils.UNIVERSEUI_SUPPORT) {
            if (UniverseThemeUtils.isUsingAlternativeTabStyle(this)) {
                LayoutInflater inflater = getLayoutInflater();
                View view = inflater.inflate(R.layout.contact_select_multi_tab_new_ui, null);
                ImageView imageView = (ImageView) view.findViewById(R.id.tab_icon);
                if (imageView != null) {
                    imageView.setImageResource(R.drawable.ic_tab_recent_newui);
                }
                TextView dialText = (TextView) view.findViewById(R.id.tab_text);
                if (dialText != null) {
                    dialText.setText(R.string.recentCallsIconLabel);
                }
                tab.setCustomView(view);
            }
        }
        tab.setTabListener(mTabListener);
        getActionBar().addTab(tab);
    }

    private final class GroupBrowserActionListener implements OnGroupBrowserActionListener {

        @Override
        public void onViewGroupAction(Uri groupUri) {
            Intent intent = new Intent(UI.MULTI_PICK_ACTION).
                    putExtra(
                            "cascading",
                            new Intent(UI.MULTI_PICK_ACTION).setType(Phone.CONTENT_ITEM_TYPE).
                                    putExtra(
                                            "cascading",
                                            new Intent(UI.MULTI_PICK_ACTION)
                                                    .setType(Email.CONTENT_ITEM_TYPE)));
            intent.putExtra("no_display_option", true);
            intent.putExtra("select_group_member", groupUri != null ? ContentUris.parseId(groupUri)
                    : -1);
            startActivityForResult(intent, REQUEST_CODE_PICK);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK) {
            if (resultCode == Activity.RESULT_OK) {
                setResult(RESULT_OK, data);
                finish();
            } else if (resultCode == Activity.RESULT_CANCELED) {

            }
        }
    }

    private final class AllInOneDataMultiPickerActionListener implements
            OnAllInOneDataMultiPickerActionListener {
        public void onPickAllInOneDataAction(HashMap<String, String> pairs) {
            returnPickerResult(pairs);
        }

        public void onCancel() {
            ContactSelectionMultiTabActivity.this.onBackPressed();
        }
    }

    public void returnPickerResult(HashMap<String, String> data) {
        Intent intent = new Intent();
        if (data.isEmpty()) {
            returnPickerResult();
        } else {
            intent.putExtra("result", data);
            returnPickerResult(intent);
        }
    }

    public void returnPickerResult() {
        setResult(RESULT_CANCELED);
        finish();
    }

    public void returnPickerResult(Intent intent) {
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void configTabAdapter(int position) {
        if (position < 0) {
            return;
        }
        switch (position) {
            case TAB_INDEX_GROUP_NEWUI:
                mGroupBrowseListFragment.setContextMenuEnable(false);
                break;
            case TAB_INDEX_FAVORITES_NEWUI:
                mFavoriteFragment.setMultiPickerSupported(true);
                mFavoriteFragment.setCascadingData(mRequest.getCascadingData());
                mFavoriteFragment.setSelection("star");
                mFavoriteFragment.setSelectTextVisible(false);
                mFavoriteFragment.setFilter(ContactListFilter
                        .createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));
                break;
            case TAB_INDEX_ALL_NEWUI:
                mAllInOneDataPickerFragment.setMultiPickerSupported(true);
                mAllInOneDataPickerFragment.setCascadingData(mRequest
                        .getCascadingData());
                mAllInOneDataPickerFragment.setSelectTextVisible(false);
                mAllInOneDataPickerFragment.setFilter(ContactListFilter
                        .createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));
                break;
            case TAB_INDEX_CALLLOG_NEWUI:
                mCallLogFragment.setMultiPickerSupported(true);
                mCallLogFragment.setCascadingData(mRequest
                        .getCascadingData());
                mCallLogFragment.setFilter(ContactListFilter
                        .createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));
                mCallLogFragment.setSearchVisible(false);
                mCallLogFragment.setFirstDividerVisible(true);
                mCallLogFragment.setSelectTextVisible(false);
                break;
            default:
                break;
        }
        setupActionListener(position);
    }

    private void setupActionListener(int position) {

        switch (position) {
            case TAB_INDEX_GROUP_NEWUI:
                mGroupBrowseListFragment.setListener(new GroupBrowserActionListener());
                break;
            case TAB_INDEX_FAVORITES_NEWUI:
                mFavoriteFragment.setOnAllInOneDataMultiPickerActionListener(
                        new AllInOneDataMultiPickerActionListener());
                break;
            case TAB_INDEX_ALL_NEWUI:
                mAllInOneDataPickerFragment.setOnAllInOneDataMultiPickerActionListener(
                        new AllInOneDataMultiPickerActionListener());
                break;
            case TAB_INDEX_CALLLOG_NEWUI:
                mCallLogFragment.setOnAllInOneDataMultiPickerActionListener(
                        new AllInOneDataMultiPickerActionListener());
                break;

            default:
                break;
        }
    }

    private void configCompleteListener(int position) {
        switch (position) {
            case TAB_INDEX_GROUP_NEWUI:
                returnPickerResult();
                break;
            case TAB_INDEX_FAVORITES_NEWUI:
                if (mFavoriteFragment != null) {
                    mFavoriteFragment.onMultiPickerSelected();
                }
                break;
            case TAB_INDEX_ALL_NEWUI:
                if (mAllInOneDataPickerFragment != null) {
                    mAllInOneDataPickerFragment.onMultiPickerSelected();
                }
                break;
            case TAB_INDEX_CALLLOG_NEWUI:
                if (mCallLogFragment != null) {
                    mCallLogFragment.onMultiPickerSelected();
                }
                break;
            default:
                break;
        }
    }

    private void clearData(int position) {
        switch (position) {
            case TAB_INDEX_GROUP_NEWUI:
                setDoneMenu(false);
                break;
            case TAB_INDEX_FAVORITES_NEWUI:
                if (mFavoriteFragment != null) {
                    mFavoriteFragment.clearCheckedItem();
                }
                break;
            case TAB_INDEX_ALL_NEWUI:
                if (mAllInOneDataPickerFragment != null) {
                    mAllInOneDataPickerFragment.clearCheckedItem();
                }
                break;
            case TAB_INDEX_CALLLOG_NEWUI:
                if (mCallLogFragment != null) {
                    mCallLogFragment.clearCheckedItem();
                }
                break;
            default:
                break;
        }
        setDoneMenu(false);
    }

    public void setDoneMenu(boolean enabled) {
    }
/*** BUG #46829 wanglei 20190418 add begin ***/
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		boolean result = false;
		int currentTab = mViewPager != null ? mViewPager.getCurrentItem() : -1;
		if (currentTab != -1) {
			Fragment currentFragment = getFragmentAt(currentTab);
			if (currentFragment instanceof ContactEntryListFragment) {
				result = ((ContactEntryListFragment) currentFragment).onKeyDown(keyCode, event);
			}
		}

		if (!result) {
			result = super.onKeyDown(keyCode, event);
		}
		return result;
	}
/*** BUG #46829 wanglei 20190418 add end ***/
}
