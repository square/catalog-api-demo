/*
 * Copyright 2017 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.catalog.demo.example.clone;

import com.squareup.connect.models.CatalogCategory;
import com.squareup.connect.models.CatalogObject;

import static com.squareup.connect.models.CatalogObject.TypeEnum.CATEGORY;

/**
 * Utility methods used to clone a {@link CatalogCategory}.
 *
 * If a category in the source account has the same name as a category in the target account,
 * then the category in the source account is not cloned.
 */
class CategoryCloneUtil extends CatalogObjectCloneUtil<CatalogCategory> {

  CategoryCloneUtil() {
    super(CATEGORY, true);
  }

  @Override CatalogCategory getCatalogData(CatalogObject catalogObject) {
    return catalogObject.getCategoryData();
  }

  @Override String encodeCatalogData(CatalogCategory category, boolean fromSourceAccount) {
    return category.getName();
  }
}
