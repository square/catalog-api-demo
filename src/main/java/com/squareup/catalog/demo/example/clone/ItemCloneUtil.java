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

import com.squareup.catalog.demo.util.CatalogObjects;
import com.squareup.connect.models.CatalogItem;
import com.squareup.connect.models.CatalogItemVariation;
import com.squareup.connect.models.CatalogObject;
import com.squareup.connect.models.ListCatalogResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Utility methods used to clone a {@link CatalogItem}.
 *
 * If an item in the source account has the same name and category as an item in the target account,
 * then the item in the source account is not cloned. In that case, the item variations from the
 * item in the source account will be added to the item in the target account if they do not match
 * any of the existing item variations.
 */
class ItemCloneUtil extends CatalogObjectCloneUtil<CatalogItem> {

  private final HashMap<String, CatalogObject> categorySourceIdToTargetObject;

  ItemCloneUtil(boolean presentAtAllLocationsByDefault,
      HashMap<String, CatalogObject> categorySourceIdToTargetObject) {
    super(CatalogObject.TypeEnum.ITEM, presentAtAllLocationsByDefault);
    this.categorySourceIdToTargetObject = categorySourceIdToTargetObject;
  }

  @Override CatalogItem getCatalogData(CatalogObject catalogObject) {
    return catalogObject.getItemData();
  }

  @Override public String encodeCatalogData(CatalogItem item, boolean fromSourceAccount) {
    String categoryId = item.getCategoryId();
    if (fromSourceAccount && categoryId != null) {
      // Convert the source category ID to a target category ID.
      categoryId = categorySourceIdToTargetObject.get(categoryId).getId();
    }
    return item.getName() + ":::" + categoryId;
  }

  @Override List<CatalogObject> getCatalogObjectsFromListResponse(
      ListCatalogResponse listResponse) {
    List<CatalogObject> allItems = super.getCatalogObjectsFromListResponse(listResponse);
    List<CatalogObject> regularItems = new ArrayList<>(allItems.size());
    for (CatalogObject item : allItems) {
      if (CatalogObjects.isRegularItem(item.getItemData())) {
        regularItems.add(item);
      }
    }
    return regularItems;
  }

  @Override
  void removeSourceAccountMetaData(CatalogObject catalogObject) {
    super.removeSourceAccountMetaData(catalogObject);

    // Also remove meta data from the embedded item variations.
    CatalogItem item = catalogObject.getItemData();
    for (CatalogObject variation : item.getVariations()) {
      removeSourceAccountMetaDataFromNestedCatalogObject(catalogObject, variation);
      variation.getItemVariationData().itemId(catalogObject.getId());

      // Remove location-specific price overrides.
      variation.getItemVariationData().locationOverrides(new ArrayList<>());
    }

    // TODO: Don't do this when we support tax and modifier list memberships
    item.taxIds(new ArrayList<>());
    item.modifierListInfo(new ArrayList<>());
  }

  @Override
  CatalogObject mergeSourceCatalogObjectIntoTarget(CatalogObject sourceCatalogObject,
      CatalogObject targetCatalogObject) {
    // Create a HashSet of the variations in the target item.
    CatalogItem targetItem = targetCatalogObject.getItemData();
    HashSet<String> targetVariations = getEncodedVariations(targetItem);

    // Add variations from the source item that are not already in the target item.
    boolean addedVariationToTarget = false;
    CatalogItem sourceItem = sourceCatalogObject.getItemData();
    for (CatalogObject variation : sourceItem.getVariations()) {
      String encodedVariation = encodeVariation(variation.getItemVariationData());
      if (!targetVariations.contains(encodedVariation)) {
        // Prepare the catalog object for the target account.
        removeSourceAccountMetaDataFromNestedCatalogObject(targetCatalogObject, variation);
        variation.getItemVariationData().itemId(targetCatalogObject.getId());

        // Remove location-specific price overrides.
        variation.getItemVariationData().locationOverrides(new ArrayList<>());

        // Add the variation to the target item.
        targetItem.addVariationsItem(variation);

        // Mark that we've updated the target modifier list.
        addedVariationToTarget = true;
      }
    }

    // Return the modifier target CatalogObject if it changed.
    return addedVariationToTarget ? targetCatalogObject : null;
  }

  /**
   * Returns a set of all encoded variations in the item.
   */
  private HashSet<String> getEncodedVariations(CatalogItem item) {
    HashSet<String> encodedItems = new HashSet<>();
    for (CatalogObject variation : item.getVariations()) {
      encodedItems.add(encodeVariation(variation.getItemVariationData()));
    }
    return encodedItems;
  }

  /**
   * Encodes an item variation based on the name and price.
   */
  private String encodeVariation(CatalogItemVariation variation) {
    return variation.getName() + ":::" + amountOrNull(variation.getPriceMoney());
  }
}
