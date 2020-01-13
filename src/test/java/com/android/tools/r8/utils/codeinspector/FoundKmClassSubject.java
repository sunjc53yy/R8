// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexClass;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmDeclarationContainer;

public class FoundKmClassSubject extends KmClassSubject
    implements FoundKmDeclarationContainerSubject {
  private final CodeInspector codeInspector;
  private final DexClass clazz;
  private final KmClass kmClass;

  FoundKmClassSubject(CodeInspector codeInspector, DexClass clazz, KmClass kmClass) {
    this.codeInspector = codeInspector;
    this.clazz = clazz;
    this.kmClass = kmClass;
  }

  @Override
  public DexClass getDexClass() {
    return clazz;
  }

  @Override
  public CodeInspector codeInspector() {
    return codeInspector;
  }

  @Override
  public KmDeclarationContainer getKmDeclarationContainer() {
    return kmClass;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return !clazz.type.getInternalName().equals(kmClass.name);
  }

  @Override
  public boolean isSynthetic() {
    // TODO(b/70169921): This should return `true` conditionally if we start synthesizing @Metadata
    //   from scratch.
    return false;
  }

  @Override
  public List<String> getSuperTypeDescriptors() {
    return kmClass.getSupertypes().stream()
        .map(this::getDescriptorFromKmType)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public List<ClassSubject> getSuperTypes() {
    return kmClass.getSupertypes().stream()
        .map(this::getClassSubjectFromKmType)
        .filter(ClassSubject::isPresent)
        .collect(Collectors.toList());
  }
}
