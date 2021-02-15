// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.ToolHelper.getKotlinAnnotationJar;
import static com.android.tools.r8.ToolHelper.getKotlinStdlibJar;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteKeepPathTest extends KotlinMetadataTestBase {

  @Parameterized.Parameters(name = "{0}, {1}, keep: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/box_primitives_lib", "lib"));
  private static final String LIB_CLASS_NAME = PKG + ".box_primitives_lib.Test";
  private final TestParameters parameters;
  private final boolean keepMetadata;

  public MetadataRewriteKeepPathTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters, boolean keepMetadata) {
    super(kotlinParameters);
    this.parameters = parameters;
    this.keepMetadata = keepMetadata;
  }

  @Test
  public void testProgramPath() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
        .addProgramFiles(getKotlinStdlibJar(kotlinc), getKotlinAnnotationJar(kotlinc))
        .addKeepRules("-keep class " + LIB_CLASS_NAME)
        .applyIf(keepMetadata, TestShrinkerBuilder::addKeepKotlinMetadata)
        .addKeepRuntimeVisibleAnnotations()
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .inspect(inspector -> inspect(inspector, keepMetadata));
  }

  @Test
  public void testClassPathPath() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
        .addClasspathFiles(getKotlinStdlibJar(kotlinc), getKotlinAnnotationJar(kotlinc))
        .addKeepRules("-keep class " + LIB_CLASS_NAME)
        .addKeepRuntimeVisibleAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  @Test
  public void testLibraryPath() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
        .addLibraryFiles(getKotlinStdlibJar(kotlinc), getKotlinAnnotationJar(kotlinc))
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .addKeepRules("-keep class " + LIB_CLASS_NAME)
        .addKeepRuntimeVisibleAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  @Test
  public void testMissing() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
        .addClasspathFiles(getKotlinStdlibJar(kotlinc), getKotlinAnnotationJar(kotlinc))
        .addKeepRules("-keep class " + LIB_CLASS_NAME)
        .addKeepRuntimeVisibleAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  private void inspect(CodeInspector inspector, boolean expectMetadata) {
    ClassSubject clazz = inspector.clazz(LIB_CLASS_NAME);
    assertThat(clazz, isPresent());
    assertEquals(expectMetadata, clazz.getKotlinClassMetadata() != null);
  }
}
