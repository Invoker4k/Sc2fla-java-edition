package com.invoker4k.sc2fla.atlas;

import com.invoker4k.sc2fla.dom.SymbolItem;
import dev.donutquine.math.Rect;   

public class NineSlice {
    public static void apply(SymbolItem symbol, float left, float top, float right, float bottom) {
        symbol.scaleGridLeft = left;
        symbol.scaleGridTop = top;
        symbol.scaleGridRight = right;
        symbol.scaleGridBottom = bottom;
    }

    public static void applyFromRect(SymbolItem symbol, Rect rect) {
        if (rect == null) return;
        symbol.scaleGridLeft = rect.getLeft();
        symbol.scaleGridTop = rect.getTop();
        symbol.scaleGridRight = rect.getRight();
        symbol.scaleGridBottom = rect.getBottom();
    }
}