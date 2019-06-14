/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.contacts.activities;

/* XXX: Please trim these imports, make them in
 * order to improve readability of our codes. */
import com.sprd.contacts.BatchOperationService;
import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.detail.ContactDetailDisplayUtils;
import com.android.contacts.detail.ContactDetailFragment;
import com.android.contacts.detail.ContactDetailLayoutController;
import com.sprd.contacts.detail.SprdContactLoaderFragment;
import com.android.contacts.detail.ContactLoaderFragment.ContactLoaderFragmentListener;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.R;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.common.util.ContactLoaderUtils;
import com.android.contacts.util.PhoneCapabilityTester;
import com.sprd.contacts.common.util.UniverseUtils;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.Telephony.Mms;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.R;
import com.android.contacts.detail.ContactDetailDisplayUtils;
import com.android.contacts.detail.ContactDetailFragment;
import com.android.contacts.detail.ContactDetailLayoutController;
import com.android.contacts.detail.ContactLoaderFragment;
import com.android.contacts.detail.ContactLoaderFragment.ContactLoaderFragmentListener;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.CallUtil;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.util.PhoneCapabilityTester;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import com.sprd.contacts.common.model.account.SimAccountType;
import com.sprd.contacts.common.model.account.USimAccountType;
import com.sprd.android.support.featurebar.FeatureBarHelper;
import android.app.ActionBar.OnMenuVisibilityListener;
import android.widget.TextView;

