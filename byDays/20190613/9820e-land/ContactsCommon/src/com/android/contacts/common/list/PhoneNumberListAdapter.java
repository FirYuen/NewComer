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
 * limitations under the License.
 */
package com.android.contacts.common.list;

import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Callable;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.R;

import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.extensions.ExtendedPhoneDirectoriesManager;
import com.android.contacts.common.extensions.ExtensionsFactory;
import com.android.contacts.common.util.Constants;
import com.sprd.contacts.common.util.UniverseUtils;

import java.util.ArrayList;
import java.util.List;

//add by SPRD
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.AccountTypeManager;
import android.provider.CallLog.Calls;
/**
 * A cursor adapter for the {@link Phone#CONTENT_ITEM_TYPE} and
 * {@link SipAddress#CONTENT_ITEM_TYPE}.
 *
 * By default this adapter just handles phone numbers. When {@link #setUseCallableUri(boolean)} is
 * called with "true", this adapter starts handling SIP addresses too, by using {@link Callable}
 * API instead of {@link Phone}.
 */
public class PhoneNumberListAdapter extends ContactEntryListAdapter {
    private static final String TAG = PhoneNumberListAdapter.class.getSimpleName();
    private static final Uri REGULAR_MATCH_URI
                            = Uri.parse("content://call_log/calls/regularmatch");
    private static final Uri CALLABLE_MATCH_URI
                            = Uri.parse("content://call_log/calls/callabledialmatch");
    // A list of extended directories to add to the directories from the database
    private final List<DirectoryPartition> mExtendedDirectories;

    // Extended directories will have ID's that are higher than any of the id's from the database.
    // Thi sis so that we can identify them and set them up properly. If no extended directories
    // exist, this will be Long.MAX_VALUE
    private long mFirstExtendedDirectoryId = Long.MAX_VALUE;
    private String mListRequestModeSelection;

    public static class PhoneQuery {
        public static final String[] PROJECTION_PRIMARY = new String[] {
            Phone._ID,                          // 0
            Phone.TYPE,                         // 1
            Phone.LABEL,                        // 2
            Phone.NUMBER,                       // 3
            Phone.CONTACT_ID,                   // 4
            Phone.LOOKUP_KEY,                   // 5
            Phone.PHOTO_ID,                     // 6
            Phone.DISPLAY_NAME_PRIMARY,         // 7
            Phone.PHOTO_THUMBNAIL_URI,          // 8
           /**
            * SPRD:
            * 
            * @{
            */
            Contacts.DISPLAY_ACCOUNT_TYPE,
            Contacts.DISPLAY_ACCOUNT_NAME,
            RawContacts.SYNC1,
            /**
            * @}
            */
};

        /* SPRD: add for bug437815 && bug456761 CallLog matching search@{ */
        public static final String[] PROJECTION_DIALMATCH = new String[] {
            Phone._ID, // 0
            Phone.TYPE, // 1
            Phone.LABEL, // 2
            Phone.NUMBER, // 3
            Phone.CONTACT_ID, // 4
            Phone.LOOKUP_KEY, // 5
            Phone.PHOTO_ID, // 6
            Phone.DISPLAY_NAME_PRIMARY, // 7
            Phone.PHOTO_THUMBNAIL_URI, // 8
            Contacts.DISPLAY_ACCOUNT_TYPE,// 9
            Contacts.DISPLAY_ACCOUNT_NAME, // 10
            "item_type",
            Calls.DATE,
            Calls.TYPE,
            Calls.CACHED_LOOKUP_URI,
            RawContacts.SYNC1
    };
    public static final String[] PROJECTION_DIALMATCH_ALTERNATIVE = new String[] {
            Phone._ID, // 0
            Phone.TYPE, // 1
            Phone.LABEL, // 2
            Phone.NUMBER, // 3
            Phone.CONTACT_ID, // 4
            Phone.LOOKUP_KEY, // 5
            Phone.PHOTO_ID, // 6
            Phone.DISPLAY_NAME_ALTERNATIVE, // 7
            Phone.PHOTO_THUMBNAIL_URI, // 8
            Contacts.DISPLAY_ACCOUNT_TYPE,
            Contacts.DISPLAY_ACCOUNT_NAME,
            "item_type",
            Calls.DATE,
            Calls.TYPE,
            Calls.CACHED_LOOKUP_URI,
            RawContacts.SYNC1
    };
    /* @} */

