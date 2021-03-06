// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.examples.jdk15;

import com.android.tools.r8.examples.JavaExampleClassProxy;
import java.nio.file.Path;

public class Records {

  private static final String EXAMPLE_FILE = "examplesJava15/records";

  public static final JavaExampleClassProxy Main =
      new JavaExampleClassProxy(EXAMPLE_FILE, "records/Main");
  public static final JavaExampleClassProxy Main$Person =
      new JavaExampleClassProxy(EXAMPLE_FILE, "records/Main$Person");

  public static Path jar() {
    return JavaExampleClassProxy.examplesJar(EXAMPLE_FILE);
  }
}
