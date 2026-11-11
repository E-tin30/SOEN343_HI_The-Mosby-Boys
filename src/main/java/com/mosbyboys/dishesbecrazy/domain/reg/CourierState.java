package com.mosbyboys.dishesbecrazy.domain.reg;

// The four courier states from the spec. Stored as text in Supabase under the same names.
public enum CourierState { OFFLINE, IDLE, ASSIGNED, CARRYING }