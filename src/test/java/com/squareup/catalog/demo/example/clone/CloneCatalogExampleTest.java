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
import com.squareup.connect.models.BatchUpsertCatalogObjectsResponse;
import com.squareup.connect.models.CatalogIdMapping;
import com.squareup.connect.models.CatalogObject;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

public class CloneCatalogExampleTest {

  @Mock Logger logger;
  private CloneCatalogExample example;

  @Before public void setUp() {
    initMocks(this);
    this.example = new CloneCatalogExample(logger);
  }

  @Test public void mapSourceIdsToInsertedCatalogObject_mapsSourceIds()
      throws CloneCatalogException {
    HashMap<String, String> sourceIdToClientId = new HashMap<>();
    sourceIdToClientId.put("source1", "#client1");
    sourceIdToClientId.put("source2", "#client2");

    HashMap<String, CatalogObject> sourceIdToTargetObject = new HashMap<>();
    sourceIdToTargetObject.put("sourceAlreadyInMap", new CatalogObject().id("targetAlreadyInMap"));

    BatchUpsertCatalogObjectsResponse response = new BatchUpsertCatalogObjectsResponse()
        .addObjectsItem(new CatalogObject().id("target1"))
        .addObjectsItem(new CatalogObject().id("target2"))
        .addIdMappingsItem(new CatalogIdMapping().clientObjectId("#client1").objectId("target1"))
        .addIdMappingsItem(new CatalogIdMapping().clientObjectId("#client2").objectId("target2"));

    example.mapSourceIdsToInsertedCatalogObject(response, sourceIdToClientId,
        sourceIdToTargetObject);
    assertThat(sourceIdToTargetObject).hasSize(3);
    assertThat(sourceIdToTargetObject.get("sourceAlreadyInMap").getId()).isEqualTo(
        "targetAlreadyInMap");
    assertThat(sourceIdToTargetObject.get("source1").getId()).isEqualTo("target1");
    assertThat(sourceIdToTargetObject.get("source2").getId()).isEqualTo("target2");
  }
}
