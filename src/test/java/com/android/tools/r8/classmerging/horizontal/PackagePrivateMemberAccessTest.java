// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.testclasses.A;
import com.android.tools.r8.classmerging.horizontal.testclasses.B;
import org.junit.Test;

public class PackagePrivateMemberAccessTest extends HorizontalClassMergingTestBase {
  public PackagePrivateMemberAccessTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addProgramClasses(A.class)
        .addProgramClasses(B.class)
        .addKeepMainRule(Main.class)
        .allowAccessModification(false)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "B", "bar", "5", "foobar")
        .inspect(
            codeInspector -> {
                assertThat(codeInspector.clazz(A.class), isPresent());
                assertThat(codeInspector.clazz(B.class), not(isPresent()));
                assertThat(codeInspector.clazz(C.class), isPresent());
            });
  }

  @NeverClassInline
  public static class C {
    public C(int v) {
      System.out.println(v);
    }

    public void foobar() {
      System.out.println("foobar");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      a.foo();
      B b = a.get("B");
      b.bar();
      C c = new C(5);
      c.foobar();
    }
  }
}
