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

import com.squareup.catalog.demo.Logger;
import com.squareup.catalog.demo.example.Example;
import com.squareup.connect.ApiClient;
import com.squareup.connect.ApiException;
import com.squareup.connect.api.CatalogApi;
import com.squareup.connect.api.LocationsApi;
import com.squareup.connect.auth.OAuth;
import com.squareup.connect.models.BatchUpsertCatalogObjectsRequest;
import com.squareup.connect.models.BatchUpsertCatalogObjectsResponse;
import com.squareup.connect.models.CatalogIdMapping;
import com.squareup.connect.models.CatalogObject;
import com.squareup.connect.models.CatalogObjectBatch;
import com.squareup.connect.models.ListCatalogResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.squareup.catalog.demo.util.Prompts.promptUserInput;
import static com.squareup.catalog.demo.util.Prompts.promptUserInputYesNo;

/**
 * This example clones catalog objects from one merchant account to another. The accessToken
 * specified when the script is executed is assumed to be the source account from which catalog
 * objects are copied.
 *
 * NOTE: This example is lossy and does not guarantee that cloned CatalogObjects will include all
 * attributes of the source account. The example will attempt to merge CatalogObjects from the
 * source account into similar CatalogObjects in the target account. For example, if an item in the
 * source account has the same name and category as an item in the target account, it will be
 * merged, even if the item in the target account has a different description.
 */
public class CloneCatalogExample extends Example {

  public CloneCatalogExample(Logger logger) {
    super("clone_catalog", "Clones catalog objects from one merchant account to another.", logger);
  }

  @Override
  public void execute(CatalogApi sourceCatalogApi, LocationsApi locationsApi) throws ApiException {
    // Prompt the user for configuration info.
    Config config = promptForConfigurationOptions();

    // Create a CatalogApi to access the target account.
    CatalogApi targetCatalogApi =
        createTargetCatalogApi(sourceCatalogApi, config.targetAccessToken);

    try {
      // Clone discounts from source to target account.
      if (config.discountCloneUtil != null) {
        cloneCatalogObjectType(sourceCatalogApi, targetCatalogApi, config.discountCloneUtil);
      }

      // Clone modifier lists from source to target account.
      HashMap<String, CatalogObject> modifierListSourceIdToTargetObject = null;
      if (config.modifierListCloneUtil != null) {
        modifierListSourceIdToTargetObject =
            cloneCatalogObjectType(sourceCatalogApi, targetCatalogApi,
                config.modifierListCloneUtil);
      }

      // Clone taxes from source to target account.
      HashMap<String, CatalogObject> taxSourceIdToTargetObject = null;
      if (config.taxCloneUtil != null) {
        taxSourceIdToTargetObject =
            cloneCatalogObjectType(sourceCatalogApi, targetCatalogApi, config.taxCloneUtil);
      }

      // Clone items and categories from source to target account.
      if (config.categoryCloneUtil != null) {
        HashMap<String, CatalogObject> categorySourceIdToTargetObject =
            cloneCatalogObjectType(sourceCatalogApi, targetCatalogApi, config.categoryCloneUtil);

        // The ItemCloneUtil uses the sourceIdToTargetCatalogObject mappings from the other catalog
        // objects that were cloned.
        ItemCloneUtil itemCloneUtil = new ItemCloneUtil(config.itemsPresentAtAllLocationsByDefault,
            categorySourceIdToTargetObject);
        cloneCatalogObjectType(sourceCatalogApi, targetCatalogApi, itemCloneUtil);
      }
    } catch (CloneCatalogException e) {
      if (e.getMessage() != null) {
        logger.error(e.getMessage());
      }
    }
  }

