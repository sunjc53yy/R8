// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.google.common.base.Predicates;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinClassStaticizerTest extends AbstractR8KotlinTestBase {

  @Parameterized.Parameters(name = "{0}, {1}, allowAccessModification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public KotlinClassStaticizerTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(parameters, kotlinParameters, allowAccessModification);
  }

  @Test
  public void testCompanionAndRegularObjects() throws Exception {
    assumeTrue("Only work with -allowaccessmodification", allowAccessModification);
    final String mainClassName = "class_staticizer.MainKt";

    // Without class staticizer.
    runTest("class_staticizer", mainClassName, true)
        .inspect(
            inspector -> {
              assertThat(inspector.clazz("class_staticizer.Derived$Companion"), isPresent());

              // The Util class is there, but its instance methods have been inlined.
              ClassSubject utilClass = inspector.clazz("class_staticizer.Util");
              assertThat(utilClass, isPresent());
              assertTrue(
                  utilClass.allMethods().stream()
                      .filter(Predicates.not(FoundMethodSubject::isStatic))
                      .allMatch(FoundMethodSubject::isInstanceInitializer));
            });

    // With class staticizer.
    runTest("class_staticizer", mainClassName, false)
        .inspect(
            inspector -> {
              assertThat(inspector.clazz("class_staticizer.Regular$Companion"), not(isPresent()));

              ClassSubject utilClass = inspector.clazz("class_staticizer.Util");
              assertThat(utilClass, isPresent());
              // TODO(b/179951488): The <init> is not removed in CF
              if (testParameters.isDexRuntime()) {
                assertTrue(utilClass.allMethods().stream().allMatch(FoundMethodSubject::isStatic));
              }
            });
  }

  protected R8TestRunResult runTest(String folder, String mainClass, boolean noClassStaticizing)
      throws Exception {
    return runTest(
        folder,
        mainClass,
        testBuilder ->
            testBuilder
                .noClassInlining()
                .noClassStaticizing(noClassStaticizing));
  }
}
