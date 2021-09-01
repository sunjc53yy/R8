// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaring;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter.Flavor;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public class InterfaceMethodProcessorFacade implements CfPostProcessingDesugaring {

  private final AppView<?> appView;
  private final Flavor flavour;
  private final List<InterfaceDesugaringProcessor> interfaceDesugaringProcessors;

  InterfaceMethodProcessorFacade(
      AppView<?> appView, Flavor flavour, Predicate<ProgramMethod> isLiveMethod) {
    this.appView = appView;
    this.flavour = flavour;
    interfaceDesugaringProcessors = instantiateInterfaceDesugaringProcessors(appView, isLiveMethod);
  }

  InterfaceMethodProcessorFacade(
      AppView<?> appView,
      Flavor flavour,
      Predicate<ProgramMethod> isLiveMethod,
      InterfaceProcessor interfaceProcessor) {
    this.appView = appView;
    this.flavour = flavour;
    interfaceDesugaringProcessors =
        ImmutableList.of(new ClassProcessor(appView, isLiveMethod), interfaceProcessor);
  }

  private List<InterfaceDesugaringProcessor> instantiateInterfaceDesugaringProcessors(
      AppView<?> appView, Predicate<ProgramMethod> isLiveMethod) {

    // Process all classes first. Add missing forwarding methods to
    // replace desugared default interface methods.
    ClassProcessor classProcessor = new ClassProcessor(appView, isLiveMethod);

    // Process interfaces, create companion or dispatch class if needed, move static
    // methods to companion class, copy default interface methods to companion classes,
    // make original default methods abstract, remove bridge methods, create dispatch
    // classes if needed.
    InterfaceProcessor interfaceProcessor = new InterfaceProcessor(appView);

    // The processors can be listed in any order.
    return ImmutableList.of(classProcessor, interfaceProcessor);
  }

  private boolean shouldProcess(DexProgramClass clazz, Flavor flavour) {
    if (appView.isAlreadyLibraryDesugared(clazz)) {
      return false;
    }
    return (!clazz.originatesFromDexResource() || flavour == Flavor.IncludeAllResources);
  }

  private void processClassesConcurrently(
      Collection<DexProgramClass> programClasses,
      InterfaceProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        Iterables.filter(programClasses, (DexProgramClass clazz) -> shouldProcess(clazz, flavour)),
        clazz -> {
          for (InterfaceDesugaringProcessor processor : interfaceDesugaringProcessors) {
            processor.process(clazz, eventConsumer);
          }
        },
        executorService);
    for (InterfaceDesugaringProcessor processor : interfaceDesugaringProcessors) {
      processor.finalizeProcessing(eventConsumer);
    }
  }

  @Override
  public void postProcessingDesugaring(
      Collection<DexProgramClass> programClasses,
      CfPostProcessingDesugaringEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    // TODO(b/183998768): Would be nice to use the ClassProcessing for the processing of classes,
    //  and do here only the finalization.
    processClassesConcurrently(programClasses, eventConsumer, executorService);
  }
}