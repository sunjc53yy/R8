// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import static com.android.tools.r8.Collectors.toSingle;
import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static com.android.tools.r8.ToolHelper.getKotlinAnnotationJar;
import static com.android.tools.r8.ToolHelper.getKotlinStdlibJar;
import static com.android.tools.r8.utils.codeinspector.Matchers.containsLinePositions;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInlineFrame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInlineStack;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.Matchers.LinePosition;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinInlineFunctionInSameFileRetraceTests extends KotlinTestBase {

  private static final String FILENAME_INLINE = "InlineFunctionsInSameFile.kt";
  private static final String MAIN = "retrace.InlineFunctionsInSameFileKt";

  private final TestParameters parameters;

  @Parameters(name = "{0}, target: {1}")
  public static List<Object[]> data() {
    // TODO(b/141817471): Extend with compilation modes.
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public KotlinInlineFunctionInSameFileRetraceTests(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer compilationResults =
      getCompileMemoizer(getKotlinSources());

  private static Collection<Path> getKotlinSources() {
    try {
      return getFilesInTestFolderRelativeToClass(
          KotlinInlineFunctionRetraceTest.class, "kt", ".kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testRuntime() throws Exception {
    // TODO(b/179666509): SMAP has changed.
    assumeTrue(kotlinc.is(KotlinCompilerVersion.KOTLINC_1_3_72));
    testForRuntime(parameters)
        .addProgramFiles(compilationResults.getForConfiguration(kotlinc, targetVersion))
        .addRunClasspathFiles(buildOnDexRuntime(parameters, getKotlinStdlibJar(kotlinc)))
        .run(parameters.getRuntime(), MAIN)
        .assertFailureWithErrorThatMatches(containsString("foo"))
        .assertFailureWithErrorThatMatches(
            containsString(
                "at retrace.InlineFunctionsInSameFileKt.main(InlineFunctionsInSameFile.kt:43"));
  }

  @Test
  public void testRetraceKotlinInlineStaticFunction() throws Exception {
    // TODO(b/179666509): SMAP has changed.
    assumeTrue(kotlinc.is(KotlinCompilerVersion.KOTLINC_1_3_72));
    Path kotlinSources = compilationResults.getForConfiguration(kotlinc, targetVersion);
    CodeInspector kotlinInspector = new CodeInspector(kotlinSources);
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.getForConfiguration(kotlinc, targetVersion))
        .addProgramFiles(getKotlinStdlibJar(kotlinc), getKotlinAnnotationJar(kotlinc))
        .addKeepAttributes("SourceFile", "LineNumberTable")
        .setMode(CompilationMode.RELEASE)
        .addKeepMainRule(MAIN)
        .allowDiagnosticWarningMessages()
        .noMinification()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), MAIN)
        .assertFailureWithErrorThatMatches(containsString("main"))
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject = codeInspector.clazz(MAIN).uniqueMethodWithName("main");
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          kotlinInspector
                              .clazz("retrace.InlineFunctionsInSameFileKt")
                              .uniqueMethodWithName("foo")
                              .asFoundMethodSubject(),
                          1,
                          8,
                          FILENAME_INLINE),
                      LinePosition.create(
                          mainSubject.asFoundMethodSubject(), 1, 43, FILENAME_INLINE));
              checkInlineInformation(stackTrace, codeInspector, mainSubject, inlineStack);
            });
  }

  private void checkInlineInformation(
      StackTrace stackTrace,
      CodeInspector codeInspector,
      MethodSubject mainSubject,
      LinePosition inlineStack) {
    assertThat(mainSubject, isPresent());
    RetraceFrameResult retraceResult =
        mainSubject
            .streamInstructions()
            .filter(InstructionSubject::isThrow)
            .collect(toSingle())
            .retraceLinePosition(codeInspector.retrace());
    assertThat(retraceResult, isInlineFrame());
    assertThat(retraceResult, isInlineStack(inlineStack));
    assertThat(stackTrace, containsLinePositions(inlineStack));
  }
}
