package com.msb.other.resultSets.t_01;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BaseExportList<T> extends ArrayList<T> {

  private Map<String, T> data = new HashMap<>();

  public boolean add(String id, T e) {
    data.put(id, e);
    return super.add(e);
  }

  public T get(String id) {
    return data.get(id);
  }

  public Map<String, T> getData() {
    return data;
  }

  public Set<String> getKeys() {
    return data.keySet();
  }

}