import android.telephony.PhoneStateListener;
import android.telephony.VoLteServiceState;
import android.telephony.TelephonyManager;
import android.os.SystemProperties;
import com.android.contacts.detail.ActionsViewContainer;
import android.content.ContentValues;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Phone;
/**
* SPRD:
*   fix Bug #149916 Landscape, the contact list of id contact number of grey box shows half.
*
* Original Android code:
* public class ContactDetailActivity extends ContactsActivity
*
* @{
*/
public class ContactDetailActivity extends ContactsActivity implements
SelectAccountDialogFragment.Listener {
/**
* @}
*/
    private static final String TAG = "ContactDetailActivity";

    /** Shows a toogle button for hiding/showing updates. Don't submit with true */
    private static final boolean DEBUG_TRANSITIONS = false;

    private Contact mContactData;
    private Uri mLookupUri;

    // SPRD: bug 821546 remove the dialer button when there is no phone number
    private ArrayList<String> mPhones;
    private FeatureBarHelper mFeatureBarHelper;
    private TextView mLeftSkView;
    private TextView mCenterSkView;
    private TextView mRightSkView;

    private ContactDetailLayoutController mContactDetailLayoutController;
    private SprdContactLoaderFragment mLoaderFragment;
    /** bug350019 Contacts detail shows wrong after joining and spliting contacts */
    private static final int REQUEST_EDIT_CONTACT = 0;

    //SPRD: add for bug633692, Support Volte VT for pikel
    private boolean mImsRegisted = false;
    private String mPhoneNumbers = null;
    private PhoneStateListener mLtePhoneStateListener;
    private static boolean mIsVolteSupport = SystemProperties.getBoolean("persist.sys.support.vt", true);
    private static boolean mIsVolteEnable = SystemProperties.getBoolean("persist.sys.volte.enable", false);

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        // SPRD: bug 821546 remove the dialer button when there is no phone number
        mPhones = new ArrayList<String>();
        if (PhoneCapabilityTester.isUsingTwoPanes(this)) {
            // This activity must not be shown. We have to select the contact in the
            // PeopleActivity instead ==> Create a forward intent and finish
            final Intent originalIntent = getIntent();
            Intent intent = new Intent();
            intent.setAction(originalIntent.getAction());
            intent.setDataAndType(originalIntent.getData(), originalIntent.getType());

            // If we are launched from the outside, we should create a new task, because the user
            // can freely navigate the app (this is different from phones, where only the UP button
            // kicks the user into the full app)
            if (shouldUpRecreateTask(intent)) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            } else {
                intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS |
                        Intent.FLAG_ACTIVITY_FORWARD_RESULT | Intent.FLAG_ACTIVITY_SINGLE_TOP |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }

            intent.setClass(this, PeopleActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.contact_detail_activity);
        mFeatureBarHelper = new FeatureBarHelper(this);
        mLeftSkView = (TextView)mFeatureBarHelper.getOptionsKeyView() ;
        mCenterSkView =(TextView)mFeatureBarHelper.getCenterKeyView();
        mRightSkView = (TextView)mFeatureBarHelper.getBackKeyView();
        mLeftSkView.setText(R.string.default_feature_bar_options);
        mCenterSkView.setText(R.string.launcherDialer);
        mRightSkView.setText(R.string.softkey_back);

        mContactDetailLayoutController = new ContactDetailLayoutController(this, savedState,
                getFragmentManager(), null, findViewById(R.id.contact_detail_container),
                mContactDetailFragmentListener);

        // We want the UP affordance but no app icon.
        // Setting HOME_AS_UP, SHOW_TITLE and clearing SHOW_HOME does the trick.
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            /**
             * SPRD: for UUI
             * Original Android code:
             * actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP |
             * ActionBar.DISPLAY_SHOW_TITLE, ActionBar.DISPLAY_HOME_AS_UP |
             * ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME);
             * actionBar.setTitle("");
             * 
             * @{
             */
            if (UniverseUtils.UNIVERSEUI_SUPPORT) {
                actionBar.setTitle("");
                actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM
                        | ActionBar.DISPLAY_SHOW_TITLE );

            } else {
                actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE,
                        ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME);
                actionBar.setTitle("");
            }
            /**
             * @}
             */

        }
        if (actionBar != null) {
            actionBar.addOnMenuVisibilityListener(new OnMenuVisibilityListener() {
                @Override
                public void onMenuVisibilityChanged(boolean isVisible) {
                    if (isVisible) {
                        mLeftSkView.setText("");
                        mCenterSkView.setText(R.string.default_feature_bar_center);
                        mRightSkView.setText(R.string.cancel);
                    } else {
                        mLeftSkView.setText(R.string.default_feature_bar_options);
                        if (mPhoneNumbers != null) {
                            mCenterSkView.setText(R.string.launcherDialer);
                        } else {
                            mCenterSkView.setText("");
                        }
                        mRightSkView.setText(R.string.softkey_back);
                    }
                }
            });
        }
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
         if (fragment instanceof SprdContactLoaderFragment) {
            mLoaderFragment = (SprdContactLoaderFragment) fragment;
            mLoaderFragment.setListener(mLoaderFragmentListener);
            mLoaderFragment.loadUri(getIntent().getData());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        /**
        * SPRD:
        *   for UUI
        *
        * Original Android code:
        * MenuInflater inflater = getMenuInflater();
        * inflater.inflate(R.menu.star, menu);
        * 
        * @{
        */
        if (!UniverseUtils.UNIVERSEUI_SUPPORT) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.star, menu);
        }
        /**
        * @}
        */
        return true;
    }

    /**
     * SPRD: bug 821546 remove the dialer button when there is no phone number
     * @{
     */
    private boolean isHasPhoneNumber(Contact data){
        /**
         * Bug953988 New card contacts and local contacts can not add email information {@
        */
        mPhones.clear();
        /**
         * @}
         */
        if (data == null) {
            return false;
        }
        ArrayList<ContentValues> cvs = data.getAllContentValues();
        for (ContentValues cv : cvs) {
            String mimeType = cv.getAsString(Data.MIMETYPE);
            if (mimeType != null && mimeType.equals(Phone.CONTENT_ITEM_TYPE)) {
                String phone = cv.getAsString(Phone.NUMBER);
                mPhones.add(phone);
            }
        }
        return mPhones.size() >= 1 ? true : false;
    }
    /**
     * @}
     */

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        /**
         * SPRD: for UUI Original Android code: 
         * final MenuItem starredMenuItem =menu.findItem(R.id.menu_star);
         * 
         * @{
         */
        if (!UniverseUtils.UNIVERSEUI_SUPPORT) {
            final MenuItem starredMenuItem = menu.findItem(R.id.menu_star);
            AccountWithDataSet accountType = null;
            if (mContactData != null) {
                accountType = mContactData.getAccount();
            }
         /**
         * @}
         */
        starredMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Toggle "starred" state
                // Make sure there is a contact
                if (mLookupUri != null) {
                    // Read the current starred value from the UI instead of using the last
                    // loaded state. This allows rapid tapping without writing the same
                    // value several times
                    final boolean isStarred = starredMenuItem.isChecked();

                    // To improve responsiveness, swap out the picture (and tag) in the UI already
                    ContactDetailDisplayUtils.configureStarredMenuItem(starredMenuItem,
                            mContactData.isDirectoryEntry(), mContactData.isUserProfile(),
                            !isStarred);

                    // Now perform the real save
                    Intent intent = ContactSaveService.createSetStarredIntent(
                            ContactDetailActivity.this, mLookupUri, !isStarred);
                    ContactDetailActivity.this.startService(intent);
                }
                return true;
            }
        });
        // If there is contact data, update the starred state
        if (mContactData != null) {
            ContactDetailDisplayUtils.configureStarredMenuItem(starredMenuItem,
                    mContactData.isDirectoryEntry(), mContactData.isUserProfile(),
                    mContactData.getStarred());
        }
            /**
             * SPRD: for UUI Original Android code:
             * 
             * @{
             */
            if (accountType != null
                    && (SimAccountType.ACCOUNT_TYPE.equals(accountType.type) || USimAccountType.ACCOUNT_TYPE
                            .equals(accountType.type))) {
                starredMenuItem.setVisible(false);
            }
        }
            /**
            * @}
            */
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // First check if the {@link ContactLoaderFragment} can handle the key
        if (mLoaderFragment != null && mLoaderFragment.handleKeyDown(keyCode)) return true;

        // Otherwise find the correct fragment to handle the event
        FragmentKeyListener mCurrentFragment = mContactDetailLayoutController.getCurrentPage();
        if (mCurrentFragment != null && mCurrentFragment.handleKeyDown(keyCode)) return true;

        // In the last case, give the key event to the superclass.
        return super.onKeyDown(keyCode, event);
    }

    /* SPRD: add for bug633692, Support Volte VT for pikel @} *//*
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        View view = this.getCurrentFocus();
        mPhoneNumbers = null;
        try {
            TextView textView = (TextView) this.getCurrentFocus().findViewById(R.id.data);
            if (textView != null && textView.getText() != null) {
                mPhoneNumbers = textView.getText().toString();
            }
        } catch (Exception e){
            Log.d(TAG, "onKeyUp Exception");
        }
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            if (mPhoneNumbers != null && !mPhoneNumbers.isEmpty() && isPhoneNumber(mPhoneNumbers)) {
                if (mIsVolteSupport && mImsRegisted) {
                    CallUtil.showCallDialogAlert(ContactDetailActivity.this, mPhoneNumbers, null);
                    return true;
                } else {
                    Intent intent = CallUtil.getCallIntent(mPhoneNumbers);
                    startActivity(intent);
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }*/

    public void createPhoneStateListener() {
        mLtePhoneStateListener = new PhoneStateListener() {
            @Override
            public void onVoLteServiceStateChanged(VoLteServiceState serviceState) {
                Log.i(TAG, "=======PhoneStateListener: ImsState = " + serviceState.getImsState() + "  srvccState = "+ serviceState.getSrvccState());
                mImsRegisted = (serviceState.getSrvccState() != VoLteServiceState.HANDOVER_STARTED
                            && (serviceState.getImsState() == 1));
            }
        };
    }

    public void startMonitor() {
        if (mIsVolteEnable) {
            TelephonyManager.from(this).listen(mLtePhoneStateListener,
                    PhoneStateListener.LISTEN_SERVICE_STATE
                            | PhoneStateListener.LISTEN_VOLTE_STATE);
        }
    }

    public void stopMonitor() {
        if (mIsVolteEnable) {
            TelephonyManager.from(this).listen(mLtePhoneStateListener,
                    PhoneStateListener.LISTEN_NONE);
        }
        mLtePhoneStateListener = null;
    }

    public static boolean isPhoneNumber(String text) {
        char[] sChar = text.toCharArray();
        for (char c : sChar) {
            if (checkCharacter(c)) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }

    private static boolean checkCharacter(char c) {
        return ((c >= '0' && c <= '9') || c == ',' || c == ';' || c == '*'
                || c == '#' || c == '+' || c == '-' || c == '(' || c == ')'
                || c == ',' || c == '/' || c == 'N' || c == '.' || c == ' ' || c == ';');
    }
    /* @} */

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mContactDetailLayoutController != null) {
            mContactDetailLayoutController.onSaveInstanceState(outState);
        }
        /*
        * SPRD:
        *   Bug 331482
        *
        * @{
        */
        mIsSaveInstance = true;
        /*
        * @}
        */
    }
    /*
     * SPRD:
     *   Bug 350019 Contacts detail shows wrong after joining and spliting contacts
     *
     * @{
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
             if (resultCode == RESULT_OK && requestCode == REQUEST_EDIT_CONTACT && data != null
                   && data.getAction() == "action_split_done") {
              Uri resultUri = data.getParcelableExtra("splitNewUri");

             if (resultUri != null && mLoaderFragment != null) {
                    mLoaderFragment.loadUri(resultUri);
             }
         }
    }
    /*
    * @}
    */
    private ContactLoaderFragmentListener mLoaderFragmentListener =
            new ContactLoaderFragmentListener() {
        @Override
        public void onContactNotFound() {
            finish();
        }

        @Override
        public void onDetailsLoaded(final Contact result) {
            if (result == null) {
                return;
            }
            /**
             * SPRD: bug 821546 remove the dialer button when there is no phone number
             * @{
             */
            if (isHasPhoneNumber(result)) {
                mCenterSkView.setText(R.string.launcherDialer);
                /**
                 * Bug953988 New card contacts and local contacts can not add email information {@
                */
                mCenterSkView.setVisibility(View.VISIBLE);
                /**
                 * @}
                 */
            } else {
                mCenterSkView.setText("");
            }
            /*
             * @}
             */

            // Since {@link FragmentTransaction}s cannot be done in the onLoadFinished() of the
            // {@link LoaderCallbacks}, then post this {@link Runnable} to the {@link Handler}
            // on the main thread to execute later.
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // If the activity is destroyed (or will be destroyed soon), don't update the UI
                    if (isFinishing()) {
                        return;
                    }
                    mContactData = result;
                    mLookupUri = result.getLookupUri();
                    invalidateOptionsMenu();
                    setupTitle();
                    mContactDetailLayoutController.setContactData(mContactData);
                }
            });
        }

        @Override
        public void onEditRequested(Uri contactLookupUri) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            intent.putExtra(
                    ContactEditorActivity.INTENT_KEY_FINISH_ACTIVITY_ON_SAVE_COMPLETED, true);
            // Don't finish the detail activity after launching the editor because when the
            // editor is done, we will still want to show the updated contact details using
            // this activity.
            /**
             * SPRD:
             *     Bug 350019 Contacts detail shows wrong after joining and spliting contacts
             *
             * Original Android code:
             *  startActivity(intent);
             *
             * @{
             */
            try {
                startActivityForResult(intent, REQUEST_EDIT_CONTACT);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "startActivity() failed: " + e);
                Toast.makeText(ContactDetailActivity.this, R.string.missing_app,
                        Toast.LENGTH_SHORT).show();
            }
             /*
              * @}
              */
        }

        @Override
        public void onDeleteRequested(Uri contactUri) {
            ContactDeletionInteraction.start(ContactDetailActivity.this, contactUri, true);
        }

                 /* @{
                 */
                @Override
                public void onCopyRequested(final String lookupKey) {
                    /*
                     * SPRD:
                     *   Bug 331482
                     *
                     * @{
                     */
                     if (mIsSaveInstance) {
                        return;
                    }
                     /*
                     * @}
                     */
                    Bundle args = new Bundle();
                    if (mContactData != null) {
                        args.putParcelable("account", mContactData.getAccount());
                    }
                    SelectAccountDialogFragment.show(getFragmentManager(),
                            R.string.copy_to,
                            AccountListFilter.ACCOUNTS_CONTACT_WRITABLE,
                            args);
                }

                @Override
                public void onNotFilterRequested(ArrayList<String> phones, String name) {
                    // remove all phones to blacklist
                    for (int i = 0; i < phones.size(); i++) {
                        String phone = phones.get(i);
                        if (ContactLoaderUtils.CheckIsBlackNumber(ContactDetailActivity.this, phone)) {
                            if (!ContactLoaderUtils.deleteFromBlockList(ContactDetailActivity.this, phone)) {
                                Toast.makeText(ContactDetailActivity.this,
                                        R.string.failed_removeFromBlacklist,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                    }
                    Toast.makeText(ContactDetailActivity.this,
                            R.string.success_removeFromBlacklist,
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFilterRequested(final ArrayList<String> phones, String name) {
                    try {
                        for (int i = 0; i < phones.size(); i++) {
                            String phone = phones.get(i);
                            if (!ContactLoaderUtils.CheckIsBlackNumber(ContactDetailActivity.this, phone)) {
                                /* SPRD: modify for Bug 827547. @{ */
                                if (!Mms.isPhoneNumber(phone)) {
                                    Toast.makeText(ContactDetailActivity.this,
                                            R.string.failed_wrong_phoneNumber,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                if (!ContactLoaderUtils.putToBlockList(ContactDetailActivity.this, phone, BLOCK_ALL,
                                        name)) {
                                    Toast.makeText(ContactDetailActivity.this,
                                            R.string.failed_addToBlacklist,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                /* @} */
                            }
                        }
                        Toast.makeText(ContactDetailActivity.this, R.string.success_addToBlacklist,
                                Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(ContactDetailActivity.this, R.string.failed_addToBlacklist,
                                Toast.LENGTH_SHORT).show();
                    }
                }
                /**
                 * @}
                 */

                @Override
                public void onSendMMS(){
/*** BUG #47088 wanglei 20190429 add begin ***/
					if (mPhoneNumbers == null) {
						View view = getCurrentFocus();
						try {
							TextView textView = (TextView) ContactDetailActivity.this.getCurrentFocus()
									.findViewById(R.id.data);
							if (textView != null && textView.getText() != null) {
								mPhoneNumbers = textView.getText().toString();
							}
						} catch (Exception e) {
							Log.d(TAG, "onSendMMS e = " + e);
						}
					}
/*** BUG #47088 wanglei 20190429 add end ***/
                    if (mPhoneNumbers != null) {
                        final ComponentName smsComponent = PhoneCapabilityTester.getSmsComponent(ContactDetailActivity.this);
                        Intent smsIntent = new Intent(Intent.ACTION_SENDTO,
                                Uri.fromParts(CallUtil.SCHEME_SMSTO, mPhoneNumbers, null));
                        smsIntent.setComponent(smsComponent);
                        startActivity(smsIntent);
                    }
                }
    };

    /**
     * Setup the activity title and subtitle with contact name and company.
     */
    private void setupTitle() {
        CharSequence displayName = ContactDetailDisplayUtils.getDisplayName(this, mContactData);
        String company =  ContactDetailDisplayUtils.getCompany(this, mContactData);

        ActionBar actionBar = getActionBar();
        /**
         * SPRD: Bug636548 The words on the actionBar should not be pressed in the ContactDetail {@
         * */
        actionBar.setHomeButtonEnabled(false);
        /**
         * @}
         * */
        actionBar.setTitle(displayName);

        final StringBuilder talkback = new StringBuilder();
        if (!TextUtils.isEmpty(displayName)) {
            talkback.append(displayName);
        }

        if (talkback.length() != 0) {
            AccessibilityManager accessibilityManager =
                    (AccessibilityManager) this.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (accessibilityManager.isEnabled()) {
                View decorView = getWindow().getDecorView();
                decorView.setContentDescription(talkback);
                decorView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            }
        }
    }

    private ContactDetailFragment.Listener mContactDetailFragmentListener =
            new ContactDetailFragment.Listener() {
        @Override
        public void onItemClicked(Intent intent) {
            if (intent == null) {
                return;
            }
            try {
                //SPRD: add for bug633692/636543/636534, Support Volte VT for pikel
                View view = ContactDetailActivity.this.getCurrentFocus();
                mPhoneNumbers = null;
                try {
                    TextView textView = (TextView) ContactDetailActivity.this.getCurrentFocus().findViewById(R.id.data);
                    if (textView != null && textView.getText() != null) {
                        mPhoneNumbers = textView.getText().toString();
                    }
                } catch (Exception e){
                    Log.d(TAG, "onKeyUp Exception");
                }
                if (mPhoneNumbers != null && !mPhoneNumbers.isEmpty() && isPhoneNumber(mPhoneNumbers)
                        && intent != null && intent.getAction() != null && !intent.getAction().equals(Intent.ACTION_SENDTO)) {
                    if (mIsVolteSupport && mImsRegisted) {
                        CallUtil.showCallDialogAlert(ContactDetailActivity.this, mPhoneNumbers, null);
                        return;
                    }
                }
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No activity found for intent: " + intent);
            }
        }
        @Override
        public void onCallItemClicked(String number) {
            if (number == null) {
                return;
            }
            if (number != null && !number.isEmpty() && isPhoneNumber(number)) {
                if (mIsVolteSupport && mImsRegisted) {
                    CallUtil.showCallDialogAlert(ContactDetailActivity.this, number, null);
                    return;
                }
            }
            Intent intent = CallUtil.getCallIntent(number);
            startActivity(intent);
        }
        @Override
        public void onCreateRawContactRequested(
                ArrayList<ContentValues> values, AccountWithDataSet account) {
            Toast.makeText(ContactDetailActivity.this, R.string.toast_making_personal_copy,
                    Toast.LENGTH_LONG).show();
            Intent serviceIntent = ContactSaveService.createNewRawContactIntent(
                    ContactDetailActivity.this, values, account,
                    ContactDetailActivity.class, Intent.ACTION_VIEW);
            startService(serviceIntent);

        }
    };

    /**
     * This interface should be implemented by {@link Fragment}s within this
     * activity so that the activity can determine whether the currently
     * displayed view is handling the key event or not.
     */
    public interface FragmentKeyListener {
        /**
         * Returns true if the key down event will be handled by the implementing class, or false
         * otherwise.
         */
        public boolean handleKeyDown(int keyCode);
    }

    /**
     * SPRD:
     * 
     * @{
     */
    private static final int BLOCK_ALL = 7;   //block mms,phone,vt
    private boolean mIsSaveInstance = false;

    @Override
    protected void onResume() {
        super.onResume();
        mIsSaveInstance = false;
        //SPRD: add for bug633692, Support Volte VT for pikel
        createPhoneStateListener();
        startMonitor();
    }

    //SPRD: add for bug633692, Support Volte VT for pikel
    @Override
    public void onPause() {
        super.onPause();
        stopMonitor();
    }


    @Override
    public void onAccountChosen(AccountWithDataSet dstAccount, Bundle extraArgs) {
        if (mContactData == null) {
            return;
        }

        confirmImport(dstAccount, new String[] {
                Long.toString(mContactData.getContactId())
        });
    }

    @Override
    public void onAccountSelectorCancelled() {

    }

    private void confirmImport(final AccountWithDataSet dstAccount, final String[] ids) {
        if (dstAccount != null
                && (SimAccountType.ACCOUNT_TYPE.equals(dstAccount.type) || USimAccountType.ACCOUNT_TYPE
                        .equals(dstAccount.type))) {
            Bundle args = new Bundle();
            args.putParcelable("accounts", dstAccount);
            args.putStringArray("result_alternative", ids);
            ConfirmCopyDetailContactDialogFragment dialog =
                    new ConfirmCopyDetailContactDialogFragment();
            dialog.setArguments(args);
            dialog.show(getFragmentManager(), null);
        } else {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(ContactDetailActivity.this,
                    BatchOperationService.class));
            intent.putExtra(BatchOperationService.KEY_MODE,
                    BatchOperationService.MODE_START_BATCH_IMPORT_EXPORT);
            intent.putExtra("dst_account", dstAccount);
            intent.putStringArrayListExtra("result_alternative",
                    new ArrayList<String>(Arrays.asList(ids)));
            startService(intent);
        }

    }

    public static class ConfirmCopyDetailContactDialogFragment extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent();
                            intent.setComponent(new ComponentName(getActivity(),
                                    BatchOperationService.class));
                            intent.putExtra(BatchOperationService.KEY_MODE,
                                    BatchOperationService.MODE_START_BATCH_IMPORT_EXPORT);
                            AccountWithDataSet accountData = (AccountWithDataSet) getArguments()
                                    .getParcelable("accounts");
                            String[] ids = (String[]) getArguments().getStringArray(
                                    "result_alternative");
                            intent.putExtra("dst_account", accountData);
                            intent.putStringArrayListExtra("result_alternative",
                                    new ArrayList<String>(Arrays.asList(ids)));
                            getActivity().startService(intent);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                    .setTitle(android.R.string.dialog_alert_title)
                    .setMessage(R.string.alert_maybe_lost_info)
                    .create();
        }
    }
    /**
     * @}
     */
    /**
     * Bug647293 the soft-key should be disappear when the popWindow show
     * @{
     */
    public TextView getLeftSkView() {
        return mLeftSkView;
    }
    /**
     * @}
     */

    /**
     * Bug953988 New card contacts and local contacts can not add email information {@
     */
    public TextView getCenterKeyView(){return  mCenterSkView;}


    public void setmPhoneNumbers(String mPhoneNumbers) {
        this.mPhoneNumbers = mPhoneNumbers;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode=event.getKeyCode();
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
            FragmentKeyListener mCurrentFragment = mContactDetailLayoutController.getCurrentPage();
            if (mCurrentFragment != null && mCurrentFragment.handleKeyDown(keyCode)) return super.dispatchKeyEvent(event);
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * @}
     */
    
/*** memory optimization wanglei 20190416 add begin ***/
	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (mContactDetailLayoutController != null) {
			mContactDetailLayoutController.onDestroy();
		}
		
		mContactDetailFragmentListener = null;
		mContactDetailLayoutController = null;
		mLoaderFragmentListener = null;
		mPhones = null;
		mCenterSkView = null;
		mRightSkView = null;
		mLoaderFragment = null;
		mFeatureBarHelper = null;
		mLookupUri = null;
		mContactData = null;
		mLeftSkView = null;
	}
/*** memory optimization wanglei 20190416 add end ***/
}
