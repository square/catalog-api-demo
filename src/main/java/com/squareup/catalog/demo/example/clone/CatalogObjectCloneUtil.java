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

import com.squareup.connect.models.CatalogObject;
import com.squareup.connect.models.ListCatalogResponse;
import com.squareup.connect.models.Money;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Defines utility methods used when cloning a CatalogObject from one merchant account to another.
 *
 * @param <T> The catalog object data type
 */
abstract class CatalogObjectCloneUtil<T> {

  final CatalogObject.TypeEnum type;
  private final boolean presentAtAllLocationsByDefault;

  /**
   * @param type the type of {@link CatalogObject} to clone
   * @param presentAtAllLocationsByDefault if true, this object should be enabled at all locations
   * in the target account
   */
  CatalogObjectCloneUtil(CatalogObject.TypeEnum type, boolean presentAtAllLocationsByDefault) {
    this.type = type;
    this.presentAtAllLocationsByDefault = presentAtAllLocationsByDefault;
  }

  /**
   * Returns the type specific data from a catalog object.
   */
  abstract T getCatalogData(CatalogObject catalogObject);

  /**
   * Encodes a subset of fields from the {@link CatalogObject} into a string, allowing
   * CatalogObjects to be compared between merchant accounts for likeness. If the encoded string of
   * a {@link CatalogObject} in the source account matches the encoded string of a {@link
   * CatalogObject} in the target account, then the object is not cloned.
   *
   * @param fromSourceAccount true if the object is from the source account, false if from the
   * target account
   */
  final String encodeCatalogObject(CatalogObject catalogObject, boolean fromSourceAccount) {
    T catalogData = getCatalogData(catalogObject);
    return encodeCatalogData(catalogData, fromSourceAccount);
  }

  /**
   * Encodes a subset of fields from the {@link CatalogObject} into a string, allowing
   * CatalogObjects to be compared between merchant accounts for likeness. If the encoded string of
   * a {@link CatalogObject} in the source account matches the encoded string of a {@link
   * CatalogObject} in the target account, then the object is not cloned.
   *
   * @param catalogData the data from the {@link CatalogObject}.
   * @param fromSourceAccount true if the object is from the source account, false if from the
   * target account
   */
  abstract String encodeCatalogData(T catalogData, boolean fromSourceAccount);

  /**
   * Retrieve the catalog objects from the {@link ListCatalogResponse}. Subclasses can filter out
   * unwanted objects.
   *
   * @param listResponse the response
   * @return a filtered down list of {@link CatalogObject}
   */
  List<CatalogObject> getCatalogObjectsFromListResponse(ListCatalogResponse listResponse) {
    // By default, assume we want all objects in the response
    return listResponse.getObjects();
  }

  /**
   * Encodes a {@link Money} object to a string.
   *
   * @param money the {@link Money} object to encode
   * @return the amount as a string, or null if the {@link Money} object is null
   */
  String amountOrNull(Money money) {
    return (money == null) ? "null" : money.getAmount().toString();
  }

  /**
   * Removes meta data from the {@link CatalogObject} that only applies to the source account, such
   * as location IDs and version.
   *
   * @param catalogObject the {@link CatalogObject} to modify
   */
  void removeSourceAccountMetaData(CatalogObject catalogObject) {
    removeSourceAccountMetaDataImpl(catalogObject);
  }

  /**
   * Removes meta data from a {@link CatalogObject} nested inside another. Also matches locations
   * with the parent {@link CatalogObject}.
   *
   * @param parent the parent {@link CatalogObject}
   * @param child the nested {@link CatalogObject}
   */
  void removeSourceAccountMetaDataFromNestedCatalogObject(CatalogObject parent,
      CatalogObject child) {
    removeSourceAccountMetaDataImpl(child);

    // Make the locations of the nested object match the locations of the parent.
    child.setPresentAtAllLocations(parent.getPresentAtAllLocations());
    child.setPresentAtLocationIds(parent.getPresentAtLocationIds());
    child.setAbsentAtLocationIds(parent.getAbsentAtLocationIds());
  }

  /**
   * Removes meta data from the {@link CatalogObject} that only applies to the source account, such
   * as location IDs and version.
   *
   * @param catalogObject the {@link CatalogObject} to modify
   */
  private void removeSourceAccountMetaDataImpl(CatalogObject catalogObject) {
    // We need to set a temporary client generated ID.
    catalogObject.setId("#" + UUID.randomUUID());

    // The V1 IDs from the source account do not apply to the target account.
    catalogObject.setCatalogV1Ids(Collections.emptyList());

    // The location IDs from the source account do not apply to the target account.
    catalogObject.setPresentAtLocationIds(Collections.emptyList());
    catalogObject.setAbsentAtLocationIds(Collections.emptyList());

    // The server will assign a version.
    catalogObject.setVersion(null);

    // The server will update the timestamp.
    catalogObject.setUpdatedAt(null);

    if (presentAtAllLocationsByDefault) {
      catalogObject.setPresentAtAllLocations(presentAtAllLocationsByDefault);
    }
  }

  /**
   * Merges data from a {@link CatalogObject} from the source account into a {@link CatalogObject}
   * from the target account when they have the same encoded string. This method can be used to
   * merge two objects that have the same name, but slightly different attributes. For example, if a
   * modifier list in the source account has the same name as a modifier list in the target account,
   * this method is used to copy the modifiers from the source account to the target account.
   *
   * @param sourceCatalogObject the {@link CatalogObject} from the source account
   * @param targetCatalogObject the {@link CatalogObject} from the target account
   * @return the modifier target {@link CatalogObject}, or null if nothing changed
   */
  CatalogObject mergeSourceCatalogObjectIntoTarget(CatalogObject sourceCatalogObject,
      CatalogObject targetCatalogObject) {
    // Default implementation assumes that the objects do not need to be merged.
    return null;
  }
}
