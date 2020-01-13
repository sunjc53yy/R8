// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRenameInLibraryTypeTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRenameInLibraryTypeTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Path baseLibJar;
  private static Path extLibJar;
  private static Path appJar;

  @BeforeClass
  public static void createLibJar() throws Exception {
    String baseLibFolder = PKG_PREFIX + "/libtype_lib_base";
    baseLibJar =
        kotlinc(KOTLINC, KotlinTargetVersion.JAVA_8)
            .addSourceFiles(getKotlinFileInTest(baseLibFolder, "base"))
            .compile();
    String extLibFolder = PKG_PREFIX + "/libtype_lib_ext";
    extLibJar =
        kotlinc(KOTLINC, KotlinTargetVersion.JAVA_8)
            .addClasspathFiles(baseLibJar)
            .addSourceFiles(getKotlinFileInTest(extLibFolder, "ext"))
            .compile();
    String appFolder = PKG_PREFIX + "/libtype_app";
    appJar =
        kotlinc(KOTLINC, KotlinTargetVersion.JAVA_8)
            .addClasspathFiles(baseLibJar)
            .addClasspathFiles(extLibJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "main"))
            .compile();
  }

  @Test
  public void testR8() throws Exception {
    String pkg = getClass().getPackage().getName();
    String main = pkg + ".libtype_app.MainKt";
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            // Intentionally not providing basLibJar as lib file nor classpath file.
            .addProgramFiles(extLibJar, appJar)
            // Keep Ext extension method which requires metadata to be called with Kotlin syntax
            // from other kotlin code.
            .addKeepRules("-keep class **.ExtKt { <methods>; }")
            // Keep the main entry.
            .addKeepMainRule(main)
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addOptionsModification(InternalOptions::enableKotlinMetadataRewriting)
            // -dontoptimize so that basic code structure is kept.
            .noOptimization()
            .compile();
    final String extClassName = pkg + ".libtype_lib_ext.ExtKt";
    compileResult.inspect(inspector -> {
      ClassSubject ext = inspector.clazz(extClassName);
      assertThat(ext, isPresent());
      assertThat(ext, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      KmPackageSubject kmPackage = ext.getKmPackage();
      assertThat(kmPackage, isPresent());
      // Type appearance of library type, Base, should be kept, even if it's not provided.
      // Note that the resulting ClassSubject for Base is an absent one as we don't provide it, and
      // thus we can't use `getReturnTypesInFunctions`, which filters out absent class subject.
      assertTrue(kmPackage.getReturnTypeDescriptorsInFunctions().stream().anyMatch(
          returnType -> returnType.contains("Base")));
    });

    Path out = compileResult.writeToZip();
    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), baseLibJar)
        .addClasspath(out)
        .run(parameters.getRuntime(), main)
        .assertSuccessWithOutputLines("Sub::foo", "Sub::boo", "true");
  }
}
