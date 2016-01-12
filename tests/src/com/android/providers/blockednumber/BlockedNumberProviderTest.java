/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.providers.blockednumber;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import junit.framework.Assert;

/**
 m BlockedNumberProviderTests &&
 adb install -r \
 ${ANDROID_PRODUCT_OUT}/data/app/BlockedNumberProviderTests/BlockedNumberProviderTests.apk &&
 adb shell am instrument -e class com.android.providers.blockednumber.BlockedNumberProviderTest \
 -w com.android.providers.blockednumber.tests/android.support.test.runner.AndroidJUnitRunner
 */
public class BlockedNumberProviderTest extends AndroidTestCase {
    private Context mRealTestContext;
    private MyMockContext mMockContext;
    private ContentResolver mResolver;

    /** Whether the country detector thinks the device is in USA. */
    private boolean mInUSA;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mRealTestContext = getContext();
        mMockContext = new MyMockContext(mRealTestContext);
        mResolver = mMockContext.getContentResolver();
        mInUSA = "US".equals(Utils.getCurrentCountryIso(mRealTestContext));
    }

    @Override
    protected void tearDown() throws Exception {
        mMockContext.shutdown();

        super.tearDown();
    }

    private static ContentValues cv(Object... namesAndValues) {
        Assert.assertTrue((namesAndValues.length % 2) == 0);

        final ContentValues ret = new ContentValues();
        for (int i = 1; i < namesAndValues.length; i += 2) {
            final String name = namesAndValues[i - 1].toString();
            final Object value =  namesAndValues[i];
            if (value == null) {
                ret.putNull(name);
            } else if (value instanceof String) {
                ret.put(name, (String) value);
            } else if (value instanceof Integer) {
                ret.put(name, (Integer) value);
            } else if (value instanceof Long) {
                ret.put(name, (Long) value);
            } else {
                Assert.fail("Unsupported type: " + value.getClass().getSimpleName());
            }
        }
        return ret;
    }

    private void assertRowCount(int count, Uri uri) {
        try (Cursor c = mResolver.query(uri, null, null, null, null)) {
            assertEquals(count, c.getCount());
        }
    }

    public void testGetType() {
        assertEquals(BlockedNumbers.CONTENT_TYPE, mResolver.getType(
                BlockedNumbers.CONTENT_URI));

        assertEquals(BlockedNumbers.CONTENT_ITEM_TYPE, mResolver.getType(
                ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, 1)));

        assertNull(mResolver.getType(
                Uri.withAppendedPath(BlockedNumberContract.AUTHORITY_URI, "invalid")));
    }

    public void testInsert() {
        insertExpectingFailure(cv());
        insertExpectingFailure(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, null));
        insertExpectingFailure(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, ""));
        insertExpectingFailure(cv(BlockedNumbers.COLUMN_ID, 1));
        insertExpectingFailure(cv(BlockedNumbers.COLUMN_STRIPPED_NUMBER, 1));
        insertExpectingFailure(cv(BlockedNumbers.COLUMN_E164_NUMBER, "1"));

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1-2-3"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1-408-454-1111"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1-408-454-2222"));

        try {
            insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1-408-454-2222"));
            fail();
        } catch (SQLiteConstraintException expected) {
        }

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1-408-4542222"));

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "045-381-1111",
                BlockedNumbers.COLUMN_E164_NUMBER, "+81453811111"));

        assertRowCount(6, BlockedNumbers.CONTENT_URI);

        // TODO Check the table content.
    }

    private Uri insert(ContentValues cv) {
        final Uri uri = mResolver.insert(BlockedNumbers.CONTENT_URI, cv);
        assertNotNull(uri);

        // Make sure the URI exists.
        try (Cursor c = mResolver.query(uri, null, null, null, null)) {
            assertEquals(1, c.getCount());
        }
        return uri;
    }

    private void insertExpectingFailure(ContentValues cv) {
        try {
            mResolver.insert(
                    BlockedNumbers.CONTENT_URI, cv());
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testDelete() {
        // Prepare test data
        Uri u1 = insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
        Uri u2 = insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1-2-3"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1-408-454-1111"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1-408-454-2222"));

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "045-381-1111",
                BlockedNumbers.COLUMN_E164_NUMBER, "12345"));

        assertRowCount(5, BlockedNumbers.CONTENT_URI);

        // Delete and check the # of remaining rows.

        mResolver.delete(u1, null, null);
        assertRowCount(4, BlockedNumbers.CONTENT_URI);

        try {
            mResolver.delete(u2, "1=1", null);
            fail();
        } catch (IllegalArgumentException expected) {
            MoreAsserts.assertContainsRegex("selection must be null", expected.getMessage());
        }

        mResolver.delete(u2, null, null);
        assertRowCount(3, BlockedNumbers.CONTENT_URI);

        mResolver.delete(BlockedNumbers.CONTENT_URI,
                BlockedNumbers.COLUMN_E164_NUMBER + "=?",
                new String[]{"12345"});
        assertRowCount(2, BlockedNumbers.CONTENT_URI);

        // SQL injection should be detected.
        try {
            mResolver.delete(BlockedNumbers.CONTENT_URI, "; DROP TABLE blocked; ", null);
            fail();
        } catch (SQLiteException expected) {
        }
        assertRowCount(2, BlockedNumbers.CONTENT_URI);

        mResolver.delete(BlockedNumbers.CONTENT_URI, null, null);
        assertRowCount(0, BlockedNumbers.CONTENT_URI);
    }

    public void testUpdate() {
        try {
            mResolver.update(BlockedNumbers.CONTENT_URI, cv(),
                    /* selection =*/ null, /* args =*/ null);
            fail();
        } catch (UnsupportedOperationException expected) {
            MoreAsserts.assertContainsRegex("Update is not supported", expected.getMessage());
        }
    }

    public void testIsBlocked() {
        // Prepare test data
        Uri u1 = insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
        Uri u2 = insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1.2-3"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1-500-454-1111"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1-500-454-2222"));

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "045-111-2222",
                BlockedNumbers.COLUMN_E164_NUMBER, "+81-45-111-2222"));

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "abc.def@gmail.com"));

        // Check
        assertIsBlocked(true, "123");
        assertIsBlocked(false, "1234");
        assertIsBlocked(true, "+81451112222");
        assertIsBlocked(true, "+81 45 111 2222");
        assertIsBlocked(true, "045 111 2222");

        if (mInUSA) {
            // Probably won't work outside of the +1 region.
            assertIsBlocked(true, "500-454 1111");
            assertIsBlocked(true, "500-454 2222");
        }
        assertIsBlocked(true, "+1 500-454 1111");
        assertIsBlocked(true, "1 500-454 1111");

        assertIsBlocked(true, "abc.def@gmail.com");
        assertIsBlocked(false, "abc.def@gmail.co");
        assertIsBlocked(false, "bc.def@gmail.com");
        assertIsBlocked(false, "abcdef@gmail.com");
    }

    private void assertIsBlocked(boolean expected, String phoneNumber) {
        assertEquals(expected, BlockedNumberContract.isBlocked(mMockContext, phoneNumber));
    }
}