        public static final String[] PROJECTION_ALTERNATIVE = new String[] {
            Phone._ID,                          // 0
            Phone.TYPE,                         // 1
            Phone.LABEL,                        // 2
            Phone.NUMBER,                       // 3
            Phone.CONTACT_ID,                   // 4
            Phone.LOOKUP_KEY,                   // 5
            Phone.PHOTO_ID,                     // 6
            Phone.DISPLAY_NAME_ALTERNATIVE,     // 7
            Phone.PHOTO_THUMBNAIL_URI,          // 8
           /**
            * SPRD:
            * 
            * @{
            */
            Contacts.DISPLAY_ACCOUNT_TYPE,
            Contacts.DISPLAY_ACCOUNT_NAME,
            RawContacts.SYNC1,
            /**
            * @}
            */
};

        public static final int PHONE_ID                = 0;
        public static final int PHONE_TYPE              = 1;
        public static final int PHONE_LABEL             = 2;
        public static final int PHONE_NUMBER            = 3;
        public static final int CONTACT_ID              = 4;
        public static final int LOOKUP_KEY              = 5;
        public static final int PHOTO_ID                = 6;
        public static final int DISPLAY_NAME            = 7;
        public static final int PHOTO_URI               = 8;
        /**
        * SPRD:
        * 
        * @{
        */
        public static final int CONTACT_DISPLAY_ACCOUNT_TYPE = 9;
        public static final int CONTACT_DISPLAY_ACCOUNT_NAME = 10;
        /**
        * @}
        */
        public static final int ITEM_TYPE = 11;
        public static final int CALLS_DATE = 12;
        public static final int CALLS_TYPE = 13;
        public static final int CACHED_LOOKUP_URI = 14;
        public static final int SYNC1 = 15;
    }

    private final CharSequence mUnknownNameText;

    private ContactListItemView.PhotoPosition mPhotoPosition;

    private boolean mUseCallableUri;
    /* SPRD: add for bug437815 @{ */
    private boolean mIsDialMatchMode;
    private boolean mIsRegularSearch;
    /* @} */
    public void setListRequestModeSelection(String selection) {
        mListRequestModeSelection = selection;
    }

    public PhoneNumberListAdapter(Context context) {
        super(context);
        setDefaultFilterHeaderText(R.string.list_filter_phones);
        mUnknownNameText = context.getText(android.R.string.unknownName);

        /* SPRD: add for bug437815 && bug442917@{ */
        mIsDialMatchMode = false;
        mIsRegularSearch = false;
        /* @} */
        final ExtendedPhoneDirectoriesManager manager
                = ExtensionsFactory.getExtendedPhoneDirectoriesManager();
        if (manager != null) {
            mExtendedDirectories = manager.getExtendedDirectories(mContext);
        } else {
            // Empty list to avoid sticky NPE's
            mExtendedDirectories = new ArrayList<DirectoryPartition>();
        }
    }
    /* SPRD: add for bug437815 && bug442917  @{ */
    protected void setDialMatchMode(boolean isDialMatchMode) {
        mIsDialMatchMode = isDialMatchMode;
    }
    protected void setRegularSearch(boolean isRegularSearch) {
        mIsRegularSearch = isRegularSearch;
    }
    /* @} */

