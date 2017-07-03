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

import com.squareup.connect.models.CatalogItem;
import com.squareup.connect.models.CatalogObject;
import com.squareup.connect.models.ListCatalogResponse;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;

import static com.squareup.connect.models.CatalogItem.ProductTypeEnum.APPOINTMENTS_SERVICE;
import static com.squareup.connect.models.CatalogItem.ProductTypeEnum.GIFT_CARD;
import static com.squareup.connect.models.CatalogItem.ProductTypeEnum.REGULAR;
import static org.fest.assertions.Assertions.assertThat;

public class ItemCloneUtilTest {

  private HashMap<String, CatalogObject> categorySourceIdToTargetObject = new HashMap<>();
  private ItemCloneUtil cloneUtil;

  @Before public void setUp() {
    this.cloneUtil = new ItemCloneUtil(true, categorySourceIdToTargetObject);
  }

  @Test public void encodeCatalogData_fromSourceAccount() {
    categorySourceIdToTargetObject.put("sourceCategoryId",
        new CatalogObject().id("targetCategoryId"));
    CatalogItem item = new CatalogItem()
        .name("name")
        .categoryId("sourceCategoryId");
    assertThat(cloneUtil.encodeCatalogData(item, true)).isEqualTo("name:::targetCategoryId");
  }

  @Test public void encodeCatalogData_fromTargetAccount() {
    CatalogItem item = new CatalogItem()
        .name("name")
        .categoryId("targetCategoryId");
    assertThat(cloneUtil.encodeCatalogData(item, false)).isEqualTo("name:::targetCategoryId");
  }

  @Test public void encodeCatalogData_nullCategoryId() {
    CatalogItem item = new CatalogItem()
        .name("name")
        .categoryId(null);
    assertThat(cloneUtil.encodeCatalogData(item, true)).isEqualTo("name:::null");
  }

  @Test public void getCatalogObjectsFromListResponse_filtersOutNonRegularItems() {
    CatalogObject itemRegular = new CatalogObject() //
        .itemData(new CatalogItem()
            .productType(REGULAR));
    CatalogObject itemNull = new CatalogObject() //
        .itemData(new CatalogItem()
            .productType(null));
    CatalogObject itemGiftCard = new CatalogObject() //
        .itemData(new CatalogItem()
            .productType(GIFT_CARD));
    CatalogObject itemService = new CatalogObject() //
        .itemData(new CatalogItem()
            .productType(APPOINTMENTS_SERVICE));
    ListCatalogResponse response = new ListCatalogResponse() //
        .addObjectsItem(itemRegular)
        .addObjectsItem(itemGiftCard)
        .addObjectsItem(itemNull)
        .addObjectsItem(itemService);
    assertThat(cloneUtil.getCatalogObjectsFromListResponse(response)).containsExactly(itemRegular,
        itemNull);
  }
}
