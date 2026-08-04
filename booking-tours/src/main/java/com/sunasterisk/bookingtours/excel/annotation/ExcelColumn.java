package com.sunasterisk.bookingtours.excel.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelColumn {
    String header();
    int order();
    boolean required() default false;
    String dateFormat() default "yyyy-MM-dd";
}
