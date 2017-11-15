package org.sil.bloom.reader;

import java.util.HashMap;
import java.util.Map;

public class PageDimensions {

    private double width;
    private double height;

    private static final Map<String, PageDimensions> DIMENSIONS_MAP = new HashMap<String, PageDimensions>();
    static {
        DIMENSIONS_MAP.put("Device16x9", new PageDimensions(100, 178));
        DIMENSIONS_MAP.put("A5", new PageDimensions(148, 210));
        DIMENSIONS_MAP.put("B5", new PageDimensions(176, 250));
        DIMENSIONS_MAP.put("A4", new PageDimensions(210, 297));
        DIMENSIONS_MAP.put("A3", new PageDimensions(297, 420));
        DIMENSIONS_MAP.put("A6", new PageDimensions(105, 148));
        DIMENSIONS_MAP.put("QuarterLetter", new PageDimensions(108, 139.7)); // 4.25" x 5.5"
        DIMENSIONS_MAP.put("HalfLetter", new PageDimensions(139.7, 215.9));  // 5.5" x 8.5"
        DIMENSIONS_MAP.put("Letter", new PageDimensions(215.9, 279.4));      // 8.5" x 11"
        DIMENSIONS_MAP.put("HalfLegal", new PageDimensions(177.8, 215.9));   // 7" x 8.5"
        DIMENSIONS_MAP.put("Legal", new PageDimensions(215.9, 355.6));       // 8.5" x 14"
    }

    public PageDimensions(double width, double height){
        this.width = width;
        this.height = height;
    }

    public PageDimensions rotated(){
        return new PageDimensions(height, width);
    }

    public double getWidthInMM(){ return width; }
    public double getHeightInMM(){ return height; }
    public double getWidthInPx(){ return width * 3.78; }
    public double getHeightInPx(){ return height * 3.78; }

    public static PageDimensions getPageDimensions(String layout, boolean landscape){
        PageDimensions dimensions = DIMENSIONS_MAP.get(layout);
        if(dimensions == null)
            dimensions = defaultPageDimensions();
        if(landscape)
            dimensions = dimensions.rotated();
        return dimensions;
    }

    public static PageDimensions defaultPageDimensions(){
        return DIMENSIONS_MAP.get("Device16x9");
    }
}
