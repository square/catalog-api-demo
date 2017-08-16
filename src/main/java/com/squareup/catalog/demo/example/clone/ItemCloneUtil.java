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
import com.squareup.connect.models.CatalogItemModifierListInfo;
import com.squareup.connect.models.CatalogItemVariation;
import com.squareup.connect.models.CatalogObject;
import com.squareup.connect.models.ListCatalogResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Utility methods used to clone a {@link CatalogItem}.
 *
 * If an item in the source account has the same name and category as an item in the target account,
 * then the item in the source account is not cloned. In that case, the item variations from the
 * item in the source account will be added to the item in the target account if they do not match
 * any of the existing item variations.
 */
class ItemCloneUtil extends CatalogObjectCloneUtil<CatalogItem> {

  /**
   * If true, modifier lists that applied to items in the source account should apply to items in
   * the target account.
   */
  private final boolean includeAppliedModifierLists;

  /**
   * If true, taxes that applied to items in the source account should apply to items in the target
   * account.
   */
  private final boolean includeAppliedTaxes;

  /**
   * A mapping of the ID of catalog objects in the source account to the same catalog object in the
   * target account.
   */
  private final HashMap<String, CatalogObject> sourceIdToTargetObject;

  /**
   * Constructs a new {@link ItemCloneUtil}.
   *
   * @param presentAtAllLocationsByDefault if true, items should be enabled at all locations in the
   * target account
   * @param sourceIdToTargetObject a mapping of the ID of catalog objects in the source account to
   * the same catalog object in the target account
   */
  ItemCloneUtil(boolean presentAtAllLocationsByDefault, boolean includeAppliedModifierLists,
      boolean includeAppliedTaxes, HashMap<String, CatalogObject> sourceIdToTargetObject) {
    super(CatalogObject.TypeEnum.ITEM, presentAtAllLocationsByDefault);
    this.includeAppliedModifierLists = includeAppliedModifierLists;
    this.includeAppliedTaxes = includeAppliedTaxes;
    this.sourceIdToTargetObject = sourceIdToTargetObject;
  }

  @Override CatalogItem getCatalogData(CatalogObject catalogObject) {
    return catalogObject.getItemData();
  }

  @Override public String encodeCatalogData(CatalogItem item, boolean fromSourceAccount) {
    String categoryId = item.getCategoryId();
    if (fromSourceAccount) {
      // Convert the source category ID to a target category ID.
      categoryId = getTargetCategoryIdBySourceId(categoryId);
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
  Map<String, String> removeSourceAccountMetaData(CatalogObject catalogObject) {
    Map<String, String> sourceIdToClientId = super.removeSourceAccountMetaData(catalogObject);

    // Update the category ID to refer to the target category ID.
    CatalogItem item = catalogObject.getItemData();
    String sourceCategoryId = item.getCategoryId();
    String targetCategoryId = getTargetCategoryIdBySourceId(item.getCategoryId());
    item.categoryId(targetCategoryId);

    // Update the tax IDs to refer to the target tax IDs.
    // TODO: DO the same for merge
    List<String> sourceTaxIds = item.getTaxIds();
    item.taxIds(new ArrayList<>());
    if (includeAppliedTaxes) {
      for (String sourceTaxId : sourceTaxIds) {
        String targetTaxId = getTargetTaxIdBySourceId(sourceTaxId);
        item.addTaxIdsItem(targetTaxId);
      }
    }

    // TODO: DO the same for merge
    // TODO: Handle Modifiers
    if (includeAppliedModifierLists) {
      // Update the modifier list IDs to refer to the target modifier list IDs.
      for (CatalogItemModifierListInfo modifierListInfo : item.getModifierListInfo()) {
        String sourceModifierListId = modifierListInfo.getModifierListId();
        String targetModifierListId = getTargetModifierListIdBySourceId(sourceModifierListId);
        modifierListInfo.modifierListId(targetModifierListId);
      }
    } else {
      // If we didn't clone modifier lists, then clear them out from the item.
      item.modifierListInfo(new ArrayList<>());
    }

    // Also remove meta data from the embedded item variations.
    for (CatalogObject variation : item.getVariations()) {
      String variationSourceId = variation.getId();
      String variationClientId =
          removeSourceAccountMetaDataFromNestedCatalogObject(catalogObject, variation);
      sourceIdToClientId.put(variationSourceId, variationClientId);
      variation.getItemVariationData().itemId(catalogObject.getId());

      // Remove location-specific price overrides.
      variation.getItemVariationData().locationOverrides(new ArrayList<>());
    }

    return sourceIdToClientId;
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

  /**
   * Get the ID of the category in the target account given the ID in the source account.
   *
   * @param sourceCategoryIdOrNull the ID of the category in the source account, or null
   * @return the ID of the category in the target account, or null if sourceCategoryIdOrNull is null
   */
  private String getTargetCategoryIdBySourceId(String sourceCategoryIdOrNull) {
    return sourceCategoryIdOrNull == null ? null
        : sourceIdToTargetObject.get(sourceCategoryIdOrNull).getId();
  }

  /**
   * Get the ID of the modifier list in the target account given the ID in the source account.
   *
   * @param sourceModifierListIdOrNull the ID of the modifier list in the source account, or null
   * @return the ID of the modifier list in the target account, or null if
   * sourceModifierListIdOrNull is null
   */
  private String getTargetModifierListIdBySourceId(String sourceModifierListIdOrNull) {
    return sourceModifierListIdOrNull == null ? null
        : sourceIdToTargetObject.get(sourceModifierListIdOrNull).getId();
  }

  /**
   * Get the ID of the category in the target account given the ID in the source account.
   *
   * @param sourceTaxIdOrNull the ID of the tax in the source account, or null
   * @return the ID of the tax in the target account, or null if sourceTaxIdOrNull is null
   */
  private String getTargetTaxIdBySourceId(String sourceTaxIdOrNull) {
    return sourceTaxIdOrNull == null ? null
        : sourceIdToTargetObject.get(sourceTaxIdOrNull).getId();
  }
}
