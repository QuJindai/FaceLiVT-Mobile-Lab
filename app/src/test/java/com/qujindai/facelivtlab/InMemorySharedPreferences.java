package com.qujindai.facelivtlab;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class InMemorySharedPreferences implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();

    @Override public Map<String, ?> getAll() { return new HashMap<>(values); }
    @Override public String getString(String key, String defValue) { Object v=values.get(key); return v instanceof String?(String)v:defValue; }
    @SuppressWarnings("unchecked") @Override public Set<String> getStringSet(String key, Set<String> defValues) {
        Object v=values.get(key); return v instanceof Set?new HashSet<>((Set<String>)v):defValues;
    }
    @Override public int getInt(String key, int defValue) { Object v=values.get(key); return v instanceof Integer?(Integer)v:defValue; }
    @Override public long getLong(String key, long defValue) { Object v=values.get(key); return v instanceof Long?(Long)v:defValue; }
    @Override public float getFloat(String key, float defValue) { Object v=values.get(key); return v instanceof Float?(Float)v:defValue; }
    @Override public boolean getBoolean(String key, boolean defValue) { Object v=values.get(key); return v instanceof Boolean?(Boolean)v:defValue; }
    @Override public boolean contains(String key) { return values.containsKey(key); }
    @Override public Editor edit() { return new MemEditor(); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    private final class MemEditor implements Editor {
        private final Map<String,Object> puts=new HashMap<>();
        private final Set<String> removes=new HashSet<>();
        private boolean clear;
        @Override public Editor putString(String key,String value){ puts.put(key,value); return this; }
        @Override public Editor putStringSet(String key,Set<String> value){ puts.put(key,new HashSet<>(value)); return this; }
        @Override public Editor putInt(String key,int value){ puts.put(key,value); return this; }
        @Override public Editor putLong(String key,long value){ puts.put(key,value); return this; }
        @Override public Editor putFloat(String key,float value){ puts.put(key,value); return this; }
        @Override public Editor putBoolean(String key,boolean value){ puts.put(key,value); return this; }
        @Override public Editor remove(String key){ removes.add(key); return this; }
        @Override public Editor clear(){ clear=true; return this; }
        @Override public boolean commit(){ apply(); return true; }
        @Override public void apply(){ if(clear) values.clear(); for(String k:removes) values.remove(k); values.putAll(puts); }
    }
}
