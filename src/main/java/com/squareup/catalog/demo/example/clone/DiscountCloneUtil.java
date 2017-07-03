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

import static com.squareup.connect.models.CatalogObject.TypeEnum.DISCOUNT;

/**
 * Utility methods used to clone a {@link CatalogDiscount}.
 *
 * If a discount in the source account has the same name, discount type, percentage, and amount
 * as a discount in the target account, then the discount in the source account is not cloned.
 */
class DiscountCloneUtil extends CatalogObjectCloneUtil<CatalogDiscount> {

  DiscountCloneUtil(boolean presentAtAllLocationsByDefault) {
    super(DISCOUNT, presentAtAllLocationsByDefault);
  }

  @Override CatalogDiscount getCatalogData(CatalogObject catalogObject) {
    return catalogObject.getDiscountData();
  }

  @Override String encodeCatalogData(CatalogDiscount discount, boolean fromSourceAccount) {
    return discount.getName()
        + ":::"
        + discount.getDiscountType()
        + ":::"
        + discount.getPercentage()
        + ":::"
        + amountOrNull(discount.getAmountMoney());
  }
}