    protected CharSequence getUnknownNameText() {
        return mUnknownNameText;
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
         String query = getQueryString();
        if (query == null) {
            query = "";
        }
        /* SPRD: modify for bug437815 && bug442917  @{ */
        if (TextUtils.isEmpty(query)) {
            mIsDialMatchMode = false;
        } else if (mIsRegularSearch){
            mIsDialMatchMode = true;
        }
        /* @} */
        if (isExtendedDirectory(directoryId)) {
            final DirectoryPartition directory = getExtendedDirectoryFromId(directoryId);
            final String contentUri = directory.getContentUri();
            if (contentUri == null) {
                throw new IllegalStateException("Extended directory must have a content URL: "
                        + directory);
            }
            final Builder builder = Uri.parse(contentUri).buildUpon();
            builder.appendPath(query);
            builder.appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                    String.valueOf(getDirectoryResultLimit(directory)));
            loader.setUri(builder.build());
            loader.setProjection(PhoneQuery.PROJECTION_PRIMARY);
        } else {
            final boolean isRemoteDirectoryQuery = isRemoteDirectory(directoryId);
            final Builder builder;
            if (isSearchMode()) {
                final Uri baseUri;
                if (isRemoteDirectoryQuery) {
                    baseUri = Phone.CONTENT_FILTER_URI;
                } else if (mUseCallableUri) {
                    baseUri = mIsDialMatchMode ? CALLABLE_MATCH_URI : Callable.CONTENT_FILTER_URI;
                } else {
                    baseUri = mIsDialMatchMode ? REGULAR_MATCH_URI : Phone.CONTENT_FILTER_URI;
                }
                builder = baseUri.buildUpon();
                builder.appendPath(query);      // Builder will encode the query
                builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId));
                if (isRemoteDirectoryQuery) {
                    builder.appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                            String.valueOf(getDirectoryResultLimit(getDirectoryById(directoryId))));
                }
            } else {
                final Uri baseUri = mUseCallableUri ? Callable.CONTENT_URI : Phone.CONTENT_URI;
                builder = baseUri.buildUpon().appendQueryParameter(
                        ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT));
                if (isSectionHeaderDisplayEnabled()) {
                    builder.appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true");
                }
                applyFilter(loader, builder, directoryId, getFilter());
                final String prevSelection = loader.getSelection();
                final String newSelection;
                //SPRD: add for bug617830, add fdn feature
                /*if (!TextUtils.isEmpty(prevSelection)) {
                    newSelection = prevSelection
                            + " AND (" + RawContacts.SYNC2 + " NOT LIKE " + "'fdn%' OR " + RawContacts.SYNC2 + " IS NULL)";
                } else {
                    newSelection = "";
                }*/
                if ("mode_pick".equals(mListRequestModeSelection)) {
                    newSelection =" (" + RawContacts.SYNC2 + " NOT LIKE " + "'fdn%' OR " + RawContacts.SYNC2 + " IS NULL)";
                } else {
                    newSelection = "";
                }
                loader.setSelection(newSelection);
            }

            // Remove duplicates when it is possible.
            if (!mIsDialMatchMode) {
                builder.appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true");
            }
            loader.setUri(builder.build());

            // TODO a projection that includes the search snippet
            if (getContactNameDisplayOrder() ==
                    ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
                loader.setProjection(mIsDialMatchMode ?
                            PhoneQuery.PROJECTION_DIALMATCH:PhoneQuery.PROJECTION_PRIMARY);
            } else {
                loader.setProjection(mIsDialMatchMode ?
                   PhoneQuery.PROJECTION_DIALMATCH_ALTERNATIVE:PhoneQuery.PROJECTION_ALTERNATIVE);
            }

            if (!mIsDialMatchMode) {
                if (getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
                    loader.setSortOrder(Phone.SORT_KEY_PRIMARY);
                } else {
                    loader.setSortOrder(Phone.SORT_KEY_ALTERNATIVE);
                }
            }
        }
    }

    protected boolean isExtendedDirectory(long directoryId) {
        return directoryId >= mFirstExtendedDirectoryId;
    }

    private DirectoryPartition getExtendedDirectoryFromId(long directoryId) {
        final int directoryIndex = (int) (directoryId - mFirstExtendedDirectoryId);
        return mExtendedDirectories.get(directoryIndex);
    }

    /**
     * Configure {@code loader} and {@code uriBuilder} according to {@code directoryId} and {@code
     * filter}.
     */
    private void applyFilter(CursorLoader loader, Uri.Builder uriBuilder, long directoryId,
            ContactListFilter filter) {
        if (filter == null || directoryId != Directory.DEFAULT) {
            return;
        }

        final StringBuilder selection = new StringBuilder();
        final List<String> selectionArgs = new ArrayList<String>();

        switch (filter.filterType) {
            case ContactListFilter.FILTER_TYPE_CUSTOM: {
                selection.append(Contacts.IN_VISIBLE_GROUP + "=1");
                selection.append(" AND " + Contacts.HAS_PHONE_NUMBER + "=1");
                break;
            }
            case ContactListFilter.FILTER_TYPE_ACCOUNT: {
                filter.addAccountQueryParameterToUrl(uriBuilder);
                break;
            }
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS:
            case ContactListFilter.FILTER_TYPE_DEFAULT:
                break; // No selection needed.
            case ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY:
                break; // This adapter is always "phone only", so no selection needed either.
            default:
                Log.w(TAG, "Unsupported filter type came " +
                        "(type: " + filter.filterType + ", toString: " + filter + ")" +
                        " showing all contacts.");
                // No selection.
                break;
        }
        loader.setSelection(selection.toString());
        loader.setSelectionArgs(selectionArgs.toArray(new String[0]));
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor) getItem(position)).getString(PhoneQuery.DISPLAY_NAME);
    }

    public String getPhoneNumber(int position) {
        final Cursor item = (Cursor)getItem(position);
        return item != null ? item.getString(PhoneQuery.PHONE_NUMBER) : null;
    }

    /**
     * Builds a {@link Data#CONTENT_URI} for the given cursor position.
     *
     * @return Uri for the data. may be null if the cursor is not ready.
     */
    public Uri getDataUri(int position) {
        final int partitionIndex = getPartitionForPosition(position);
        final Cursor item = (Cursor)getItem(position);
        return item != null ? getDataUri(partitionIndex, item) : null;
    }

    public Uri getDataUri(int partitionIndex, Cursor cursor) {
        final long directoryId =
                ((DirectoryPartition)getPartition(partitionIndex)).getDirectoryId();
        if (!isRemoteDirectory(directoryId)) {
            final long phoneId = cursor.getLong(PhoneQuery.PHONE_ID);
            return ContentUris.withAppendedId(Data.CONTENT_URI, phoneId);
        }
        return null;
    }

    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setUnknownNameText(mUnknownNameText);
        view.setQuickContactEnabled(isQuickContactEnabled());
        view.setPhotoPosition(mPhotoPosition);
        return view;
    }

    protected void setHighlight(ContactListItemView view, Cursor cursor) {
        view.setHighlightedPrefix(isSearchMode() ? getUpperCaseQueryString() : null);
    }

    // Override default, which would return number of phone numbers, so we
    // instead return number of contacts.
    @Override
    protected int getResultCount(Cursor cursor) {
        if (cursor == null) {
            return 0;
        }
        cursor.moveToPosition(-1);
        long curContactId = -1;
        int numContacts = 0;
        while(cursor.moveToNext()) {
            final long contactId = cursor.getLong(PhoneQuery.CONTACT_ID);
            if (contactId != curContactId) {
                curContactId = contactId;
                ++numContacts;
            }
        }
        return numContacts;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        ContactListItemView view = (ContactListItemView)itemView;

        setHighlight(view, cursor);

        // Look at elements before and after this position, checking if contact IDs are same.
        // If they have one same contact ID, it means they can be grouped.
        //
        // In one group, only the first entry will show its photo and its name, and the other
        // entries in the group show just their data (e.g. phone number, email address).
        cursor.moveToPosition(position);
        boolean isFirstEntry = true;
        boolean showBottomDivider = true;
        final long currentContactId = cursor.getLong(PhoneQuery.CONTACT_ID);
        if (cursor.moveToPrevious() && !cursor.isBeforeFirst()) {
            final long previousContactId = cursor.getLong(PhoneQuery.CONTACT_ID);
            if (currentContactId == previousContactId && previousContactId != -1) {
                isFirstEntry = false;
            }
        }
        cursor.moveToPosition(position);
        if (cursor.moveToNext() && !cursor.isAfterLast()) {
            final long nextContactId = cursor.getLong(PhoneQuery.CONTACT_ID);
            if (currentContactId == nextContactId) {
                // The following entry should be in the same group, which means we don't want a
                // divider between them.
                // TODO: we want a different divider than the divider between groups. Just hiding
                // this divider won't be enough.
                showBottomDivider = false;
            }
        }
        cursor.moveToPosition(position);

        bindSectionHeaderAndDivider(view, position);
        if (isFirstEntry) {
            bindName(view, cursor);
            if (isQuickContactEnabled()) {
                /**
                * SPRD:
                * 
                *
                * Original Android code:
                * bindQuickContact(view, partition, cursor, PhoneQuery.PHONE_PHOTO_ID,
                        PhoneQuery.PHOTO_URI, PhoneQuery.CONTACT_ID,
                        PhoneQuery.LOOKUP_KEY, PhoneQuery.DISPLAY_NAME);
                * 
                * @{
                */
                bindQuickContact(view, partition, cursor, PhoneQuery.PHOTO_ID,
                        PhoneQuery.PHOTO_URI,
                        PhoneQuery.CONTACT_ID, PhoneQuery.LOOKUP_KEY,
                        PhoneQuery.CONTACT_DISPLAY_ACCOUNT_TYPE,
                        PhoneQuery.CONTACT_DISPLAY_ACCOUNT_NAME,
                        PhoneQuery.DISPLAY_NAME);
                /**
                * @}
                */
            } else {
                if (getDisplayPhotos()) {
                    bindPhoto(view, partition, cursor);
                }
            }
        } else {
            unbindName(view);

            view.removePhotoView(true, false);
        }
        /**
        * SPRD:
        *
        * @{
        */
        if (isMultiPickerSupported()) {
            // note: position is NOT the real position in the adapter, in face,
            // it is the relative offset in the partition
            bindCheckbox(view, getRealPosition(partition, position));
        } 
        /**
        * @}
        */
        final DirectoryPartition directory = (DirectoryPartition) getPartition(partition);
        bindPhoneNumber(view, cursor, directory.isDisplayNumber());
        view.setDividerVisible(showBottomDivider);
    }

    protected void bindPhoneNumber(ContactListItemView view, Cursor cursor, boolean displayNumber) {
        CharSequence label = null;
        if (displayNumber &&  !cursor.isNull(PhoneQuery.PHONE_TYPE)) {
            final int type = cursor.getInt(PhoneQuery.PHONE_TYPE);
            final String customLabel = cursor.getString(PhoneQuery.PHONE_LABEL);

            // TODO cache
            label = Phone.getTypeLabel(getContext().getResources(), type, customLabel);
        }
        view.setLabel(label);
        final String text;
        if (displayNumber) {
            text = cursor.getString(PhoneQuery.PHONE_NUMBER);
        } else {
            // Display phone label. If that's null, display geocoded location for the number
            final String phoneLabel = cursor.getString(PhoneQuery.PHONE_LABEL);
            if (phoneLabel != null) {
                text = phoneLabel;
            } else {
                final String phoneNumber = cursor.getString(PhoneQuery.PHONE_NUMBER);
                text = GeoUtil.getGeocodedLocationFor(mContext, phoneNumber);
            }
        }
        view.setPhoneNumber(text);
    }

    protected void bindSectionHeaderAndDivider(final ContactListItemView view, int position) {
        if (isSectionHeaderDisplayEnabled()) {
            Placement placement = getItemPlacementInSection(position);
            view.setSectionHeader(placement.firstInSection ? placement.sectionHeader : null);
            view.setDividerVisible(!placement.lastInSection);
        } else {
            view.setSectionHeader(null);
            view.setDividerVisible(true);
        }
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, PhoneQuery.DISPLAY_NAME, getContactNameDisplayOrder());
        // Note: we don't show phonetic names any more (see issue 5265330)
    }

    protected void unbindName(final ContactListItemView view) {
        view.hideDisplayName();
    }

    protected void bindPhoto(final ContactListItemView view, int partitionIndex, Cursor cursor) {
        if (!isPhotoSupported(partitionIndex)) {
            view.removePhotoView();
            return;
        }

        long photoId = 0;
        if (!cursor.isNull(PhoneQuery.PHOTO_ID)) {
            photoId = cursor.getLong(PhoneQuery.PHOTO_ID);
        }

        /*
        * SPRD:
        *   Bug 340257
        *
        * @orig
            if (photoId != 0) {
                getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId, false);
            } else {
                final String photoUriString = cursor.getString(PhoneQuery.PHOTO_URI);
                final Uri photoUri = photoUriString == null ? null : Uri.parse(photoUriString);
                getPhotoLoader().loadDirectoryPhoto(view.getPhotoView(), photoUri, false);
            }
        *
        * @{
        */
        String accountType = cursor.getString(PhoneQuery.CONTACT_DISPLAY_ACCOUNT_TYPE);
        String accountName = cursor.getString(PhoneQuery.CONTACT_DISPLAY_ACCOUNT_NAME);
        String sync1 = cursor.getString(cursor.getColumnIndex(RawContacts.SYNC1));
        AccountWithDataSet account = null;
        if (accountType != null
                && accountName != null
                && (accountType
                        .equalsIgnoreCase(AccountTypeManager.ACCOUNT_SIM) || accountType
                        .equalsIgnoreCase(AccountTypeManager.ACCOUNT_USIM))) {
            account = new AccountWithDataSet(accountName, accountType, null);
            getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId, false, null,
                  getPhotoLoader().getDefaultPhotoProviderForAccount(mContext, account, "sdn".equals(sync1)));
        } else {
            /* add by android-4.4.4_r1 @ { */
            if (photoId != 0) {
                getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId, false, null);
            } else {
                final String photoUriString = cursor.getString(PhoneQuery.PHOTO_URI);
                final Uri photoUri = photoUriString == null ? null : Uri.parse(photoUriString);

                DefaultImageRequest request = null;
                if (photoUri == null) {
                    final String displayName = cursor.getString(PhoneQuery.DISPLAY_NAME);
                    final String lookupKey = cursor.getString(PhoneQuery.LOOKUP_KEY);
                    request = new DefaultImageRequest(displayName, lookupKey);
                }
                getPhotoLoader().loadDirectoryPhoto(view.getPhotoView(), photoUri, false, request);
            }
            /* end */
        }
