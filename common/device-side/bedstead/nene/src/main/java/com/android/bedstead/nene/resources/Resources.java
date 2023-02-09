/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.bedstead.nene.resources;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;

/**
 * TestApi to access resources
 */
@Experimental
public final class Resources extends ResourcesWrapper {

    public static final Resources sInstance = new Resources();

    private Resources() {
        super(TestApis.context().instrumentedContext().getResources());
    }

    /**
     * Get reference to system level resources
     */
    public ResourcesWrapper system() {
        return new ResourcesWrapper(android.content.res.Resources.getSystem());
    }

}