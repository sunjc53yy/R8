// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.DexMethod;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Maintains a set of canonical positions. Also supports appending a new caller at the end of the
 * caller chain of a Position.
 */
public class CanonicalPositions {
  private final Position callerPosition;
  private final Map<Position, Position> canonicalPositions;
  private final Position preamblePosition;
  private final boolean isCompilerSynthesizedInlinee;

  // Lazily computed synthetic position for shared exceptional exits in synchronized methods.
  private Position syntheticPosition;

  public CanonicalPositions(
      Position callerPosition,
      int expectedPositionsCount,
      DexMethod method,
      boolean methodIsSynthesized) {
    canonicalPositions =
        new HashMap<>(1 + (callerPosition == null ? 0 : 1) + expectedPositionsCount);
    if (callerPosition != null) {
      this.callerPosition = getCanonical(callerPosition);
      isCompilerSynthesizedInlinee = methodIsSynthesized;
      preamblePosition =
          methodIsSynthesized
              ? callerPosition
              : getCanonical(new Position(0, null, method, callerPosition));
    } else {
      this.callerPosition = null;
      isCompilerSynthesizedInlinee = false;
      preamblePosition = getCanonical(Position.synthetic(0, method, null));
    }
  }

  public Position getPreamblePosition() {
    return preamblePosition;
  }

  /**
   * Update the internal set if this is the first occurrence of the position's value and return
   * canonical instance of position.
   */
  public Position getCanonical(Position position) {
    Position canonical = canonicalPositions.putIfAbsent(position, position);
    return canonical != null ? canonical : position;
  }

  /**
   * Append callerPosition (supplied in constructor) to the end of caller's caller chain and return
   * the canonical instance.
   */
  public Position canonicalizeCallerPosition(Position caller) {
    if (caller == null) {
      return callerPosition;
    }
    if (caller.callerPosition == null && callerPosition == null) {
      // This is itself the outer-most position.
      return getCanonical(caller);
    }
    if (caller.callerPosition == null && isCompilerSynthesizedInlinee) {
      // This is the outer-most position of the inlinee (eg, the inlinee itself).
      // If compiler synthesized, strip it from the position info by directly returning caller.
      return callerPosition;
    }
    Position callerOfCaller = canonicalizeCallerPosition(caller.callerPosition);
    return getCanonical(
        caller.isNone()
            ? Position.noneWithMethod(caller.method, callerOfCaller)
            : new Position(caller.line, caller.file, caller.method, callerOfCaller));
  }

  // If we need to emit a synthetic position for exceptional monitor exits, we try to cook up a
  // position that is not actually a valid program position, so as not to incorrectly position the
  // user on an exit that is not the actual exit being taken. Our heuristic for this is that if the
  // method has at least two positions we use the first position minus one as the synthetic exit.
  // If the method only has one position it is safe to just use that position.
  public Position getExceptionalExitPosition(
      boolean debug, Supplier<Iterable<Position>> positions, DexMethod originalMethod) {
    if (syntheticPosition == null) {
      if (debug) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Position position : positions.get()) {
          // No inlining in debug mode, so the position is from the only frame.
          assert position == position.getOutermostCaller();
          int line = position.line;
          min = Math.min(min, line);
          max = Math.max(max, line);
        }
        syntheticPosition =
            (min == Integer.MAX_VALUE)
                ? getPreamblePosition()
                : Position.synthetic(min < max ? min - 1 : min, originalMethod, callerPosition);
      } else {
        // If in release mode we explicitly associate a synthetic none position with monitor exit.
        // This is safe as the runtime must never throw at this position because the monitor cannot
        // be null and the thread calling exit can only be the same thread that entered the monitor
        // at method entry.
        syntheticPosition = Position.syntheticNone();
      }
    }
    return syntheticPosition;
  }
}