  /**
   * Prompt the user for configuration options required to clone the account.
   *
   * @return the configuration to apply when cloning accounts
   */
  private Config promptForConfigurationOptions() {
    Config config = new Config();
    config.targetAccessToken = promptUserInput("Enter access token of target account: ").trim();

    if (promptUserInputYesNo("Clone all discounts (y/n)? ", logger)) {
      boolean presentAtAllLocationsByDefault =
          promptUserInputYesNo("  Make cloned discounts available at all locations (y/n)? ",
              logger);
      config.discountCloneUtil = new DiscountCloneUtil(presentAtAllLocationsByDefault);
    }

    if (promptUserInputYesNo("Clone all modifier lists (y/n)? ", logger)) {
      logger.info("  Note: Modifier lists will be enabled at all locations");
      config.modifierListCloneUtil = new ModifierListCloneUtil();
    }

    if (promptUserInputYesNo("Clone all taxes (y/n)? ", logger)) {
      config.taxCloneUtil = new TaxCloneUtil();
    }

    if (promptUserInputYesNo("Clone all items and categories (y/n)? ", logger)) {
      config.categoryCloneUtil = new CategoryCloneUtil();
      config.itemsPresentAtAllLocationsByDefault =
          promptUserInputYesNo("  Make cloned items available at all locations (y/n)? ", logger);
    }

    return config;
  }

  /**
   * Create a CatalogApi instance to access the target account.
   *
   * @param sourceCatalogApi the {@link CatalogApi} instance used to access the source account
   * @param targetAccessToken the access token of the target account
   * @return a {@link CatalogApi} for the target account
   */
  private CatalogApi createTargetCatalogApi(CatalogApi sourceCatalogApi, String targetAccessToken) {
    // Create a new instance of the ApiClient.
    ApiClient targetApiClient = new ApiClient();
    targetApiClient.setBasePath(sourceCatalogApi.getApiClient().getBasePath());

    // Set the auth token of the ApiClient to the target account.
    OAuth oauth2 = (OAuth) targetApiClient.getAuthentication("oauth2");
    oauth2.setAccessToken(targetAccessToken);

    // Return a new CatalogApi instance that uses the new ApiClient.
    return new CatalogApi(targetApiClient);
  }

