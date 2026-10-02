package io.oryxos.provider;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 精简的反射 JSON Schema 生成器：把 {@code OryxTool.getParameterSchema()} 的 Class 转为参数 JSON
 * Schema，不引入额外依赖。支持基本类型/包装类型、枚举、数组、Collection/Map、 record 组件与普通字段，带祖先链循环引用保护。
 */
final class JsonSchemaGenerator {

  private JsonSchemaGenerator() {}

  static String generate(Class<?> type) {
    if (type == null || type == Void.class || type == void.class) {
      return "{\"type\":\"object\",\"properties\":{}}";
    }
    StringBuilder sb = new StringBuilder();
    appendSchema(sb, type, new HashSet<>());
    return sb.toString();
  }

  private static void appendSchema(StringBuilder sb, Class<?> type, Set<Class<?>> visiting) {
    if (type == String.class || type == char.class || type == Character.class) {
      sb.append("{\"type\":\"string\"}");
    } else if (type == boolean.class || type == Boolean.class) {
      sb.append("{\"type\":\"boolean\"}");
    } else if (isInteger(type)) {
      sb.append("{\"type\":\"integer\"}");
    } else if (isNumber(type)) {
      sb.append("{\"type\":\"number\"}");
    } else if (type.isEnum()) {
      appendEnum(sb, type);
    } else if (type.isArray()) {
      sb.append("{\"type\":\"array\",\"items\":");
      appendSchema(sb, type.getComponentType(), visiting);
      sb.append('}');
    } else if (Collection.class.isAssignableFrom(type)) {
      sb.append("{\"type\":\"array\"}");
    } else if (Map.class.isAssignableFrom(type)) {
      sb.append("{\"type\":\"object\"}");
    } else {
      appendObject(sb, type, visiting);
    }
  }

  private static void appendEnum(StringBuilder sb, Class<?> type) {
    sb.append("{\"type\":\"string\",\"enum\":[");
    Object[] constants = type.getEnumConstants();
    for (int i = 0; i < constants.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append('"').append(constants[i]).append('"');
    }
    sb.append("]}");
  }

  private static void appendObject(StringBuilder sb, Class<?> type, Set<Class<?>> visiting) {
    if (!visiting.add(type)) {
      sb.append("{\"type\":\"object\"}"); // 循环引用：不再展开
      return;
    }
    sb.append("{\"type\":\"object\",\"properties\":{");
    boolean first = true;
    if (type.isRecord()) {
      for (RecordComponent component : type.getRecordComponents()) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        appendProperty(sb, component.getName(), component.getType(), visiting);
      }
    } else {
      for (Field field : type.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
          continue;
        }
        if (!first) {
          sb.append(',');
        }
        first = false;
        appendProperty(sb, field.getName(), field.getType(), visiting);
      }
    }
    sb.append("}}");
    visiting.remove(type);
  }

  private static void appendProperty(
      StringBuilder sb, String name, Class<?> type, Set<Class<?>> visiting) {
    sb.append('"').append(name).append("\":");
    appendSchema(sb, type, visiting);
  }

  private static boolean isInteger(Class<?> type) {
    return type == int.class
        || type == Integer.class
        || type == long.class
        || type == Long.class
        || type == short.class
        || type == Short.class
        || type == byte.class
        || type == Byte.class
        || type == BigInteger.class;
  }

  private static boolean isNumber(Class<?> type) {
    return type == float.class
        || type == Float.class
        || type == double.class
        || type == Double.class
        || type == BigDecimal.class;
  }
}
