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
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.location.CountryDetector;
import android.provider.BlockedNumberContract;
import android.test.mock.MockContentResolver;
import android.test.mock.MockContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class MyMockContext extends MockContext {
    public final Context realTestContext;

    @Mock
    CountryDetector countryDetector;

    MockContentResolver resolver;

    BlockedNumberProviderTestable provider;

    public MyMockContext(Context realTestContext) {
        this.realTestContext = realTestContext;
        MockitoAnnotations.initMocks(this);

        resolver = new MockContentResolver();

        provider = new BlockedNumberProviderTestable();

        final ProviderInfo info = new ProviderInfo();
        info.authority = BlockedNumberContract.AUTHORITY;
        provider.attachInfoForTesting(realTestContext, info);

        resolver.addProvider(BlockedNumberContract.AUTHORITY, provider);
    }

    @Override
    public Object getSystemService(String name) {
        switch (name) {
            case Context.COUNTRY_DETECTOR:
                return countryDetector;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public ContentResolver getContentResolver() {
        return resolver;
    }

    public void shutdown() {
        provider.shutdown();
    }
}