  /**
   * Clones all catalog objects of a single type. The basic approach for cloning catalog objects is
   * the same for all types.
   *
   * 1) Fetch all the catalog objects of the specified type from the target account
   *
   * 2) For each object in the target account, generate a unique encoded string based on certain
   * field values. For example, for taxes we compare name, percentage, and inclusion type.
   *
   * 3) Store all unique encoded strings in a HashSet
   *
   * 4) Fetch all the catalog objects of the specified type from the source account
   *
   * 5) For each object in the source account, generate a unique encoded strings using the same
   * method and check to see if it exists in the HashSet. If it does not, then add it to the target
   * account.
   *
   * @param sourceCatalogApi the API used to access the catalog of the source account
   * @param targetCatalogApi the API used to access the catalog of the target account
   * @param cloneUtil the clone utility methods for the specified catalog object type
   * @return a mapping of cloned CatalogObjects in the target account keyed by the object ID in the
   * source account
   */
  private HashMap<String, CatalogObject> cloneCatalogObjectType(CatalogApi sourceCatalogApi,
      CatalogApi targetCatalogApi,
      CatalogObjectCloneUtil<?> cloneUtil)
      throws ApiException, CloneCatalogException {
    logger.info("\nCloning " + cloneUtil.type.toString());

    // Retrieve catalog objects from the target account.
    HashMap<String, CatalogObject> targetCatalogObjects =
        retrieveTargetCatalogObjects(targetCatalogApi, cloneUtil);

    // Retrieve catalog objects from the source account.
    logger.info("  Retrieving " + cloneUtil.type.toString() + " from source account");
    HashMap<String, CatalogObject> sourceIdToTargetObject = new HashMap<>();
    String cursor = null;
    do {
      int clonedCatalogObjectCount = 0;
      int mergedCatalogObjectCount = 0;
      List<CatalogObject> catalogObjectsToUpsert = new ArrayList<>();

      // A mapping of the ID of the source CatalogObject to the client generated ID assigned to the
      // CatalogObject in the target account.
      HashMap<String, String> sourceIdToClientId = new HashMap<>();

      // Retrieve a page of catalog objects from the source account.
      ListCatalogResponse listResponse =
          sourceCatalogApi.listCatalog(cursor, cloneUtil.type.toString());
      if (checkAndLogErrors(listResponse.getErrors())) {
        throw new CloneCatalogException();
      }

      // Log and return if no objects are found. Note that we do not get the filtered list of
      // objects from getCatalogObjectsFromListResponse() yet because if first page is completely
      // filtered out, we want to get additional pages.
      if (listResponse.getObjects().isEmpty() && cursor == null) {
        logger.info(
            "    No "
                + cloneUtil.type.toString().toLowerCase(Locale.US)
                + " found in source account.");
        return sourceIdToTargetObject;
      }

      // Iterate over the catalog objects to clone.
      List<CatalogObject> catalogObjects =
          cloneUtil.getCatalogObjectsFromListResponse(listResponse);
      for (CatalogObject sourceCatalogObject : catalogObjects) {
        String encodedString = cloneUtil.encodeCatalogObject(sourceCatalogObject, true);

        // Check if a similar catalog object already exists in the target account.
        CatalogObject targetCatalogObject = targetCatalogObjects.get(encodedString);
        if (targetCatalogObject == null) {
          // Remember the source ID.
          String sourceId = sourceCatalogObject.getId();

          // Clear source account specific metadata, including the source ID.
          cloneUtil.removeSourceAccountMetaData(sourceCatalogObject);

          // Save the mapping of the source object ID to the new client generated ID.
          sourceIdToClientId.put(sourceId, sourceCatalogObject.getId());

          // Add the object to the list of objects to insert into the target catalog.
          catalogObjectsToUpsert.add(sourceCatalogObject);
          clonedCatalogObjectCount++;
        } else {
          // If a similar CatalogObject already exists in the target account, attempt to merge the
          // source CatalogObject into the existing target CatalogObject.
          CatalogObject modifiedTargetCatalogObject =
              cloneUtil.mergeSourceCatalogObjectIntoTarget(sourceCatalogObject,
                  targetCatalogObject);

          // If something changed in the target catalog object, upsert it into the target account.
          if (modifiedTargetCatalogObject != null) {
            catalogObjectsToUpsert.add(modifiedTargetCatalogObject);
            mergedCatalogObjectCount++;
          }

          // Save the mapping of the source object ID to the target object.
          sourceIdToTargetObject.put(sourceCatalogObject.getId(), targetCatalogObject);
        }
      }

      // Upsert the catalog objects into the target account.
      if (catalogObjectsToUpsert.size() > 0) {
        BatchUpsertCatalogObjectsResponse batchUpsertResponse =
            upsertCatalogObjectsIntoTargetAccount(targetCatalogApi, catalogObjectsToUpsert);
        mapSourceIdsToInsertedCatalogObject(batchUpsertResponse, sourceIdToClientId,
            sourceIdToTargetObject);
      }

      // Log the number of objects cloned.
      logger.info("    Retrieved "
          + listResponse.getObjects().size()
          + " objects ("
          + clonedCatalogObjectCount
          + " cloned, "
          + mergedCatalogObjectCount
          + " merged)");

      // Move to the next page.
      cursor = listResponse.getCursor();
    } while (cursor != null);

    return sourceIdToTargetObject;
  }

  /**
   * Retrieves all of the CatalogObjects of the specified type from the target account and return
   * them in a HashSet keyed by the encoded string.
   *
   * @param targetCatalogApi the API used to access the catalog of the target account
   * @param cloneUtil the clone utility methods for the specified catalog object type
   * @return a map of the encoded string to the {@link CatalogObject} in the target account
   */
  private HashMap<String, CatalogObject> retrieveTargetCatalogObjects(CatalogApi
      targetCatalogApi,
      CatalogObjectCloneUtil<?> cloneUtil) throws ApiException, CloneCatalogException {
    logger.info("  Retrieving " + cloneUtil.type.toString() + " from target account");
    HashMap<String, CatalogObject> encodedStringToCatalogObject = new HashMap<>();
    int count = 0;
    String cursor = null;
    do {
      // Retrieve a page of catalog objects.
      ListCatalogResponse listResponse =
          targetCatalogApi.listCatalog(cursor, cloneUtil.type.toString());
      if (checkAndLogErrors(listResponse.getErrors())) {
        throw new CloneCatalogException();
      }

      // Generate an encoded string for each object and add it to the hash map.
      for (CatalogObject catalogObject : listResponse.getObjects()) {
        String encodedString = cloneUtil.encodeCatalogObject(catalogObject, false);
        encodedStringToCatalogObject.put(encodedString, catalogObject);
      }

      // Move to the next page.
      count += listResponse.getObjects().size();
      logger.info("    Retrieved " + count + " total");
      cursor = listResponse.getCursor();
    } while (cursor != null);

    return encodedStringToCatalogObject;
  }

