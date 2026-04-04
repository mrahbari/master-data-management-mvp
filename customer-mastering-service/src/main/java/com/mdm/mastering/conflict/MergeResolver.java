/*
 * Copyright © 2026. All rights reserved.
 * This code is for demonstration purposes only.
 */
package com.mdm.mastering.conflict;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.mdm.mastering.conflict.ResolutionConfig.MergeStrategy;
import com.mdm.mastering.conflict.ResolutionConfig.MergeStrategy.MergeType;

/**
 * MERGE strategy: Combine values from both sources.
 *
 * <p>Used for multi-value fields like phone numbers or email addresses. Supports UNION
 * (deduplicate) and APPEND modes with configurable max values.
 */
@Component
public class MergeResolver implements ConflictResolver {

  @Override
  public FieldResolution resolve(FieldConflict conflict, ResolutionConfig config) {
    MergeStrategy mergeStrategy = config.mergeStrategy();
    if (mergeStrategy == null) {
      // Default to union with max 10
      mergeStrategy = new MergeStrategy(MergeType.UNION, 10);
    }

    List<String> mergedValues = mergeValues(conflict, mergeStrategy);

    // Build reason string
    String reason =
        String.format(
            "Merged %d values using %s strategy (max: %d)",
            mergedValues.size(), mergeStrategy.type(), mergeStrategy.maxValues());

    // Check if value changed
    Object currentValue = conflict.currentValue();
    boolean changed = !valuesEqual(currentValue, mergedValues);

    if (mergedValues.isEmpty()) {
      return FieldResolution.unchanged(currentValue, "No values to merge");
    }

    return FieldResolution.changed(mergedValues, reason);
  }

  @SuppressWarnings("unchecked")
  private List<String> mergeValues(FieldConflict conflict, MergeStrategy mergeStrategy) {
    Set<String> uniqueValues = new LinkedHashSet<>();

    // Add current values
    Object currentValue = conflict.currentValue();
    if (currentValue instanceof List) {
      for (Object val : (List<?>) currentValue) {
        if (val != null) {
          uniqueValues.add(val.toString());
        }
      }
    } else if (currentValue != null) {
      uniqueValues.add(currentValue.toString());
    }

    // Add incoming values
    Object incomingValue = conflict.incomingValue();
    if (incomingValue instanceof List) {
      for (Object val : (List<?>) incomingValue) {
        if (val != null) {
          uniqueValues.add(val.toString());
        }
      }
    } else if (incomingValue != null) {
      uniqueValues.add(incomingValue.toString());
    }

    // Convert to list and apply max limit
    List<String> result = new ArrayList<>(uniqueValues);

    // If we exceed max, drop oldest values (FIFO)
    int maxValues = mergeStrategy.maxValues();
    if (result.size() > maxValues) {
      int startIndex = result.size() - maxValues;
      result = result.subList(startIndex, result.size());
    }

    return result;
  }

  @SuppressWarnings("unchecked")
  private boolean valuesEqual(Object currentValue, List<String> mergedValues) {
    if (currentValue instanceof List<?> currentList) {
      if (currentList.size() != mergedValues.size()) {
        return false;
      }
      return currentList.containsAll(mergedValues) && mergedValues.containsAll(currentList);
    } else if (currentValue == null) {
      return mergedValues.isEmpty();
    } else {
      return mergedValues.size() == 1 && mergedValues.get(0).equals(currentValue.toString());
    }
  }
}
