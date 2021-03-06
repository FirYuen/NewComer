/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts.common.model.account;

import android.content.Context;
import android.util.Log;

import com.android.contacts.common.R;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.test.NeededForTesting;
import android.os.SystemProperties;

public class FallbackAccountType extends BaseAccountType {
    private static final String TAG = "FallbackAccountType";

    /* SPRD:
     * Because of dialer was split from Contacts, the resPackageName MUST
     * be used into AccountType.syncAdapterPackageName to ensure your resources.
     * You can see AccountType.getResourceText and AccountType.getDrawableIcon.
     * The id of R.string.label_phone may be different from com.android.dialer
     * bettween com.android.contacts. So it may cause the string resouce not be
     * correct or ResouceNotFoundException because different resIds but the
     * same name. The resPackageName must be given, but the PhoneAccountType was
     * brought from us and extends of FallbackAccountType, so given higher
     * accessibility to the contructor of the FallbackAccountType.
     * Change private -> protected
     */
    protected FallbackAccountType(Context context, String resPackageName) {
        this.accountType = null;
        this.dataSet = null;
        this.titleRes = R.string.account_phone;
        this.iconRes = R.mipmap.ic_launcher_contacts;

        // Note those are only set for unit tests.
        this.resourcePackageName = resPackageName;
        this.syncAdapterPackageName = resPackageName;

        try {
            addDataKindStructuredName(context);
            addDataKindDisplayName(context);
            addDataKindPhoneticName(context);
            addDataKindNickname(context);
            addDataKindPhone(context);
            addDataKindEmail(context);
            addDataKindStructuredPostal(context);
            addDataKindIm(context);
            addDataKindOrganization(context);
            addDataKindPhoto(context);
            addDataKindNote(context);
            addDataKindWebsite(context);
            /*
             * SPRD:Bug414101 Remove internet call item when cmcc and cucc operators.
             * Orig:addDataKindSipAddress(context);
             * @{
             */
            if (SystemProperties.get("ro.operator").isEmpty() ||
                    (!SystemProperties.get("ro.operator").equals("cmcc") &&
                    !SystemProperties.get("ro.operator").equals("cucc"))) {
                addDataKindSipAddress(context);
            }
            /*
             * @}
             */


            mIsInitialized = true;
        } catch (DefinitionException e) {
            Log.e(TAG, "Problem building account type", e);
        }
    }

    public FallbackAccountType(Context context) {
        this(context, null);
    }

    /**
     * Used to compare with an {@link ExternalAccountType} built from a test contacts.xml.
     * In order to build {@link DataKind}s with the same resource package name,
     * {@code resPackageName} is injectable.
     */
    @NeededForTesting
    static AccountType createWithPackageNameForTest(Context context, String resPackageName) {
        return new FallbackAccountType(context, resPackageName);
    }

    @Override
    public boolean areContactsWritable() {
        return true;
    }
}