//        getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId, false, null,
//                getPhotoLoader().getDefaultPhotoProviderForAccount(mContext, account));
        /*
        * @}
        */
    }

    public void setPhotoPosition(ContactListItemView.PhotoPosition photoPosition) {
        mPhotoPosition = photoPosition;
    }

    public ContactListItemView.PhotoPosition getPhotoPosition() {
        return mPhotoPosition;
    }

    public void setUseCallableUri(boolean useCallableUri) {
        mUseCallableUri = useCallableUri;
    }

    public boolean usesCallableUri() {
        return mUseCallableUri;
    }

    /**
     * Override base implementation to inject extended directories between local & remote
     * directories. This is done in the following steps:
     * 1. Call base implementation to add directories from the cursor.
     * 2. Iterate all base directories and establish the following information:
     *   a. The highest directory id so that we can assign unused id's to the extended directories.
     *   b. The index of the last non-remote directory. This is where we will insert extended
     *      directories.
     * 3. Iterate the extended directories and for each one, assign an ID and insert it in the
     *    proper location.
     */
    @Override
    public void changeDirectories(Cursor cursor) {
        super.changeDirectories(cursor);
        if (getDirectorySearchMode() == DirectoryListLoader.SEARCH_MODE_NONE) {
            return;
        }
        final int numExtendedDirectories = mExtendedDirectories.size();
        if (getPartitionCount() == cursor.getCount() + numExtendedDirectories) {
            // already added all directories;
            return;
        }
        //
        mFirstExtendedDirectoryId = Long.MAX_VALUE;
        if (numExtendedDirectories > 0) {
            // The Directory.LOCAL_INVISIBLE is not in the cursor but we can't reuse it's
            // "special" ID.
            long maxId = Directory.LOCAL_INVISIBLE;
            int insertIndex = 0;
            for (int i = 0, n = getPartitionCount(); i < n; i++) {
                final DirectoryPartition partition = (DirectoryPartition) getPartition(i);
                final long id = partition.getDirectoryId();
                if (id > maxId) {
                    maxId = id;
                }
                if (!isRemoteDirectory(id)) {
                    // assuming remote directories come after local, we will end up with the index
                    // where we should insert extended directories. This also works if there are no
                    // remote directories at all.
                    insertIndex = i + 1;
                }
            }
            // Extended directories ID's cannot collide with base directories
            mFirstExtendedDirectoryId = maxId + 1;
            for (int i = 0; i < numExtendedDirectories; i++) {
                final long id = mFirstExtendedDirectoryId + i;
                final DirectoryPartition directory = mExtendedDirectories.get(i);
                if (getPartitionByDirectoryId(id) == -1) {
                    addPartition(insertIndex, directory);
                    directory.setDirectoryId(id);
                }
            }
        }
    }

    protected Uri getContactUri(int partitionIndex, Cursor cursor,
            int contactIdColumn, int lookUpKeyColumn) {
        final DirectoryPartition directory = (DirectoryPartition) getPartition(partitionIndex);
        final long directoryId = directory.getDirectoryId();
        if (!isExtendedDirectory(directoryId)) {
            return super.getContactUri(partitionIndex, cursor, contactIdColumn, lookUpKeyColumn);
        }
        return Contacts.CONTENT_LOOKUP_URI.buildUpon()
                .appendPath(Constants.LOOKUP_URI_ENCODED)
                .appendQueryParameter(Directory.DISPLAY_NAME, directory.getLabel())
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(directoryId))
                .encodedFragment(cursor.getString(lookUpKeyColumn))
                .build();
    }

    /**
    * SPRD:
    * 
    * @{
    */
    protected void bindCheckbox(final ContactListItemView view, int position) {
        view.showCheckbox(isChecked(position));
    }

    /**
     * Builds a {@link Data#CONTENT_URI} for the given cursor position.
     *
     * @return Uri for the contact. may be null if the cursor is not ready.
     */
    public Uri getContactUri(int position) {
        Cursor cursor = ((Cursor)getItem(position));
        if (cursor != null) {
            int partitionIndex = getPartitionForPosition(position);
            return getContactUri(partitionIndex, cursor, PhoneQuery.CONTACT_ID, PhoneQuery.LOOKUP_KEY);
        } else {
            Log.w(TAG, "Cursor was null in getContactUri() call. Returning null instead.");
            return null;
        }
    }

    @Override
    public String getQuantityText(int count, int zeroResourceId, int pluralResourceId) {
        if (count == 0) {
            return getContext().getString(zeroResourceId);
        } else {
            String format = getContext().getResources().getQuantityText(pluralResourceId, count).toString();
            return String.format(format, count);
        }
    }
    /**
    * @}
    */
}
