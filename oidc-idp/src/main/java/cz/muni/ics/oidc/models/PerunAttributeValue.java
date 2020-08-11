package cz.muni.ics.oidc.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Strings;
import cz.muni.ics.oidc.exceptions.InconvertibleValueException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Model representing value of attribute from Perun
 *
 * @author Dominik Frantisek Bucik <bucik@ics.muni.cz>
 */
public class PerunAttributeValue {

	public final static String STRING_TYPE = "java.lang.String";
	public final static String INTEGER_TYPE = "java.lang.Integer";
	public final static String BOOLEAN_TYPE = "java.lang.Boolean";
	public final static String ARRAY_TYPE = "java.util.ArrayList";
	public final static String MAP_TYPE = "java.util.LinkedHashMap";
	public final static String LARGE_STRING_TYPE = "java.lang.LargeString";
	public final static String LARGE_ARRAY_LIST_TYPE = "java.util.LargeArrayList";

	private String type;
	private Object value;

	public PerunAttributeValue(String type, JsonNode value) {
		this.setType(type);
		this.setValue(type, value);
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		if (Strings.isNullOrEmpty(type)) {
			throw new IllegalArgumentException("type can't be null or empty");
		}

		this.type = type;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	public void setValue(String type, JsonNode value) {
		if (isNullValue(value)) {
			if (!BOOLEAN_TYPE.equals(type)) {
				this.value = null;
				return;
			} else {
				value = JsonNodeFactory.instance.booleanNode(false);
			}
		}

		switch (type) {
			case STRING_TYPE:
			case LARGE_STRING_TYPE: {
				this.value = value.asText();
			} break;
			case INTEGER_TYPE: {
				this.value = value.asLong();
			} break;
			case BOOLEAN_TYPE: {
				this.value = value.asBoolean();
			} break;
			case ARRAY_TYPE:
			case LARGE_ARRAY_LIST_TYPE: {
				this.value = parseArrayValue(value);
			} break;
			case MAP_TYPE: {
				this.value = parseMapValue(value);
			} break;
			default:
				this.value = null;
		}
	}

	/**
	 * Get value as String.
	 * @return String value or null.
	 */
	public String valueAsString() {
		if ((STRING_TYPE.equals(type) || LARGE_STRING_TYPE.equals(type))) {
			if (isNullValue(value)) {
				return null;
			} else if (value instanceof String) {
				return (String) value;
			} else if (value instanceof TextNode) {
				return ((TextNode) value).textValue();
			}
		}

		throw inconvertible(String.class);
	}

	/**
	 * Get value as Long
	 * @return Long value or null.
	 */
	public Long valueAsLong() {
		if (INTEGER_TYPE.equals(type)) {
			if (isNullValue(value)) {
				return null;
			} else if (value instanceof Long) {
				return (Long) value;
			} else if (value instanceof NumericNode) {
				return ((NumericNode) value).longValue();
			}
		}

		throw inconvertible(Long.class);
	}

	/**
	 * Get value as Boolean.
	 * @return True if value is TRUE, false in case of value being false or null.
	 */
	public boolean valueAsBoolean() {
		if (BOOLEAN_TYPE.equals(type)) {
			if (isNullValue(value)) {
				return false;
			} else if (value instanceof Boolean) {
				return (boolean) value;
			} else if (value instanceof BooleanNode) {
				return ((BooleanNode) value).asBoolean();
			}
		}

		throw inconvertible(Boolean.class);
	}

	/**
	 * Get value as List of Strings
	 * @return List of strings or null
	 */
	@SuppressWarnings("unchecked")
	public List<String> valueAsList() throws InconvertibleValueException {
		if ((ARRAY_TYPE.equals(type) || LARGE_ARRAY_LIST_TYPE.equals(type))) {
			if (isNullValue(value)) {
				return new ArrayList<>();
			} else if (value instanceof List) {
				return (List<String>) value;
			} else if (value instanceof ArrayNode) {
				List<String> arr = new ArrayList<>();
				ArrayNode arrJson = (ArrayNode) value;
				arrJson.forEach(item -> arr.add(item.asText()));
				return arr;
			}
		}

		// we need to try convert it to string as LDAP does not distinguish between List and String.
		// when LDAP sees one value, it assumes it is String, even if it is supposed to be an array
		try {
			String strVal = valueAsString();
			if (strVal == null) {
				return new ArrayList<>();
			}
			return Arrays.asList(strVal);
		} catch (InconvertibleValueException e) {
			// this is OK, we will throw exception after catch
		}

		throw inconvertible(List.class);
	}

	/**
	 * Get value as map of Strings to Strings
	 * @return Map or null
	 */
	@SuppressWarnings("unchecked")
	public Map<String, String> valueAsMap() throws InconvertibleValueException {
		if (MAP_TYPE.equals(type)) {
			if (isNullValue(value)) {
				return new HashMap<>();
			} else if (value instanceof Map) {
				return (Map<String, String>) value;
			} else if (value instanceof ObjectNode) {
				Map<String, String> res = new HashMap<>();
				ObjectNode objJson = (ObjectNode) value;
				Iterator<String> it = objJson.fieldNames();
				while (it.hasNext()) {
					String key = it.next();
					res.put(key, objJson.get(key).asText());
				}
				return res;
			}
		}

		throw inconvertible(Map.class);
	}

	/**
	 * Get value as JsonNode
	 * @return JsonNode or NullNode
	 */
	public JsonNode valueAsJson() {
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			String json = objectMapper.writeValueAsString(value);
			return objectMapper.readTree(json);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return NullNode.getInstance();
	}

	@Override
	public String toString() {
		return "PerunAttributeValue{" +
				"type='" + type + '\'' +
				", value=" + value +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PerunAttributeValue value1 = (PerunAttributeValue) o;
		return Objects.equals(type, value1.type) &&
				Objects.equals(value, value1.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, value);
	}

	private InconvertibleValueException inconvertible(Class clazz) {
		return new InconvertibleValueException("Cannot convert value of attribute to " + clazz.getName() +
				" for object: " + this.toString());
	}

	private List<String> parseArrayValue(JsonNode value) {
		List<String> arrValue = new ArrayList<>();
		for (int i = 0; i < value.size(); i++) {
			JsonNode subValue = value.get(i);
			arrValue.add(subValue.asText());
		}

		return arrValue;
	}

	private Map<String, String> parseMapValue(JsonNode value) {
		Map<String, String> mapValue = new HashMap<>();

		Iterator<String> keysIt = value.fieldNames();
		while (keysIt.hasNext()) {
			String key = keysIt.next();
			String val = value.get(key).asText();
			mapValue.put(key, val);
		}

		return mapValue;
	}

	public boolean isNullValue() {
		if (value == null || value instanceof NullNode) {
			return true;
		} else if (value instanceof JsonNode) {
			JsonNode json = (JsonNode) value;
			return json.isNull() || "null".equalsIgnoreCase(json.asText());
		}

		return false;
	}

	private static boolean isNullValue(Object value) {
		return value == null || value instanceof NullNode;
	}

	private static boolean isNullValue(JsonNode value) {
		return value == null ||
				value.isNull() ||
				value instanceof NullNode ||
				"null".equalsIgnoreCase(value.asText());
	}
}
