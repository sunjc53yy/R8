// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jar;

import static org.objectweb.asm.Opcodes.ACC_SUPER;

import com.android.tools.r8.OutputSink;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexAccessFlags;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import jdk.internal.org.objectweb.asm.Type;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

public class CfApplicationWriter {
  private final DexApplication application;
  private final InternalOptions options;

  public CfApplicationWriter(DexApplication application, InternalOptions options) {
    this.application = application;
    this.options = options;
  }

  public void write(OutputSink outputSink, ExecutorService executor) throws IOException {
    application.timing.begin("CfApplicationWriter.write");
    try {
      writeApplication(outputSink, executor);
    } finally {
      application.timing.end();
    }
  }

  private void writeApplication(OutputSink outputSink, ExecutorService executor)
      throws IOException {
    for (DexProgramClass clazz : application.classes()) {
      if (clazz.getSynthesizedFrom().isEmpty()) {
        writeClass(clazz, outputSink);
      } else {
        throw new Unimplemented("No support for synthetics in the Java bytecode backend.");
      }
    }
  }

  private void writeClass(DexProgramClass clazz, OutputSink outputSink) throws IOException {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    writer.visitSource(clazz.sourceFile.toString(), null);
    int version = clazz.getClassFileVersion();
    int access = classAndInterfaceAccessFlags(clazz.accessFlags);
    String desc = clazz.type.toDescriptorString();
    String name = internalName(clazz.type);
    String signature = null; // TODO(zerny): Support generic signatures.
    String superName =
        clazz.type == options.itemFactory.objectType ? null : internalName(clazz.superType);
    String[] interfaces = new String[clazz.interfaces.values.length];
    for (int i = 0; i < clazz.interfaces.values.length; i++) {
      interfaces[i] = internalName(clazz.interfaces.values[i]);
    }
    writer.visit(version, access, name, signature, superName, interfaces);
    // TODO(zerny): Methods and fields.
    for (DexEncodedMethod method : clazz.directMethods()) {
      writeMethod(method, writer);
    }
    outputSink.writeClassFile(writer.toByteArray(), Collections.singleton(desc), desc);
  }

  private void writeMethod(DexEncodedMethod method, ClassWriter writer) {
    int access = method.accessFlags.get();
    String name = method.method.name.toString();
    String desc = method.descriptor();
    String signature = null; // TODO(zerny): Support generic signatures.
    String[] exceptions = null;
    MethodVisitor visitor = writer.visitMethod(access, name, desc, signature, exceptions);
    method.getCode().asJarCode().writeTo(visitor);
  }

  private static int classAndInterfaceAccessFlags(DexAccessFlags accessFlags) {
    // TODO(zerny): Refactor access flags to account for the union of both DEX and Java flags.
    int access = accessFlags.get();
    access |= ACC_SUPER;
    return access;
  }

  private static String internalName(DexType type) {
    return Type.getType(type.toDescriptorString()).getInternalName();
  }
}