  /**
   * Inserts or updates catalog objects into the target account.
   *
   * @param targetCatalogApi the API used to access the catalog of the target account
   * @param catalogObjectsToUpsert the CatalogObjects to insert or update
   * @return the {@link BatchUpsertCatalogObjectsResponse} from the server
   */
  private BatchUpsertCatalogObjectsResponse upsertCatalogObjectsIntoTargetAccount(
      CatalogApi targetCatalogApi, List<CatalogObject> catalogObjectsToUpsert)
      throws ApiException, CloneCatalogException {
    BatchUpsertCatalogObjectsRequest batchUpsertRequest = new BatchUpsertCatalogObjectsRequest()
        .idempotencyKey(UUID.randomUUID().toString())
        .addBatchesItem(new CatalogObjectBatch()
            .objects(catalogObjectsToUpsert)
        );
    BatchUpsertCatalogObjectsResponse batchUpsertResponse =
        targetCatalogApi.batchUpsertCatalogObjects(batchUpsertRequest);
    if (checkAndLogErrors(batchUpsertResponse.getErrors())) {
      throw new CloneCatalogException();
    }
    return batchUpsertResponse;
  }

  /**
   * Add the newly inserted catalog objects to the sourceIdToTargetObject map.
   *
   * @param batchUpsertResponse the {@link BatchUpsertCatalogObjectsResponse} containing the newly
   * inserted CatalogObjects.
   * @param sourceIdToClientId A mapping of the ID of the source CatalogObject to the client
   * generated ID assigned to the CatalogObject in the target account.
   * @param sourceIdToTargetObject a mapping of cloned CatalogObjects in the target account keyed by
   * the object ID in the source account. The map will be updated with the newly inserted
   * CatalogObjects.
   */
  void mapSourceIdsToInsertedCatalogObject(BatchUpsertCatalogObjectsResponse batchUpsertResponse,
      HashMap<String, String> sourceIdToClientId,
      HashMap<String, CatalogObject> sourceIdToTargetObject) throws CloneCatalogException {
    // Convert the list of inserted catalog objects from a list to a map.
    HashMap<String, CatalogObject> targetIdToTargetObject = new HashMap<>();
    for (CatalogObject targetObject : batchUpsertResponse.getObjects()) {
      targetIdToTargetObject.put(targetObject.getId(), targetObject);
    }

    // Convert the CatalogIdMapping from a list to a map.
    HashMap<String, CatalogObject> clientIdToTargetObject = new HashMap<>();
    for (CatalogIdMapping catalogIdMapping : batchUpsertResponse.getIdMappings()) {
      String clientId = catalogIdMapping.getClientObjectId();
      String targetId = catalogIdMapping.getObjectId();
      CatalogObject targetObject = targetIdToTargetObject.get(targetId);

      // targetObject can be null because the BatchResponse includes IdMappings for nested
      // CatalogObjects, but the nested objects are not returned in the response. We only care
      // about the parent objects anyway.
      if (targetObject != null) {
        clientIdToTargetObject.put(clientId, targetObject);
      }
    }

    // Map the sourceId to the target object.
    for (Map.Entry<String, String> entry : sourceIdToClientId.entrySet()) {
      String sourceId = entry.getKey();
      String clientId = entry.getValue();
      CatalogObject targetObject = clientIdToTargetObject.get(clientId);
      if (targetObject == null) {
        throw new CloneCatalogException(
            "Cloned CatalogObject not found in batch response: " + sourceId);
      }
      sourceIdToTargetObject.put(sourceId, targetObject);
    }
  }

  /**
   * User specified configuration options.
   */
  private static class Config {
    private String targetAccessToken;
    private DiscountCloneUtil discountCloneUtil;
    private ModifierListCloneUtil modifierListCloneUtil;
    private TaxCloneUtil taxCloneUtil;
    private CategoryCloneUtil categoryCloneUtil;
    private boolean itemsPresentAtAllLocationsByDefault = false;
  }
}
