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

import com.squareup.connect.models.CatalogDiscount;
import com.squareup.connect.models.CatalogObject;
import com.squareup.connect.models.CatalogTax;

import static com.squareup.connect.models.CatalogObject.TypeEnum.TAX;

/**
 * Utility methods used to clone a {@link CatalogDiscount}.
 *
 * If a tax in the source account has the same name, percentage, and inclusion type as a tax in
 * the target account, then the tax in the source account is not cloned.
 */
class TaxCloneUtil extends CatalogObjectCloneUtil<CatalogTax> {

  TaxCloneUtil() {
    super(TAX, false);
  }

  @Override CatalogTax getCatalogData(CatalogObject catalogObject) {
    return catalogObject.getTaxData();
  }

  @Override String encodeCatalogData(CatalogTax tax, boolean fromSourceAccount) {
    return tax.getName() + ":::" + tax.getPercentage() + ":::" + tax.getInclusionType();
  }
}
