package com.nlzh.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * controller注解
 * @author 13521
 *
 */
// 注解在类上生效
@Target({ElementType.TYPE})
// 注解是运行时可用
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Controller {
	String value() default "";
}
