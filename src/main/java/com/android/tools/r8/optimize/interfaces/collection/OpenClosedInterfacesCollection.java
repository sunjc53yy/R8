// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.collection;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Supplier;

/**
 * Knowledge about open/closed interfaces.
 *
 * <p>An interface type is "open" if it may store an instance that is not a subtype of the given
 * interface.
 *
 * <p>An interface type is "closed" if it is guaranteed to store instances that are subtypes of the
 * given interface.
 */
public abstract class OpenClosedInterfacesCollection {

  public static DefaultOpenClosedInterfacesCollection getDefault() {
    return DefaultOpenClosedInterfacesCollection.getInstance();
  }

  public abstract boolean isDefinitelyClosed(DexClass clazz);

  public final boolean isDefinitelyInstanceOfStaticType(
      AppView<AppInfoWithLiveness> appView, Value value) {
    return isDefinitelyInstanceOfStaticType(
        appView, () -> value.getDynamicType(appView), value.getType());
  }

  public final boolean isDefinitelyInstanceOfStaticType(
      AppView<?> appView, Supplier<DynamicType> dynamicTypeSupplier, TypeElement staticType) {
    if (!staticType.isClassType()) {
      // Only interface types may store instances that are not a subtype of the static type.
      return true;
    }
    ClassTypeElement staticClassType = staticType.asClassType();
    if (staticClassType.getClassType() != appView.dexItemFactory().objectType) {
      // Ditto.
      return true;
    }
    if (staticClassType.nullability().isDefinitelyNull()) {
      // The null value is definitely an instance of the static type.
      return true;
    }
    boolean isStaticTypeDefinitelyClosed =
        staticClassType
            .getInterfaces()
            .allKnownInterfacesMatch(
                knownInterfaceType -> {
                  DexClass knownInterface = appView.definitionFor(knownInterfaceType);
                  return knownInterface != null && isDefinitelyClosed(knownInterface);
                });
    if (isStaticTypeDefinitelyClosed) {
      return true;
    }
    DynamicType dynamicType = dynamicTypeSupplier.get();
    if (dynamicType.isNullType()) {
      return true;
    }
    if (dynamicType.isUnknown()) {
      return false;
    }
    TypeElement dynamicUpperBoundType = dynamicType.getDynamicUpperBoundType(staticType);
    if (!dynamicUpperBoundType.isClassType()) {
      // Should not happen, since the dynamic type should be assignable to the static type.
      assert false;
      return false;
    }
    ClassTypeElement dynamicUpperBoundClassType = dynamicUpperBoundType.asClassType();
    if (dynamicUpperBoundClassType.getClassType() != appView.dexItemFactory().objectType) {
      // The dynamic upper bound type is a non-interface type. Check if this non-interface type is a
      // subtype of the static interface type.
      return dynamicUpperBoundClassType.lessThanOrEqualUpToNullability(staticType, appView);
    }
    return false;
  }

  public abstract OpenClosedInterfacesCollection rewrittenWithLens(GraphLens graphLens);

  public abstract OpenClosedInterfacesCollection withoutPrunedItems(PrunedItems prunedItems);
}